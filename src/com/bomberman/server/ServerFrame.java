package com.bomberman.server;

import com.bomberman.common.GameConstants;
import com.bomberman.server.model.Player;
import com.bomberman.server.model.Room;
import com.bomberman.server.network.PlayerSession;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

/**
 * Giao diện đồ họa (GUI) quản trị Server Bomberman Multiplayer.
 * Phong cách Modern Light Dashboard (Nền trắng, chữ đen, hiện đại, chuyên nghiệp).
 * Phân quyền tối giản: Chỉ hiển thị dữ liệu và cho phép Xóa phòng treo tránh quá tải hệ thống.
 * Không can thiệp vào Tạo phòng, Bắt đầu trận hay Kick người chơi (chuyển giao cho Client Host).
 */
public class ServerFrame extends JFrame {

    private final RoomManager roomManager;
    private final int port;

    private final JLabel lblPort;
    private final JLabel lblStatus;
    private final JLabel lblRoomCount;
    private final JLabel lblPlayerCount;

    private final JTable roomsTable;
    private final DefaultTableModel roomsModel;

    private final JTable playersTable;
    private final DefaultTableModel playersModel;

    private final JTextArea logArea;
    private String selectedRoomCode = null;

    // ── Bảng màu Modern Light Dashboard ───────────────────────
    private static final Color BG_WINDOW    = new Color(0xF8, 0xFA, 0xFC); // Slate 50
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color BORDER_LIGHT = new Color(0xE2, 0xE8, 0xF0); // Slate 200
    private static final Color HEADER_BG    = new Color(0xF1, 0xF5, 0xF9); // Slate 100
    private static final Color ROW_ALT_BG   = new Color(0xFA, 0xFA, 0xFB);
    private static final Color SELECT_BG    = new Color(0xEB, 0xF4, 0xFF); // Soft Indigo
    private static final Color TEXT_PRI     = new Color(0x0F, 0x17, 0x2A); // Slate 900
    private static final Color TEXT_SEC     = new Color(0x47, 0x55, 0x69); // Slate 600
    private static final Color TEXT_MUTED   = new Color(0x94, 0xA3, 0xB8); // Slate 400
    private static final Color ACCENT_BLUE  = new Color(0x25, 0x63, 0xEB); // Blue 600
    private static final Color ACCENT_RED   = new Color(0xDC, 0x26, 0x26); // Red 600
    private static final Color ACCENT_GREEN = new Color(0x16, 0xA3, 0x4A); // Green 600
    private static final Color ACCENT_AMBER = new Color(0xD9, 0x77, 0x06); // Amber 600
    private static final Color ACCENT_PURPLE= new Color(0x7C, 0x3A, 0xED); // Purple 600

