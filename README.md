# Parallel Image Processing

This project shows how to apply Gaussian blur filter to images using both sequential and parallel processing (Fork/Join Framework) in Java. It includes benchmarking to compare performance using multiple threads.

## Files We Included

### `MainApp.java`:
Application logic java code that generates test images and applies on them sequential processing (gaussian blur), the same process is applied using parallel programming concept and threads. The processing time and the speedup are compared based on the thread count (we notice that for a single thread, sequential and parallel programming have the same delay time since we are not benefiting from parallel programming). When the thread count increases, not only the performance and speed up increase but the CPU and RAM usage also increase indicating the full benefit of the machine performance. 

### `UI.java`:
User interface making the filtering process more flexible and visually more attractive, where the user can tune the number of threads and the parameter 'sigma' indicating the blur strength of the gaussian filter. Also the output image after applying the filter can be saved on the computer. We can notice that the output is consistent between the sequential method and parallel method. In addition, the processing time is printed and is very much less for parallel processing for the same blur strength. It is easily shown that the processing time is inversely proportional to the number of threads for a specific range depending on the CPU (sometimes if the CPU contains less cores, increasing the number of threads will result in an increased processing time because of context switching)

## 🚀 Features
- Apply Gaussian blur using customizable `sigma` value.
- Run image processing in sequential or parallel mode.
- Benchmark parallel speedup with configurable thread count.
- Auto-generate test images (10 images of 1024×1024 by default).
- Compare performance across multiple thread counts (1, 2, 4, 8, 16).
- User Interface to upload and preview images.

## Output Example
Found 10 test images

Image Processing Benchmark

Images: 10

ThreadCount,ProcessingTime,Speedup

1,23.746,1.00

2,5.751,4.13

4,10.734,2.21

8,2.825,8.41

16,2.912,8.15

## Brief Code Explanation 
The 'GaussianBlur' class applies the blur filter either sequentially or in parallel

GaussianBlur(float sigma) initializes the Gaussian kernel (or filter) based on the sigma value controlling the blur strength

applySequential(BufferedImage) applies the window or also named kernel to each pixel using nested loops (sequential). It is simple but slow

applyParallel(BufferedImage) uses ForkJoinPool + RecursiveTask (BlurTask) to divide the image into smaller regions and blur them in parallel (we are dividing the main task between multiple threads)

blurRegionWithPadding() applies blur to a region with padding to avoid edge artifacts resulting in a clean output image

mergeQuadrants combines the four processed image parts (quadrants) into one final image

The 'Processor' class handles batch processing of images, it uses an ExecutorService to run multiple image blurring tasks in parallel

processSequential(List<String>) for sequrntial processing

processParallel(List<String>) for parallel processing

The 'ProcessingResult' class holds a list of processed images and total processing time to compare sequential vs. parallel performance

the 'Benchmark' class runs tests using different thread counts and prints the performance metrics (processing time and speedup)

createTestImages() creates 10 randomly colored images (1024×1024 pixels) for testing
