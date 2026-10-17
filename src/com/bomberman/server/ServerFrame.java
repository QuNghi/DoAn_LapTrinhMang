package com.bomberman.server;

import com.bomberman.common.GameConstants;
import com.bomberman.server.model.Player;
import com.bomberman.server.model.Room;
import com.bomberman.server.network.PlayerSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

/**
 * Giao diện đồ họa (GUI) quản lý Server Bomberman Multiplayer.
 * Hiển thị danh sách phòng, người chơi trong phòng, và các nút điều khiển quản trị.
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

    private static final Color BG_DARK      = new Color(0x0d, 0x1b, 0x2a);
    private static final Color PANEL_BG     = new Color(0x16, 0x24, 0x36);
    private static final Color TABLE_BG     = new Color(0x1c, 0x2d, 0x42);
    private static final Color ACCENT_ORANGE= new Color(0xFF, 0x6B, 0x35);
    private static final Color ACCENT_GOLD  = new Color(0xFF, 0xD7, 0x00);
    private static final Color ACCENT_GREEN = new Color(0x2E, 0xCC, 0x71);
    private static final Color ACCENT_RED   = new Color(0xE7, 0x4C, 0x3C);
    private static final Color TEXT_PRI     = Color.WHITE;
    private static final Color TEXT_SEC     = new Color(0xA8, 0xB8, 0xC8);

    public ServerFrame(RoomManager roomManager, int port) {
        super("💣 Bomberman Server Control Center");
        this.roomManager = roomManager;
        this.port = port;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(14, 16, 14, 16));

        // ── TOP: Header & Stat Badges ─────────────────────────
        JPanel topPanel = new JPanel(new BorderLayout(15, 0));
        topPanel.setOpaque(false);

        JLabel title = new JLabel("BOMBERMAN UDP SERVER MANAGER");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(ACCENT_ORANGE);
        topPanel.add(title, BorderLayout.WEST);

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        badges.setOpaque(false);

        lblStatus = createBadge("🟢 ONLINE", ACCENT_GREEN);
        lblPort = createBadge("CỔNG: " + port, new Color(0x34, 0x98, 0xDB));
        lblRoomCount = createBadge("PHÒNG: 0", ACCENT_GOLD);
        lblPlayerCount = createBadge("NGƯỜI CHƠI: 0", ACCENT_ORANGE);

        badges.add(lblStatus);
        badges.add(lblPort);
        badges.add(lblRoomCount);
        badges.add(lblPlayerCount);
        topPanel.add(badges, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // ── CENTER: Split Pane (Rooms Table + Players Table) ──
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setOpaque(false);
        centerSplit.setDividerLocation(520);
        centerSplit.setResizeWeight(0.55);
        centerSplit.setBorder(null);

        // --- Cột Trái: Danh Sách Phòng ---
        JPanel leftPanel = new JPanel(new BorderLayout(0, 8));
        leftPanel.setOpaque(false);

        JLabel roomsTitle = new JLabel("📋 DANH SÁCH PHÒNG CHƠI (ROOMS)");
        roomsTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        roomsTitle.setForeground(TEXT_SEC);
        leftPanel.add(roomsTitle, BorderLayout.NORTH);

        // Khởi tạo model và table người chơi trước để callback có thể truy cập an toàn
        String[] playerCols = {"ID", "Tên", "Vai Trò", "Sống?", "Vũ Khí", "IP:Port"};
        playersModel = new DefaultTableModel(playerCols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        playersTable = createStyledTable(playersModel);

        String[] roomCols = {"Mã Phòng", "Trạng Thái", "Người Chơi", "ID Host", "Thời Gian Tạo"};
        roomsModel = new DefaultTableModel(roomCols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        roomsTable = createStyledTable(roomsModel);
        roomsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = roomsTable.getSelectedRow();
                if (row >= 0) {
                    selectedRoomCode = (String) roomsModel.getValueAt(row, 0);
                    refreshPlayersTable();
                } else {
                    selectedRoomCode = null;
                    playersModel.setRowCount(0);
                }
            }
        });

        JScrollPane roomsScroll = new JScrollPane(roomsTable);
        roomsScroll.getViewport().setBackground(TABLE_BG);
        roomsScroll.setBorder(BorderFactory.createLineBorder(new Color(0x27, 0x3b, 0x54)));
        leftPanel.add(roomsScroll, BorderLayout.CENTER);

        // Nút hành động Phòng
        JPanel roomBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        roomBtns.setOpaque(false);

        JButton btnCreateRoom = createBtn("⊕ Tạo Phòng", ACCENT_GREEN);
        btnCreateRoom.addActionListener(e -> {
            try {
                Room room = roomManager.createEmptyRoom();
                log("Đã tạo phòng thủ công: " + room.getRoomCode());
                refreshRoomsTable();
            } catch (Exception ex) {
                log("Lỗi tạo phòng: " + ex.getMessage());
            }
        });

        JButton btnStartRoom = createBtn("▶ Bắt Đầu", ACCENT_GOLD);
        btnStartRoom.addActionListener(e -> {
            if (selectedRoomCode != null) {
                roomManager.forceStartRoom(selectedRoomCode);
                log("Force start phòng: " + selectedRoomCode);
                refreshRoomsTable();
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn phòng để bắt đầu!");
            }
        });

        JButton btnDeleteRoom = createBtn("🗑 Xóa Phòng", ACCENT_RED);
        btnDeleteRoom.addActionListener(e -> {
            if (selectedRoomCode != null) {
                roomManager.deleteRoom(selectedRoomCode);
                log("Đã xóa phòng: " + selectedRoomCode);
                selectedRoomCode = null;
                refreshRoomsTable();
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn phòng để xóa!");
            }
        });

        roomBtns.add(btnCreateRoom);
        roomBtns.add(btnStartRoom);
        roomBtns.add(btnDeleteRoom);
        leftPanel.add(roomBtns, BorderLayout.SOUTH);

        centerSplit.setLeftComponent(leftPanel);

        // --- Cột Phải: Danh Sách Người Chơi Trong Phòng ---
        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setOpaque(false);

        JLabel playersTitle = new JLabel("👥 NGƯỜI CHƠI TRONG PHÒNG");
        playersTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        playersTitle.setForeground(TEXT_SEC);
        rightPanel.add(playersTitle, BorderLayout.NORTH);

        JScrollPane playersScroll = new JScrollPane(playersTable);
        playersScroll.getViewport().setBackground(TABLE_BG);
        playersScroll.setBorder(BorderFactory.createLineBorder(new Color(0x27, 0x3b, 0x54)));
        rightPanel.add(playersScroll, BorderLayout.CENTER);

        // Nút Kick người chơi
        JPanel playerBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        playerBtns.setOpaque(false);

        JButton btnKick = createBtn("⛔ Kick Người Chơi", ACCENT_RED);
        btnKick.addActionListener(e -> {
            int row = playersTable.getSelectedRow();
            if (row >= 0 && selectedRoomCode != null) {
                int pid = (int) playersModel.getValueAt(row, 0);
                roomManager.kickPlayer(selectedRoomCode, pid);
                log("Đã kick Player " + pid + " khỏi phòng " + selectedRoomCode);
                refreshPlayersTable();
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn người chơi để kick!");
            }
        });
        playerBtns.add(btnKick);
        rightPanel.add(playerBtns, BorderLayout.SOUTH);

        centerSplit.setRightComponent(rightPanel);

        add(centerSplit, BorderLayout.CENTER);

        // ── BOTTOM: Activity Log ──────────────────────────────
        JPanel botPanel = new JPanel(new BorderLayout(0, 4));
        botPanel.setOpaque(false);
        botPanel.setPreferredSize(new Dimension(800, 150));

        JLabel logTitle = new JLabel("📜 NHẬT KÝ HOẠT ĐỘNG (LIVE EVENT LOG)");
        logTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logTitle.setForeground(TEXT_SEC);
        botPanel.add(logTitle, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(0x0a, 0x12, 0x1d));
        logArea.setForeground(new Color(0x90, 0xEE, 0x90)); // Soft green
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(0x27, 0x3b, 0x54)));
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

        lblRoomCount.setText("PHÒNG: " + rooms.size());
        lblPlayerCount.setText("NGƯỜI CHƠI: " + totalPlayers);

        // Ghi nhớ selection hiện tại
        String currentSelected = selectedRoomCode;

        roomsModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        for (Room r : rooms) {
            roomsModel.addRow(new Object[]{
                    r.getRoomCode(),
                    r.getStateName(),
                    r.getPlayerCount() + "/" + GameConstants.MAX_PLAYERS,
                    r.getHostPlayerId() > 0 ? "P" + r.getHostPlayerId() : "Chưa có",
                    sdf.format(new Date(r.getCreatedAt()))
            });
        }

        // Khôi phục selection nếu còn tồn tại
        if (currentSelected != null) {
            for (int i = 0; i < roomsModel.getRowCount(); i++) {
                if (currentSelected.equals(roomsModel.getValueAt(i, 0))) {
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
            String role = ps.isHost() ? "👑 Chủ phòng" : "Thành viên";
            String alive = p.isAlive() ? "✔ Sống" : "☠ KO";
            String weapon = p.hasWeapon() ? "🔫 " + p.getWeaponAmmo() + " đạn" : "💣 Bom";
            String addr = ps.getAddress().getHostAddress() + ":" + ps.getPort();

            playersModel.addRow(new Object[]{
                    p.getId(),
                    p.getName(),
                    role,
                    alive,
                    weapon,
                    addr
            });
        }
    }

    private JLabel createBadge(String text, Color bg) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(Color.WHITE);
        lbl.setBackground(bg);
        lbl.setOpaque(true);
        lbl.setBorder(new EmptyBorder(5, 12, 5, 12));
        return lbl;
    }

    private JButton createBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(TABLE_BG);
        table.setForeground(TEXT_PRI);
        table.setGridColor(new Color(0x27, 0x3b, 0x54));
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setBackground(PANEL_BG);
        table.getTableHeader().setForeground(ACCENT_GOLD);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, center);

        return table;
    }
}
