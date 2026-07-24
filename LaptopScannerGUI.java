import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

public class LaptopScannerGUI extends JFrame {

    // Base API URLs
    private static final String BASE_API_URL = "https://cms.cditproject.org/api";
    private static final String GET_EXAMS_URL = BASE_API_URL + "/getActiveExams";
    private static final String SAVE_SYSTEM_URL = BASE_API_URL + "/saveExamAllotedSystem";
    private static final String GET_STATUS_URL = BASE_API_URL + "/getSystemStatus";
    private static final String DEACTIVATE_URL = BASE_API_URL + "/deactivateExamAllotedSystem";

    // Placeholder Item for Exam Dropdown
    private static final ExamItem PLACEHOLDER_EXAM = new ExamItem(-1, "-- Select an examination --", "");

    // UI Input Controls
    private JComboBox<ExamItem> cmbExams;
    private JTextField txtVenueNo;
    private JComboBox<String> cmbServerType;
    private JButton btnSubmit;
    private JButton btnDeactivate;
    private JLabel lblStatus;

    // Existing Registration Card Controls
    private JPanel activeRegistrationPanel;
    private JLabel lblActiveRegDetails;

    // Hardware Spec Labels
    private JLabel lblSerial, lblModel, lblProcessor, lblRam, lblStorage, lblMac, lblOs, lblIp;

    private String serialNumber = "Scanning...";
    private String systemName = "Scanning...";
    private String systemOs = "Scanning...";
    private String processorInfo = "Scanning...";
    private String ram = "Scanning...";
    private String storage = "Scanning...";
    private String macAddress = "Scanning...";
    private String activeIp = "Scanning...";

    private List<ExamItem> allExamsList = new ArrayList<>();
    private int currentExamAllotedSystemId = -1;
    private boolean isProgrammaticChange = false; // Flag to prevent triggering status check during UI resets