    public ServerFrame(RoomManager roomManager, int port) {
        super("Bomberman Multiplayer - Server Control Center");
        this.roomManager = roomManager;
        this.port = port;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 720);
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_WINDOW);

        setLayout(new BorderLayout(12, 12));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(16, 18, 16, 18));

        // ── TOP: Header & Stat KPI Badges ─────────────────────
        JPanel topPanel = new JPanel(new BorderLayout(15, 0));
        topPanel.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBox.setOpaque(false);

        JLabel title = new JLabel("BOMBERMAN UDP SERVER DASHBOARD");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_PRI);

        JLabel subTitle = new JLabel("Hệ thống quản lý phòng & giám sát kết nối thời gian thực");
        subTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subTitle.setForeground(TEXT_SEC);

        titleBox.add(title);
        titleBox.add(subTitle);
        topPanel.add(titleBox, BorderLayout.WEST);

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        badges.setOpaque(false);

        lblStatus = createPillBadge("[ON] TRUC TUYEN", new Color(0xDC, 0xFC, 0xE7), ACCENT_GREEN);
        lblPort = createPillBadge("CONG: " + port, new Color(0xDB, 0xEA, 0xFE), ACCENT_BLUE);
        lblRoomCount = createPillBadge("PHONG: 0", new Color(0xF3, 0xE8, 0xFF), ACCENT_PURPLE);
        lblPlayerCount = createPillBadge("NGUOI CHOI: 0", new Color(0xFE, 0xF3, 0xC7), ACCENT_AMBER);

        badges.add(lblStatus);
        badges.add(lblPort);
        badges.add(lblRoomCount);
        badges.add(lblPlayerCount);
        topPanel.add(badges, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // ── CENTER: Split Pane (Rooms Table + Players Table) ──
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setOpaque(false);
        centerSplit.setDividerLocation(580);
        centerSplit.setResizeWeight(0.55);
        centerSplit.setBorder(null);

        // --- Cột Trái: Danh Sách Phòng Chơi ---
        JPanel leftPanel = new JPanel(new BorderLayout(0, 8));
        leftPanel.setOpaque(false);

        JLabel roomsTitle = new JLabel("[=] DANH SACH PHONG CHOI (ROOMS)");
        roomsTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        roomsTitle.setForeground(TEXT_PRI);
        leftPanel.add(roomsTitle, BorderLayout.NORTH);

        String[] playerCols = {"ID", "Ten Nguoi Choi", "Vai Tro", "Trang Thai", "Vu Khi", "Dia Chi IP:Port"};
        playersModel = new DefaultTableModel(playerCols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        playersTable = createStyledTable(playersModel, false);

        String[] roomCols = {"STT", "Ma Phong", "Ten Phong", "Trang Thai", "So Nguoi", "Chu Phong", "Ban Do", "Thoi Gian"};
        roomsModel = new DefaultTableModel(roomCols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        roomsTable = createStyledTable(roomsModel, true);
        roomsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = roomsTable.getSelectedRow();
                if (row >= 0) {
                    selectedRoomCode = (String) roomsModel.getValueAt(row, 1);
                    refreshPlayersTable();
                } else {
                    selectedRoomCode = null;
                    playersModel.setRowCount(0);
                }
            }
        });

        JScrollPane roomsScroll = new JScrollPane(roomsTable);
        roomsScroll.getViewport().setBackground(CARD_BG);
        roomsScroll.setBorder(new LineBorder(BORDER_LIGHT, 1, true));
        leftPanel.add(roomsScroll, BorderLayout.CENTER);

        // Thanh thao tác phía dưới bảng Phòng: CHỈ CÓ NÚT XÓA PHÒNG TREO
        JPanel roomActionRow = new JPanel(new BorderLayout(10, 0));
        roomActionRow.setOpaque(false);

        JButton btnDeleteRoom = createButton("[DEL] Xoa Phong Treo", ACCENT_RED, Color.WHITE);
        btnDeleteRoom.setToolTipText("Xoa phong chi dinh khi bi treo hoac bo hoang de giai phong tai nguyen he thong");
        btnDeleteRoom.addActionListener(e -> {
            if (selectedRoomCode != null) {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Ban co chac muon xoa phong [" + selectedRoomCode + "] khong?\nThao tac nay se ngat ket noi cac nguoi choi trong phong (neu co).",
                        "Xac nhan xoa phong",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    roomManager.deleteRoom(selectedRoomCode);
                    log("Da giai phong phong treo: " + selectedRoomCode);
                    selectedRoomCode = null;
                    refreshRoomsTable();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Vui long chon phong can xoa trong danh sach!");
            }
        });

        JLabel serverHintLbl = new JLabel("[i] Server chi quan tri xoa phong rac. Quyen bat dau, kick nguoi choi do Client Chu phong xu ly.");
        serverHintLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        serverHintLbl.setForeground(TEXT_MUTED);

        roomActionRow.add(btnDeleteRoom, BorderLayout.WEST);
        roomActionRow.add(serverHintLbl, BorderLayout.CENTER);
        leftPanel.add(roomActionRow, BorderLayout.SOUTH);

        centerSplit.setLeftComponent(leftPanel);

        // --- Cột Phải: Danh Sách Người Chơi Trong Phòng ---
        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setOpaque(false);

        JLabel playersTitle = new JLabel("[P] NGUOI CHOI TRONG PHONG DUOC CHON");
        playersTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        playersTitle.setForeground(TEXT_PRI);
        rightPanel.add(playersTitle, BorderLayout.NORTH);

        JScrollPane playersScroll = new JScrollPane(playersTable);
        playersScroll.getViewport().setBackground(CARD_BG);
        playersScroll.setBorder(new LineBorder(BORDER_LIGHT, 1, true));
        rightPanel.add(playersScroll, BorderLayout.CENTER);

        // Chú thích phân quyền phía dưới bảng người chơi
        JPanel playerNoteRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        playerNoteRow.setOpaque(false);
        JLabel clientControlLbl = new JLabel("[HOST] Chu phong (Client Host) co quyen kick thanh vien truc tiep trong phong cho.");
        clientControlLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        clientControlLbl.setForeground(TEXT_MUTED);
        playerNoteRow.add(clientControlLbl);
        rightPanel.add(playerNoteRow, BorderLayout.SOUTH);

        centerSplit.setRightComponent(rightPanel);

        add(centerSplit, BorderLayout.CENTER);

        // ── BOTTOM: Activity Log ──────────────────────────────
        JPanel botPanel = new JPanel(new BorderLayout(0, 6));
        botPanel.setOpaque(false);
        botPanel.setPreferredSize(new Dimension(800, 150));

        JLabel logTitle = new JLabel("[LOG] NHAT KY HOAT DONG (EVENT LOG)");
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logTitle.setForeground(TEXT_PRI);
        botPanel.add(logTitle, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(CARD_BG);
        logArea.setForeground(TEXT_PRI);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new LineBorder(BORDER_LIGHT, 1, true));
        botPanel.add(logScroll, BorderLayout.CENTER);

        add(botPanel, BorderLayout.SOUTH);

        // Đăng ký listener từ RoomManager
        roomManager.addListener(this::onRoomsUpdated);

        // Timer làm mới định kỳ mỗi 1 giây
        Timer timer = new Timer(1000, e -> {
            refreshRoomsTable();
            if (selectedRoomCode != null) {
                refreshPlayersTable();
            }
        });
        timer.start();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                roomManager.stopAll();
            }
        });

        log("Khởi động Bomberman Server tại cổng " + port + " thành công!");
        log("Hệ thống đã sẵn sàng đón nhận kết nối từ Client.");
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + time + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void onRoomsUpdated() {
        SwingUtilities.invokeLater(() -> {
            refreshRoomsTable();
            if (selectedRoomCode != null) {
                refreshPlayersTable();
            }
        });
    }

    public synchronized void refreshRoomsTable() {
        Collection<Room> rooms = roomManager.getAllRooms();
        int totalPlayers = roomManager.getTotalPlayers();

        lblRoomCount.setText("PHONG: " + rooms.size());
        lblPlayerCount.setText("NGUOI CHOI: " + totalPlayers);

        String currentSelected = selectedRoomCode;
        roomsModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        int stt = 1;
        for (Room r : rooms) {
            String hostName = r.getHostName();
            if (hostName.isEmpty() && r.getHostPlayerId() > 0) {
                hostName = "P" + r.getHostPlayerId();
            } else if (hostName.isEmpty()) {
                hostName = "Chưa có";
            }

            roomsModel.addRow(new Object[]{
                    stt++,
                    r.getRoomCode(),
                    r.getRoomName(),
                    r.getStateName(),
                    r.getPlayerCount() + "/" + GameConstants.MAX_PLAYERS,
                    hostName,
                    r.getMapName(),
                    sdf.format(new Date(r.getCreatedAt()))
            });
        }

        // Khôi phục selection
        if (currentSelected != null) {
            for (int i = 0; i < roomsModel.getRowCount(); i++) {
                if (currentSelected.equals(roomsModel.getValueAt(i, 1))) {
                    roomsTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    public synchronized void refreshPlayersTable() {
        playersModel.setRowCount(0);
        if (selectedRoomCode == null) return;

        Room room = roomManager.getRoom(selectedRoomCode);
        if (room == null) return;

        for (PlayerSession ps : room.getSessions()) {
            Player p = ps.getPlayer();
            String role = ps.isHost() ? "[HOST] Chu phong" : "Thanh vien";
            String status = p.isAlive() ? "[OK] Song" : "[X] Da bi loai";
            String weapon = p.hasWeapon() ? "[GUN] " + p.getWeaponAmmo() + " dan" : "[BOM] Bom";
            String addr = ps.getAddress().getHostAddress() + ":" + ps.getPort();

            playersModel.addRow(new Object[]{
                    p.getId(),
                    p.getName(),
                    role,
                    status,
                    weapon,
                    addr
            });
        }
    }

    private JLabel createPillBadge(String text, Color bg, Color fg) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(fg);
        lbl.setBackground(bg);
        lbl.setOpaque(true);
        lbl.setBorder(new CompoundBorder(
                new LineBorder(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60), 1, true),
                new EmptyBorder(5, 12, 5, 12)
        ));
        return lbl;
    }

    private JButton createButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setOpaque(true);  // Bắt buộc để Metal L&F vẽ màu nền
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(
                new LineBorder(bg.darker(), 1, true),
                new EmptyBorder(6, 14, 6, 14)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JTable createStyledTable(DefaultTableModel model, boolean isRoomTable) {
        JTable table = new JTable(model);
        table.setBackground(CARD_BG);
        table.setForeground(TEXT_PRI);
        table.setGridColor(BORDER_LIGHT);
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setSelectionBackground(SELECT_BG);
        table.setSelectionForeground(TEXT_PRI);
        table.setShowGrid(true);

        table.getTableHeader().setBackground(HEADER_BG);
        table.getTableHeader().setForeground(TEXT_PRI);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setPreferredSize(new Dimension(0, 32));
        table.getTableHeader().setBorder(new LineBorder(BORDER_LIGHT, 1));

        // Renderer căn giữa và tô màu trạng thái
        DefaultTableCellRenderer customRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);

                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? CARD_BG : ROW_ALT_BG);
                    c.setForeground(TEXT_PRI);

                    // Tô màu cột Trạng thái
                    String strVal = value != null ? value.toString() : "";
                    if (strVal.equals("WAITING")) {
                        c.setForeground(ACCENT_BLUE);
                    } else if (strVal.equals("COUNTDOWN")) {
                        c.setForeground(ACCENT_AMBER);
                    } else if (strVal.equals("PLAYING") || strVal.equals("[OK] Song")) {
                        c.setForeground(ACCENT_GREEN);
                    } else if (strVal.equals("ENDED") || strVal.equals("[X] Da bi loai")) {
                        c.setForeground(ACCENT_RED);
                    } else if (strVal.contains("[HOST] Chu phong")) {
                        c.setForeground(ACCENT_AMBER);
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    }
                }
                return c;
            }
        };

        table.setDefaultRenderer(Object.class, customRenderer);
        return table;
    }
}
