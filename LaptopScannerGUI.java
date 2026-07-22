import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

public class LaptopScannerGUI extends JFrame {

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

    // TODO: Update URL to match your teammate's actual CMS API endpoint
    private static final String CMS_API_URL = "https://cms.cditproject.org/api/saveExamAllotedSystem";

    public LaptopScannerGUI() {
        setTitle("C-DIT CBT Server Inventory & Registration Agent");
        setSize(580, 660);
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
        JPanel gridSpecs = new JPanel(new GridLayout(8, 2, 10, 8));

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
        JPanel formPanel = createCardPanel("Venue Configuration");
        JPanel gridForm = new JPanel(new GridLayout(2, 2, 10, 10));

        txtVenueNo = new JTextField();
        txtVenueNo.setToolTipText("Enter Venue Number (e.g., 101)");

        cmbServerType = new JComboBox<>(new String[]{"MAIN SERVER (1)", "BACKUP SERVER (2)"});

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

        // Run background hardware scan
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

    private void scanHardwareInBackground() {
        new Thread(() -> {
            try {
                SystemInfo si = new SystemInfo();
                HardwareAbstractionLayer hal = si.getHardware();
                OperatingSystem os = si.getOperatingSystem();
                ComputerSystem computerSystem = hal.getComputerSystem();

                systemName = computerSystem.getManufacturer() + " " + computerSystem.getModel();
                systemOs = os.getFamily() + " " + os.getVersionInfo().getVersion();

                // 1. Fetch Processor Specs
                CentralProcessor proc = hal.getProcessor();
                processorInfo = proc.getProcessorIdentifier().getName().trim();

                // 2. Fetch Serial Number
                serialNumber = fetchSerialWithPkexec();
                if (serialNumber.equals("Unknown") || serialNumber.isEmpty()) {
                    serialNumber = computerSystem.getSerialNumber();
                }

                // 3. Fetch RAM Details
                GlobalMemory memory = hal.getMemory();
                long totalRamGb = memory.getTotal() / (1024 * 1024 * 1024);
                ram = totalRamGb + "GB";

                // 4. Fetch Storage Devices
                List<HWDiskStore> diskStores = hal.getDiskStores();
                StringBuilder diskStr = new StringBuilder();
                for (HWDiskStore disk : diskStores) {
                    if (disk.getSize() > 0) {
                        long sizeGb = disk.getSize() / (1024 * 1024 * 1024);
                        diskStr.append(sizeGb).append("GB Storage; ");
                    }
                }
                storage = diskStr.length() > 0 ? diskStr.toString().trim() : "Unknown";

                // 5. Fetch Physical MAC Address
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

                // 6. Fetch Active IP
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
                    cmbServerType.setSelectedIndex(0); // Main Server = 1
                } else if (activeIp.endsWith(".2")) {
                    cmbServerType.setSelectedIndex(1); // Backup Server = 2
                }
            });
        }).start();
    }

    private String fetchSerialWithPkexec() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"pkexec", "dmidecode", "-s", "system-serial-number"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String serial = reader.readLine();
            return (serial != null && !serial.isBlank()) ? serial.trim() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
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

    private void submitDataToCms() {
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

        // system_type: 1 for Main Server, 2 for Backup Server
        int systemType = (cmbServerType.getSelectedIndex() == 0) ? 1 : 2;

        btnSubmit.setEnabled(false);
        lblStatus.setText("Connecting to CMS API...");
        lblStatus.setForeground(Color.BLUE);

        // Construct exact JSON Payload required by backend API
        String jsonPayload = String.format(
            "{\n" +
            "  \"system_details\": \"Serial: %s | Model: %s | IP: %s\",\n" +
            "  \"venue_no\": %d,\n" +
            "  \"system_type\": %d,\n" +
            "  \"system_macid\": \"%s\",\n" +
            "  \"system_ram\": \"%s\",\n" +
            "  \"system_memory\": \"%s\",\n" +
            "  \"system_name\": \"%s\",\n" +
            "  \"system_os\": \"%s\",\n" +
            "  \"system_cpu\": \"%s\"\n" +
            "}",
            serialNumber, systemName, activeIp,
            venueNo,
            systemType,
            macAddress,
            ram,
            storage,
            systemName,
            systemOs,
            processorInfo
        );

        new Thread(() -> {
            boolean isSuccess = false;
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(CMS_API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                isSuccess = (response.statusCode() == 200 || response.statusCode() == 201);
            } catch (Exception ex) {
                isSuccess = false;
            }

            boolean finalSuccess = isSuccess;
            SwingUtilities.invokeLater(() -> {
                btnSubmit.setEnabled(true);
                if (finalSuccess) {
                    lblStatus.setText("SUCCESS: Registered to CMS!");
                    lblStatus.setForeground(new Color(22, 163, 74));
                } else {
                    lblStatus.setText("FAILED: Could not reach CMS.");
                    lblStatus.setForeground(Color.RED);
                }
            });
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LaptopScannerGUI().setVisible(true));
    }
}