    public LaptopScannerGUI() {
        setTitle("C-DIT CBT Server Inventory & Registration Agent");
        setSize(600, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Header Title
        JLabel header = new JLabel("CBT Server Venue Mapping");
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        mainPanel.add(header);
        mainPanel.add(Box.createVerticalStrut(15));

        // Specs Panel (Auto-Detected)
        JPanel specsPanel = createCardPanel("Hardware Details (Auto-Detected)");
        JPanel gridSpecs = new JPanel(new GridLayout(8, 2, 10, 6));

        lblSerial = new JLabel("Loading...");
        lblModel = new JLabel("Loading...");
        lblProcessor = new JLabel("Loading...");
        lblRam = new JLabel("Loading...");
        lblStorage = new JLabel("Loading...");
        lblMac = new JLabel("Loading...");
        lblOs = new JLabel("Loading...");
        lblIp = new JLabel("Loading...");

        addGridRow(gridSpecs, "System Serial:", lblSerial);
        addGridRow(gridSpecs, "System Name / Model:", lblModel);
        addGridRow(gridSpecs, "Processor (system_cpu):", lblProcessor);
        addGridRow(gridSpecs, "RAM (system_ram):", lblRam);
        addGridRow(gridSpecs, "Storage (system_memory):", lblStorage);
        addGridRow(gridSpecs, "MAC ID (system_macid):", lblMac);
        addGridRow(gridSpecs, "OS (system_os):", lblOs);
        addGridRow(gridSpecs, "Active Local IP:", lblIp);

        specsPanel.add(gridSpecs);
        mainPanel.add(specsPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Active Registration Banner Panel
        activeRegistrationPanel = createCardPanel("Active Allocation Found");
        activeRegistrationPanel.setVisible(false);
        activeRegistrationPanel.setBackground(new Color(254, 242, 242));

        lblActiveRegDetails = new JLabel("<html>Loading registration details...</html>");
        lblActiveRegDetails.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblActiveRegDetails.setForeground(new Color(153, 27, 27));

        btnDeactivate = new JButton("Deactivate / Remove Registration");
        btnDeactivate.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnDeactivate.setBackground(new Color(220, 38, 38));
        btnDeactivate.setForeground(Color.WHITE);
        btnDeactivate.setFocusPainted(false);
        btnDeactivate.addActionListener(e -> deactivateCurrentRegistration());

        JPanel regInnerBox = new JPanel(new BorderLayout(10, 10));
        regInnerBox.setOpaque(false);
        regInnerBox.add(lblActiveRegDetails, BorderLayout.CENTER);
        regInnerBox.add(btnDeactivate, BorderLayout.SOUTH);

        activeRegistrationPanel.add(regInnerBox);
        mainPanel.add(activeRegistrationPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Venue Configuration Panel
        JPanel formPanel = createCardPanel("Venue & Exam Configuration");
        JPanel gridForm = new JPanel(new GridLayout(3, 2, 10, 10));

        // Searchable and Scrollable Exam Dropdown
        cmbExams = new JComboBox<>();
        cmbExams.setEditable(true);
        cmbExams.setMaximumRowCount(6);
        cmbExams.addItem(PLACEHOLDER_EXAM);

        setupSearchableComboBox(cmbExams);

        // Listener to check system status ONLY when a real exam is selected
        cmbExams.addActionListener(e -> {
            if (isProgrammaticChange) return;

            ExamItem selected = getSelectedExamItem();
            if (selected != null && selected.id != -1 && !macAddress.equals("Scanning...")) {
                checkSystemRegistrationStatus(selected.id, macAddress);
            } else if (selected == null || selected.id == -1) {
                // If placeholder is selected, clear registration card and reset status
                activeRegistrationPanel.setVisible(false);
                btnSubmit.setEnabled(true);
                lblStatus.setText("Select an examination to proceed.");
                lblStatus.setForeground(Color.GRAY);
            }
        });

        txtVenueNo = new JTextField();
        txtVenueNo.setToolTipText("Enter Venue Number (e.g., 101)");

        cmbServerType = new JComboBox<>(new String[]{"MAIN SERVER (1)", "BACKUP SERVER (2)"});

        gridForm.add(new JLabel("Select Exam (exam_id):"));
        gridForm.add(cmbExams);
        gridForm.add(new JLabel("Venue No (venue_no):"));
        gridForm.add(txtVenueNo);
        gridForm.add(new JLabel("Server Type (system_type_id):"));
        gridForm.add(cmbServerType);

        formPanel.add(gridForm);
        mainPanel.add(formPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Submit Area
        btnSubmit = new JButton("Register Server to CMS");
        btnSubmit.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnSubmit.setBackground(new Color(37, 99, 235));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setFocusPainted(false);
        btnSubmit.addActionListener(e -> submitDataToCms());

        lblStatus = new JLabel("Select an examination to proceed.");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblStatus.setForeground(Color.GRAY);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionPanel.add(btnSubmit);
        actionPanel.add(lblStatus);

        mainPanel.add(actionPanel);

        add(mainPanel);

        // Run background tasks
        fetchActiveExamsInBackground();
        scanHardwareInBackground();
    }

    private JPanel createCardPanel(String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));
        return p;
    }

    private void addGridRow(JPanel panel, String title, JLabel valLabel) {
        JLabel l = new JLabel(title);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(l);
        panel.add(valLabel);
    }

    private void setupSearchableComboBox(JComboBox<ExamItem> comboBox) {
        JTextField editor = (JTextField) comboBox.getEditor().getEditorComponent();
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN ||
                        e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    return;
                }

                String text = editor.getText().toLowerCase();
                SwingUtilities.invokeLater(() -> filterExams(text));
            }
        });
    }

    private void filterExams(String text) {
        JTextField editor = (JTextField) cmbExams.getEditor().getEditorComponent();
        int caretPos = editor.getCaretPosition();

        isProgrammaticChange = true;
        cmbExams.removeAllItems();
        cmbExams.addItem(PLACEHOLDER_EXAM);

        for (ExamItem item : allExamsList) {
            if (item.examName.toLowerCase().contains(text) || item.examCode.toLowerCase().contains(text)) {
                cmbExams.addItem(item);
            }
        }
        isProgrammaticChange = false;

        editor.setText(text);
        try {
            editor.setCaretPosition(Math.min(caretPos, text.length()));
        } catch (Exception ignored) {}

        if (cmbExams.getItemCount() > 1) {
            cmbExams.showPopup();
        }
    }

    // API Call 1: Get Active Exams
    private void fetchActiveExamsInBackground() {
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(GET_EXAMS_URL)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<ExamItem> parsedExams = parseExamsJson(response.body());
                    SwingUtilities.invokeLater(() -> {
                        allExamsList.clear();
                        allExamsList.addAll(parsedExams);

                        isProgrammaticChange = true;
                        cmbExams.removeAllItems();
                        cmbExams.addItem(PLACEHOLDER_EXAM); // Default placeholder
                        for (ExamItem item : allExamsList) {
                            cmbExams.addItem(item);
                        }
                        cmbExams.setSelectedItem(PLACEHOLDER_EXAM);
                        isProgrammaticChange = false;

                        if (allExamsList.isEmpty()) {
                            lblStatus.setText("⚠️ Warning: No active exams found!");
                            lblStatus.setForeground(Color.ORANGE);
                        }
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("❌ Error fetching exams: " + ex.getMessage());
                    lblStatus.setForeground(Color.RED);
                });
            }
        }).start();
    }

    private List<ExamItem> parseExamsJson(String json) {
        List<ExamItem> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*(\\d+)[^}]*?\"exam_name\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"examcode\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            String name = matcher.group(2);
            String code = matcher.group(3);
            list.add(new ExamItem(id, name, code));
        }
        return list;
    }

    // API Call 3: Get System Status
    private void checkSystemRegistrationStatus(int examId, String mac) {
        if (examId == -1) return;

        String url = GET_STATUS_URL + "?system_macid=" + mac + "&exam_id=" + examId;
        lblStatus.setText("Checking registration status...");
        lblStatus.setForeground(Color.BLUE);

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String body = response.body();
                    boolean isRegistered = body.contains("\"registered\":true") || body.contains("\"registered\": true");

                    if (isRegistered) {
                        int examAllotedSystemId = extractIntFromJson(body, "exam_alloted_system_id");
                        int venueNo = extractIntFromJson(body, "venue_no");

                        SwingUtilities.invokeLater(() -> {
                            currentExamAllotedSystemId = examAllotedSystemId;
                            activeRegistrationPanel.setVisible(true);
                            lblActiveRegDetails.setText(String.format(
                                    "<html><b>Registered for this exam!</b><br/>" +
                                            "• Alloted System ID: %d<br/>" +
                                            "• Venue No: %d<br/>" +
                                            "• MAC Address: %s</html>",
                                    examAllotedSystemId, venueNo, mac
                            ));
                            txtVenueNo.setText(String.valueOf(venueNo));
                            btnSubmit.setEnabled(false);
                            lblStatus.setText("⚠️ Device is currently registered for this exam.");
                            lblStatus.setForeground(new Color(180, 83, 9));
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            currentExamAllotedSystemId = -1;
                            activeRegistrationPanel.setVisible(false);
                            btnSubmit.setEnabled(true);
                            lblStatus.setText("Ready to register.");
                            lblStatus.setForeground(new Color(22, 163, 74));
                        });
                    }
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Status check failed: " + ex.getMessage());
                    lblStatus.setForeground(Color.RED);
                });
            }
        }).start();
    }

    // API Call 4: Deactivate Allocation
    private void deactivateCurrentRegistration() {
        if (currentExamAllotedSystemId == -1) return;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to deactivate registration ID " + currentExamAllotedSystemId + "?",
                "Confirm Deactivation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        btnDeactivate.setEnabled(false);
        lblStatus.setText("Deactivating registration...");
        lblStatus.setForeground(Color.BLUE);

        String jsonPayload = String.format("{\"exam_alloted_system_id\": %d}", currentExamAllotedSystemId);

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(DEACTIVATE_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    SwingUtilities.invokeLater(() -> {
                        btnDeactivate.setEnabled(true);
                        resetFormAndDeselectExam();
                        lblStatus.setText("SUCCESS: Registration deactivated successfully!");
                        lblStatus.setForeground(new Color(22, 163, 74));
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        btnDeactivate.setEnabled(true);
                        lblStatus.setText("Deactivation failed: Status " + response.statusCode());
                        lblStatus.setForeground(Color.RED);
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnDeactivate.setEnabled(true);
                    lblStatus.setText("Deactivation error: " + ex.getMessage());
                    lblStatus.setForeground(Color.RED);
                });
            }
        }).start();
    }

    private void scanHardwareInBackground() {
        new Thread(() -> {
            try {
                SystemInfo si = new SystemInfo();
                HardwareAbstractionLayer hal = si.getHardware();
                OperatingSystem os = si.getOperatingSystem();
                ComputerSystem computerSystem = hal.getComputerSystem();

                systemName = computerSystem.getManufacturer() + " " + computerSystem.getModel();
                systemOs = os.getFamily() + " " + os.getVersionInfo().getVersion();

                CentralProcessor proc = hal.getProcessor();
                processorInfo = proc.getProcessorIdentifier().getName().trim();

                serialNumber = fetchSystemSerialNumber();

                GlobalMemory memory = hal.getMemory();
                long totalRamGb = memory.getTotal() / (1024 * 1024 * 1024);
                ram = totalRamGb + "GB";

                List<HWDiskStore> diskStores = hal.getDiskStores();
                StringBuilder diskStr = new StringBuilder();
                for (HWDiskStore disk : diskStores) {
                    if (disk.getSize() > 0) {
                        long sizeGb = disk.getSize() / (1024 * 1024 * 1024);
                        diskStr.append(sizeGb).append("GB SSD; ");
                    }
                }
                storage = diskStr.length() > 0 ? diskStr.toString().trim() : "Unknown";

                List<NetworkIF> networkIFs = hal.getNetworkIFs();
                for (NetworkIF net : networkIFs) {
                    if (net.getMacaddr() != null && !net.getMacaddr().isEmpty() && !net.getMacaddr().equals("00:00:00:00:00:00")) {
                        String nameLower = net.getDisplayName().toLowerCase();
                        if (nameLower.contains("wireless") || nameLower.contains("ethernet") || nameLower.contains("realtek") || nameLower.contains("intel")) {
                            macAddress = net.getMacaddr().toUpperCase();
                            break;
                        }
                    }
                }

                activeIp = fetchActiveNetworkIp();

            } catch (Exception e) {
                e.printStackTrace();
            }

            SwingUtilities.invokeLater(() -> {
                lblSerial.setText(serialNumber);
                lblModel.setText(systemName);
                lblProcessor.setText(processorInfo);
                lblRam.setText(ram);
                lblStorage.setText(storage);
                lblMac.setText(macAddress);
                lblOs.setText(systemOs);
                lblIp.setText(activeIp);

                if (activeIp.endsWith(".1")) {
                    cmbServerType.setSelectedIndex(0);
                } else if (activeIp.endsWith(".2")) {
                    cmbServerType.setSelectedIndex(1);
                }
            });
        }).start();
    }

    private String fetchSystemSerialNumber() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"wmic", "bios", "get", "serialnumber"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.equalsIgnoreCase("SerialNumber")) {
                        return line;
                    }
                }
            } catch (Exception e) {
                return "Unknown";
            }
        } else {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"pkexec", "dmidecode", "-s", "system-serial-number"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String serial = reader.readLine();
                return (serial != null && !serial.isBlank()) ? serial.trim() : "Unknown";
            } catch (Exception e) {
                return "Unknown";
            }
        }
        return "Unknown";
    }

    private String fetchActiveNetworkIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netIf : Collections.list(interfaces)) {
                if (netIf.isLoopback() || !netIf.isUp() || netIf.isVirtual()) continue;
                Enumeration<InetAddress> addresses = netIf.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    String ip = addr.getHostAddress();
                    if (ip.contains(".") && !ip.startsWith("127.")) return ip;
                }
            }
        } catch (Exception e) {
            return "Disconnected";
        }
        return "Disconnected";
    }

    // API Call 2: Save Exam Alloted System
    private void submitDataToCms() {
        ExamItem selectedExam = getSelectedExamItem();
        if (selectedExam == null || selectedExam.id == -1) {
            lblStatus.setText("❌ Error: Please select an examination from the list!");
            lblStatus.setForeground(Color.RED);
            return;
        }

        String venueStr = txtVenueNo.getText().trim();
        if (venueStr.isEmpty()) {
            lblStatus.setText("❌ Error: Enter Venue Number!");
            lblStatus.setForeground(Color.RED);
            return;
        }

        int venueNo;
        try {
            venueNo = Integer.parseInt(venueStr);
        } catch (NumberFormatException ex) {
            lblStatus.setText("❌ Error: Venue No must be a valid number!");
            lblStatus.setForeground(Color.RED);
            return;
        }

        int systemType = (cmbServerType.getSelectedIndex() == 0) ? 1 : 2;

        btnSubmit.setEnabled(false);
        lblStatus.setText("Connecting to CMS API...");
        lblStatus.setForeground(Color.BLUE);

        String jsonPayload = String.format(
                "{\n" +
                        "  \"exam_id\": %d,\n" +
                        "  \"system_details\": \"Serial: %s | Model: %s | IP: %s\",\n" +
                        "  \"venue_no\": %d,\n" +
                        "  \"system_macid\": \"%s\",\n" +
                        "  \"system_type_id\": %d,\n" +
                        "  \"system_ram\": \"%s\",\n" +
                        "  \"system_memory\": \"%s\",\n" +
                        "  \"system_name\": \"%s\",\n" +
                        "  \"system_os\": \"%s\",\n" +
                        "  \"system_cpu\": \"%s\"\n" +
                        "}",
                selectedExam.id,
                serialNumber, systemName, activeIp,
                venueNo,
                macAddress,
                systemType,
                ram,
                storage,
                systemName,
                systemOs,
                processorInfo
        );

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SAVE_SYSTEM_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    SwingUtilities.invokeLater(() -> {
                        resetFormAndDeselectExam();
                        lblStatus.setText("SUCCESS: Registered to CMS!");
                        lblStatus.setForeground(new Color(22, 163, 74));
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        btnSubmit.setEnabled(true);
                        lblStatus.setText("FAILED: Status " + response.statusCode());
                        lblStatus.setForeground(Color.RED);
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnSubmit.setEnabled(true);
                    lblStatus.setText("Error: " + ex.getMessage());
                    lblStatus.setForeground(Color.RED);
                });
            }
        }).start();
    }

    // Helper: Deselects the current exam and clears form fields after success
    private void resetFormAndDeselectExam() {
        isProgrammaticChange = true;
        cmbExams.setSelectedItem(PLACEHOLDER_EXAM);
        JTextField editor = (JTextField) cmbExams.getEditor().getEditorComponent();
        editor.setText("-- Select an examination --");
        isProgrammaticChange = false;

        txtVenueNo.setText("");
        activeRegistrationPanel.setVisible(false);
        currentExamAllotedSystemId = -1;
        btnSubmit.setEnabled(true);
    }

    private ExamItem getSelectedExamItem() {
        Object selectedObj = cmbExams.getSelectedItem();
        if (selectedObj instanceof ExamItem) {
            return (ExamItem) selectedObj;
        } else if (selectedObj instanceof String) {
            String typed = ((String) selectedObj).trim();
            if (typed.equalsIgnoreCase("-- Select an examination --") || typed.isEmpty()) {
                return PLACEHOLDER_EXAM;
            }
            for (ExamItem item : allExamsList) {
                if (item.examName.equalsIgnoreCase(typed) || item.examCode.equalsIgnoreCase(typed)) {
                    return item;
                }
            }
        }
        return PLACEHOLDER_EXAM;
    }

    private int extractIntFromJson(String json, String key) {
        try {
            Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception ignored) {}
        return -1;
    }

    static class ExamItem {
        int id;
        String examName;
        String examCode;

        public ExamItem(int id, String examName, String examCode) {
            this.id = id;
            this.examName = examName;
            this.examCode = examCode;
        }

        @Override
        public String toString() {
            if (id == -1) return examName;
            return examName + " (" + examCode + ")";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LaptopScannerGUI().setVisible(true));
    }
}