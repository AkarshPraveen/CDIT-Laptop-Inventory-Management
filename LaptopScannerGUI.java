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

    // Base API URL — Update this domain to match your teammate's actual host/ip
    private static final String BASE_API_URL = "https://cms.cditproject.org/api";
    private static final String GET_EXAMS_URL = BASE_API_URL + "/getActiveExams";
    private static final String SAVE_SYSTEM_URL = BASE_API_URL + "/saveExamAllotedSystem";

    // UI Input Controls
    private JComboBox<ExamItem> cmbExams;
    private JTextField txtVenueNo;
    private JComboBox<String> cmbServerType;
    private JButton btnSubmit;
    private JLabel lblStatus;

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

    public LaptopScannerGUI() {
        setTitle("C-DIT CBT Server Inventory & Registration Agent");
        setSize(600, 700);
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

        // Venue Configuration Panel
        JPanel formPanel = createCardPanel("Venue & Exam Configuration");
        JPanel gridForm = new JPanel(new GridLayout(3, 2, 10, 10));

        // Searchable and Scrollable Exam Dropdown
        cmbExams = new JComboBox<>();
        cmbExams.setEditable(true); // Enables direct typing to search
        cmbExams.setMaximumRowCount(6); // Sets visible scroll height

        setupSearchableComboBox(cmbExams);

        txtVenueNo = new JTextField();
        txtVenueNo.setToolTipText("Enter Venue Number (e.g., 101)");

        cmbServerType = new JComboBox<>(new String[]{"MAIN SERVER (1)", "BACKUP SERVER (2)"});

        gridForm.add(new JLabel("Select Exam (exam_id):"));
        gridForm.add(cmbExams);
        gridForm.add(new JLabel("Venue No (venue_no):"));
        gridForm.add(txtVenueNo);
        gridForm.add(new JLabel("Server Type (system_type):"));
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

        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));

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

    // Enables instant search filtering when typing inside the dropdown
    private void setupSearchableComboBox(JComboBox<ExamItem> comboBox) {
        JTextField editor = (JTextField) comboBox.getEditor().getEditorComponent();
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                // Ignore navigation keys
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

        cmbExams.removeAllItems();
        for (ExamItem item : allExamsList) {
            if (item.examName.toLowerCase().contains(text) || item.examCode.toLowerCase().contains(text)) {
                cmbExams.addItem(item);
            }
        }

        editor.setText(text);
        try {
            editor.setCaretPosition(Math.min(caretPos, text.length()));
        } catch (Exception ignored) {}

        if (cmbExams.getItemCount() > 0) {
            cmbExams.showPopup();
        }
    }

    // API Call 1: Get Active Exams
    private void fetchActiveExamsInBackground() {
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(GET_EXAMS_URL)).GET().build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<ExamItem> parsedExams = parseExamsJson(response.body());
                    SwingUtilities.invokeLater(() -> {
                        allExamsList.clear();
                        allExamsList.addAll(parsedExams);

                        cmbExams.removeAllItems();
                        for (ExamItem item : allExamsList) {
                            cmbExams.addItem(item);
                        }

                        if (allExamsList.isEmpty()) {
                            lblStatus.setText("⚠️ Warning: No active exams found!");
                            lblStatus.setForeground(Color.ORANGE);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("❌ Failed to load exams (Status: " + response.statusCode() + ")");
                        lblStatus.setForeground(Color.RED);
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

    // Simple RegEx-based JSON parser for exam objects
    private List<ExamItem> parseExamsJson(String json) {
        List<ExamItem> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"exam_name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"examcode\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");
        Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            String name = matcher.group(2);
            String code = matcher.group(3);
            list.add(new ExamItem(id, name, code));
        }
        return list;
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

                serialNumber = fetchSerialWithPkexec();
                if (serialNumber.equals("Unknown") || serialNumber.isEmpty()) {
                    serialNumber = computerSystem.getSerialNumber();
                }

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

    private String fetchSerialWithPkexec() {
        String osName = System.getProperty("os.name").toLowerCase();

        // 1. Windows Serial Number Extraction
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
        }

        // 2. Linux / Ubuntu Serial Number Extraction (dmidecode via pkexec)
        else if (osName.contains("nix") || osName.contains("nux")) {
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
        Object selectedExamObj = cmbExams.getSelectedItem();
        ExamItem selectedExam = null;

        if (selectedExamObj instanceof ExamItem) {
            selectedExam = (ExamItem) selectedExamObj;
        } else if (selectedExamObj instanceof String) {
            String typedText = ((String) selectedExamObj).trim();
            for (ExamItem item : allExamsList) {
                if (item.examName.equalsIgnoreCase(typedText) || item.examCode.equalsIgnoreCase(typedText)) {
                    selectedExam = item;
                    break;
                }
            }
        }

        if (selectedExam == null) {
            lblStatus.setText("❌ Error: Please select a valid Exam from the list!");
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

        // Construct exact JSON Payload matching your team's API schema
        String jsonPayload = String.format(
                "{\n" +
                        "  \"exam_id\": %d,\n" +
                        "  \"system_details\": \"Serial: %s | Model: %s | IP: %s\",\n" +
                        "  \"venue_no\": %d,\n" +
                        "  \"system_macid\": \"%s\",\n" +
                        "  \"system_type\": %d,\n" +
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

        System.out.println("\n--- Sending Payload to: " + SAVE_SYSTEM_URL + " ---");
        System.out.println(jsonPayload);

        new Thread(() -> {
            boolean isSuccess = false;
            String serverMsg = "";

            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SAVE_SYSTEM_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                System.out.println("HTTP Response Status: " + response.statusCode());
                System.out.println("HTTP Response Body: " + response.body());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    isSuccess = true;
                } else {
                    serverMsg = "Status " + response.statusCode() + " - " + response.body();
                }
            } catch (Exception ex) {
                serverMsg = "Exception: " + ex.getMessage();
            }

            boolean finalSuccess = isSuccess;
            String finalMsg = serverMsg;

            SwingUtilities.invokeLater(() -> {
                btnSubmit.setEnabled(true);
                if (finalSuccess) {
                    lblStatus.setText("SUCCESS: Saved to CMS!");
                    lblStatus.setForeground(new Color(22, 163, 74));
                } else {
                    lblStatus.setText("FAILED: " + finalMsg);
                    lblStatus.setForeground(Color.RED);
                }
            });
        }).start();
    }

    // Helper Data Object for Exam Items
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
            return examName + " (" + examCode + ")";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LaptopScannerGUI().setVisible(true));
    }
}