package final_project;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public class MainApp {

    public static class GaussianBlur {
        private final float[][] kernel;
        private final int kernelSize;

        public GaussianBlur(float sigma) {
            this.kernelSize = (int) Math.ceil(sigma * 3) * 2 + 1;
            this.kernel = createGaussianKernel(sigma);
        }

        private float[][] createGaussianKernel(float sigma) {
            int size = kernelSize;
            float[][] kernel = new float[size][size];
            float sum = 0.0f;
            int center = size / 2;

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int dx = x - center;
                    int dy = y - center;
                    float value = (float) Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
                    kernel[x][y] = value;
                    sum += value;
                }
            }

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    kernel[x][y] /= sum;
                }
            }

            return kernel;
        }

        public BufferedImage applySequential(BufferedImage input) {
            int width = input.getWidth();
            int height = input.getHeight();
            BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            int offset = kernelSize / 2;

            for (int y = offset; y < height - offset; y++) {
                for (int x = offset; x < width - offset; x++) {
                    float r = 0, g = 0, b = 0;

                    for (int ky = 0; ky < kernelSize; ky++) {
                        for (int kx = 0; kx < kernelSize; kx++) {
                            int px = x + kx - offset;
                            int py = y + ky - offset;

                            Color pixel = new Color(input.getRGB(px, py));
                            float weight = kernel[kx][ky];

                            r += pixel.getRed() * weight;
                            g += pixel.getGreen() * weight;
                            b += pixel.getBlue() * weight;
                        }
                    }

                    int rgb = new Color(
                            Math.min(255, Math.max(0, (int) r)),
                            Math.min(255, Math.max(0, (int) g)),
                            Math.min(255, Math.max(0, (int) b))
                    ).getRGB();

                    output.setRGB(x, y, rgb);
                }
            }

            return output;
        }

        public BufferedImage applyParallel(BufferedImage input) {
            ForkJoinPool pool = new ForkJoinPool();
            try {
                BlurTask task = new BlurTask(input, 0, 0, input.getWidth(), input.getHeight());
                return pool.invoke(task);
            } finally {
                pool.shutdown();
            }
        }

        private class BlurTask extends RecursiveTask<BufferedImage> {
            private static final int THRESHOLD = 50000;
            private final BufferedImage input;
            private final int x, y, width, height;

            public BlurTask(BufferedImage input, int x, int y, int width, int height) {
                this.input = input;
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }

            @Override
            protected BufferedImage compute() {
                if (width * height <= THRESHOLD) {
                    return blurRegionWithPadding(input, x, y, width, height);
                } else {
                    int midX = width / 2;
                    int midY = height / 2;

                    BlurTask topLeft = new BlurTask(input, x, y, midX, midY);
                    BlurTask topRight = new BlurTask(input, x + midX, y, width - midX, midY);
                    BlurTask bottomLeft = new BlurTask(input, x, y + midY, midX, height - midY);
                    BlurTask bottomRight = new BlurTask(input, x + midX, y + midY, width - midX, height - midY);

                    topLeft.fork();
                    topRight.fork();
                    bottomLeft.fork();

                    BufferedImage br = bottomRight.compute();
                    BufferedImage bl = bottomLeft.join();
                    BufferedImage tr = topRight.join();
                    BufferedImage tl = topLeft.join();

                    return mergeQuadrants(tl, tr, bl, br);
                }
            }
        }

        private BufferedImage blurRegion(BufferedImage input, int startX, int startY,
                                         int regionWidth, int regionHeight) {
            BufferedImage output = new BufferedImage(regionWidth, regionHeight, BufferedImage.TYPE_INT_RGB);
            int offset = kernelSize / 2;

            for (int y = Math.max(offset, startY); y < Math.min(startY + regionHeight - offset, input.getHeight() - offset); y++) {
                for (int x = Math.max(offset, startX); x < Math.min(startX + regionWidth - offset, input.getWidth() - offset); x++) {
                    float r = 0, g = 0, b = 0;

                    for (int ky = 0; ky < kernelSize; ky++) {
                        for (int kx = 0; kx < kernelSize; kx++) {
                            int px = x + kx - offset;
                            int py = y + ky - offset;

                            if (px >= 0 && px < input.getWidth() && py >= 0 && py < input.getHeight()) {
                                Color pixel = new Color(input.getRGB(px, py));
                                float weight = kernel[kx][ky];

                                r += pixel.getRed() * weight;
                                g += pixel.getGreen() * weight;
                                b += pixel.getBlue() * weight;
                            }
                        }
                    }

                    int rgb = new Color(
                            Math.min(255, Math.max(0, (int) r)),
                            Math.min(255, Math.max(0, (int) g)),
                            Math.min(255, Math.max(0, (int) b))
                    ).getRGB();

                    output.setRGB(x - startX, y - startY, rgb);
                }
            }

            return output;
        }

        private BufferedImage blurRegionWithPadding(BufferedImage input, int startX, int startY,
                                                    int regionWidth, int regionHeight) {
            int offset = kernelSize / 2;
            int paddedX = Math.max(0, startX - offset);
            int paddedY = Math.max(0, startY - offset);
            int paddedWidth = Math.min(input.getWidth() - paddedX, regionWidth + 2 * offset);
            int paddedHeight = Math.min(input.getHeight() - paddedY, regionHeight + 2 * offset);

            BufferedImage paddedRegion = blurRegion(input, paddedX, paddedY, paddedWidth, paddedHeight);

            BufferedImage cropped = new BufferedImage(regionWidth, regionHeight, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < regionHeight; y++) {
                for (int x = 0; x < regionWidth; x++) {
                    int rgb = paddedRegion.getRGB(x + (startX - paddedX), y + (startY - paddedY));
                    cropped.setRGB(x, y, rgb);
                }
            }
            return cropped;
        }

        private BufferedImage mergeQuadrants(BufferedImage tl, BufferedImage tr,
                                             BufferedImage bl, BufferedImage br) {
            int totalWidth = tl.getWidth() + tr.getWidth();
            int totalHeight = tl.getHeight() + bl.getHeight();
            BufferedImage merged = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);

            Graphics2D g2d = merged.createGraphics();
            g2d.drawImage(tl, 0, 0, null);
            g2d.drawImage(tr, tl.getWidth(), 0, null);
            g2d.drawImage(bl, 0, tl.getHeight(), null);
            g2d.drawImage(br, tl.getWidth(), tl.getHeight(), null);
            g2d.dispose();

            return merged;
        }
    }

    public static class Processor {
        private final GaussianBlur blur;
        private final ExecutorService executor;

        public Processor(float blurSigma, int threadCount) {
            this.blur = new GaussianBlur(blurSigma);
            this.executor = Executors.newFixedThreadPool(threadCount);
        }

        public ProcessingResult processSequential(List<String> imagePaths) {
            long startTime = System.nanoTime();
            List<BufferedImage> results = new ArrayList<>();

            for (String path : imagePaths) {
                try {
                    BufferedImage image = ImageIO.read(new File(path));
                    BufferedImage blurred = blur.applySequential(image);
                    results.add(blurred);
                } catch (IOException e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                }
            }

            long endTime = System.nanoTime();
            return new ProcessingResult(results, endTime - startTime);
        }

        public ProcessingResult processParallel(List<String> imagePaths) {
            long startTime = System.nanoTime();

            List<CompletableFuture<BufferedImage>> futures = imagePaths.stream()
                    .map(path -> CompletableFuture.supplyAsync(() -> {
                        try {
                            BufferedImage image = ImageIO.read(new File(path));
                            return blur.applyParallel(image);
                        } catch (IOException e) {
                            System.err.println("Error processing " + path + ": " + e.getMessage());
                            return null;
                        }
                    }, executor))
                    .toList();

            List<BufferedImage> results = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            long endTime = System.nanoTime();
            return new ProcessingResult(results, endTime - startTime);
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    public static class ProcessingResult {
        private final List<BufferedImage> images;
        private final long processingTimeNanos;

        public ProcessingResult(List<BufferedImage> images, long timeNanos) {
            this.images = images;
            this.processingTimeNanos = timeNanos;
        }

        public double getProcessingTimeSeconds() {
            return processingTimeNanos / 1_000_000_000.0;
        }

        public List<BufferedImage> getImages() {
            return images;
        }

        public long getProcessingTimeNanos() {
            return processingTimeNanos;
        }
    }

    public static class Benchmark {
        public static void runBenchmark(List<String> imagePaths) {
            System.out.println("Image Processing Benchmark");
            System.out.println("Images: " + imagePaths.size());
            System.out.println("ThreadCount,ProcessingTime,Speedup");

            Processor seqProcessor = new Processor(2.0f, 1);
            ProcessingResult seqResult = seqProcessor.processSequential(imagePaths);
            double seqTime = seqResult.getProcessingTimeSeconds();
            seqProcessor.shutdown();

            System.out.printf("1,%.3f,1.00%n", seqTime);

            int[] threadCounts = {2, 4, 8, 16};

            for (int threads : threadCounts) {
                Processor parProcessor = new Processor(2.0f, threads);

                double totalTime = 0;
                for (int run = 0; run < 3; run++) {
                    ProcessingResult result = parProcessor.processParallel(imagePaths);
                    totalTime += result.getProcessingTimeSeconds();
                }
                double avgTime = totalTime / 3;
                double speedup = seqTime / avgTime;

                System.out.printf("%d,%.3f,%.2f%n", threads, avgTime, speedup);
                parProcessor.shutdown();
            }
        }
    }

    public static void createTestImages(String directory, int count, int width, int height) {
        new File(directory).mkdirs();

        for (int i = 0; i < count; i++) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Random random = new Random();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = random.nextInt(256);
                    int g = random.nextInt(256);
                    int b = random.nextInt(256);
                    image.setRGB(x, y, new Color(r, g, b).getRGB());
                }
            }

            try {
                ImageIO.write(image, "jpg", new File(directory + "/test_" + i + ".jpg"));
            } catch (IOException e) {
                System.err.println("Error creating test image: " + e.getMessage());
            }
        }

        System.out.println("Created " + count + " test images in " + directory);
    }

    public static void main(String[] args) {
        String testDir = "test_images";
        if (!new File(testDir).exists()) {
            System.out.println("Creating test images...");
            createTestImages(testDir, 10, 1024, 1024);
        }

        File dir = new File(testDir);
        List<String> imagePaths = Arrays.stream(dir.listFiles())
                .filter(f -> f.getName().endsWith(".jpg"))
                .map(File::getAbsolutePath)
                .toList();

        if (imagePaths.isEmpty()) {
            System.out.println("No test images found!");
            return;
        }

        System.out.println("Found " + imagePaths.size() + " test images");

        Benchmark.runBenchmark(imagePaths);
    }
}
