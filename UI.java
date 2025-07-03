package final_project;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class UI extends JFrame {

    private DefaultListModel<String> imageListModel;
    private JTextField sigmaField;
    private JTextField threadCountField;
    private JRadioButton sequentialButton, parallelButton;
    private JTextArea outputArea;
    private JLabel originalImageLabel, blurredImageLabel;
    private List<BufferedImage> lastProcessedImages = new ArrayList<>();

    public UI() {
        setTitle("💻 Gaussian Blur - Parallel vs Sequential");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 14));
        UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 13));
        UIManager.put("TextField.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("RadioButton.font", new Font("SansSerif", Font.PLAIN, 13));

        add(createLeftPanel(), BorderLayout.WEST);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("📁 Loaded Images"));

        imageListModel = new DefaultListModel<>();
        JList<String> imageList = new JList<>(imageListModel);
        imageList.setVisibleRowCount(10);
        panel.add(new JScrollPane(imageList), BorderLayout.CENTER);

        JButton loadButton = new JButton("Load Images");
        loadButton.addActionListener(e -> loadImages());
        panel.add(loadButton, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        // Controls
        JPanel controls = new JPanel(new GridLayout(0, 2, 10, 10));
        controls.setBorder(new TitledBorder("⚙️ Controls"));

        controls.add(new JLabel("Sigma (blur strength):"));
        sigmaField = new JTextField("2.0");
        controls.add(sigmaField);

        controls.add(new JLabel("Thread Count:"));
        threadCountField = new JTextField("4");
        controls.add(threadCountField);

        sequentialButton = new JRadioButton("Sequential", true);
        parallelButton = new JRadioButton("Parallel");
        ButtonGroup group = new ButtonGroup();
        group.add(sequentialButton);
        group.add(parallelButton);
        controls.add(sequentialButton);
        controls.add(parallelButton);

        JButton processButton = new JButton("🚀 Start Processing");
        processButton.addActionListener(this::startProcessing);
        controls.add(processButton);

        JButton saveButton = new JButton("💾 Save Output Images");
        saveButton.addActionListener(e -> saveOutputImages());
        controls.add(saveButton);

        centerPanel.add(controls, BorderLayout.NORTH);

        // Image preview
        JPanel previewPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        previewPanel.setBorder(new TitledBorder("🖼️ Image Preview"));

        originalImageLabel = new JLabel("Original", SwingConstants.CENTER);
        originalImageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        blurredImageLabel = new JLabel("Blurred", SwingConstants.CENTER);
        blurredImageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        previewPanel.add(originalImageLabel);
        previewPanel.add(blurredImageLabel);

        centerPanel.add(previewPanel, BorderLayout.CENTER);

        return centerPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new TitledBorder("📊 Output & Logs"));

        outputArea = new JTextArea(6, 40);
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(outputArea);
        bottomPanel.add(scroll, BorderLayout.CENTER);

        return bottomPanel;
    }

    private void log(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        outputArea.append("[" + time + "] " + message + "\n");
    }

    private void loadImages() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "jpg", "png", "bmp"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imageListModel.clear();
            for (File file : chooser.getSelectedFiles()) {
                imageListModel.addElement(file.getAbsolutePath());
            }
            log("Loaded " + chooser.getSelectedFiles().length + " image(s).");
        }
    }

    private void startProcessing(ActionEvent e) {
        List<String> paths = Collections.list(imageListModel.elements());
        if (paths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please load images first.");
            return;
        }

        float sigma;
        int threadCount;

        try {
            sigma = Float.parseFloat(sigmaField.getText());
            threadCount = Integer.parseInt(threadCountField.getText());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid sigma or thread count!");
            return;
        }

        outputArea.setText("");
        log("Starting processing...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                MainApp.Processor processor = new MainApp.Processor(sigma, threadCount);
                MainApp.ProcessingResult result;

                long start = System.nanoTime();

                if (sequentialButton.isSelected()) {
                    result = processor.processSequential(paths);
                } else {
                    result = processor.processParallel(paths);
                }

                long end = System.nanoTime();
                double seconds = (end - start) / 1e9;
                log("Processing completed in " + String.format("%.3f", seconds) + " seconds.");
                log("Processed " + result.getImages().size() + " images.");
                processor.shutdown();

                // Preview first image
                if (!paths.isEmpty() && result.getImages().size() > 0) {
                    try {
                        BufferedImage original = ImageIO.read(new File(paths.get(0)));
                        BufferedImage blurred = result.getImages().get(0);

                        ImageIcon originalIcon = new ImageIcon(original.getScaledInstance(300, 300, Image.SCALE_SMOOTH));
                        ImageIcon blurredIcon = new ImageIcon(blurred.getScaledInstance(300, 300, Image.SCALE_SMOOTH));

                        originalImageLabel.setIcon(originalIcon);
                        blurredImageLabel.setIcon(blurredIcon);

                        lastProcessedImages = result.getImages();

                    } catch (Exception ex) {
                        log("Error displaying preview: " + ex.getMessage());
                    }
                }

                return null;
            }
        };

        worker.execute();
    }

    private void saveOutputImages() {
        if (lastProcessedImages == null || lastProcessedImages.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No images to save. Please process first.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Folder to Save Images");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            for (int i = 0; i < lastProcessedImages.size(); i++) {
                try {
                    File outFile = new File(dir, "blurred_" + i + ".jpg");
                    ImageIO.write(lastProcessedImages.get(i), "jpg", outFile);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Failed to save image: " + e.getMessage());
                }
            }
            log("Images saved to: " + dir.getAbsolutePath());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UI ui = new UI();
            ui.setVisible(true);
        });
    }
}
