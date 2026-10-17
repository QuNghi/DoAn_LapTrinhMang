package com.bomberman.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Màn hình Lobby – hỗ trợ Kết Nối Nhanh, Tạo Phòng Mới và Vào Phòng theo Mã.
 * Thiết kế phong cách Bomb It: nền tối gradient, bo góc, hiệu ứng hover mượt mà.
 */
public class LobbyPanel extends JPanel {

    // ── Callbacks ─────────────────────────────────────────────
    public interface LobbyCallback {
        void onQuickJoin(String playerName, String host, int port);
        void onCreateRoom(String playerName, String host, int port);
        void onJoinRoom(String roomCode, String playerName, String host, int port);
    }

    // Tương thích ngược với interface ConnectCallback cũ
    public interface ConnectCallback {
        void onConnect(String playerName, String host, int port);
    }

    // ── UI Fields ─────────────────────────────────────────────
    private final JTextField nameField;
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField roomCodeField;
    private final JButton quickJoinBtn;
    private final JButton createRoomBtn;
    private final JButton joinRoomBtn;
    private final JLabel statusLabel;
    private final LobbyCallback callback;

    // ── Màu sắc chuẩn Bomb It theme ───────────────────────────
    private static final Color BG_TOP    = new Color(0x0d1b2a);
    private static final Color BG_BOT    = new Color(0x1b2838);
    private static final Color CARD_BG   = new Color(0x1e2d3d);
    private static final Color ACCENT    = new Color(0xFF6B35);
    private static final Color ACCENT2   = new Color(0xFFD700);
    private static final Color ACCENT_GREEN = new Color(0x2ECC71);
    private static final Color ACCENT_BLUE  = new Color(0x3498DB);
    private static final Color TEXT_PRI  = new Color(0xFFFFFF);
    private static final Color TEXT_SEC  = new Color(0xA8B8C8);
    private static final Color FIELD_BG  = new Color(0x0d1b2a);
    private static final Color FIELD_BD  = new Color(0x2e4a62);

    public LobbyPanel(ConnectCallback legacyCallback) {
        this(new LobbyCallback() {
            @Override
            public void onQuickJoin(String name, String host, int port) {
                if (legacyCallback != null) legacyCallback.onConnect(name, host, port);
            }
            @Override
            public void onCreateRoom(String name, String host, int port) {
                if (legacyCallback != null) legacyCallback.onConnect(name, host, port);
            }
            @Override
            public void onJoinRoom(String roomCode, String name, String host, int port) {
                if (legacyCallback != null) legacyCallback.onConnect(name, host, port);
            }
        });
    }

    public LobbyPanel(LobbyCallback callback) {
        this.callback = callback;
        setLayout(new GridBagLayout());
        setOpaque(false);

        // ── Card container ────────────────────────────────────
        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                // Top border accent
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), 5, 5, 5);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(30, 44, 30, 44));
        card.setPreferredSize(new Dimension(460, 560));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 0, 14, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0;

        // ── Title ─────────────────────────────────────────────
        JPanel titlePanel = new JPanel(new BorderLayout(0, 4));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("💣 BOMB IT", SwingConstants.CENTER);
        titleLabel.setFont(loadFont(38f).deriveFont(Font.BOLD));
        titleLabel.setForeground(ACCENT);
        titlePanel.add(titleLabel, BorderLayout.CENTER);

        JLabel subLabel = new JLabel("MULTIPLAYER UDP", SwingConstants.CENTER);
        subLabel.setFont(loadFont(12f).deriveFont(Font.PLAIN));
        subLabel.setForeground(TEXT_SEC);
        titlePanel.add(subLabel, BorderLayout.SOUTH);

        card.add(titlePanel, gc);

        // ── Divider ───────────────────────────────────────────
        gc.gridy++;
        gc.insets = new Insets(0, 0, 18, 0);
        JPanel divider = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(FIELD_BD);
                g2.fillRect(0, 0, getWidth(), 1);
                g2.dispose();
            }
        };
        divider.setOpaque(false);
        divider.setPreferredSize(new Dimension(320, 1));
        card.add(divider, gc);

        // ── Name field ────────────────────────────────────────
        gc.gridy++;
        gc.insets = new Insets(0, 0, 12, 0);
        nameField = createStyledField("Tên người chơi", "Player1");
        card.add(buildFieldGroup("👤  TÊN NGƯỜI CHƠI", nameField), gc);

        // ── Host & Port row (gộp 2 cột) ───────────────────────
        gc.gridy++;
        JPanel netRow = new JPanel(new GridBagLayout());
        netRow.setOpaque(false);
        GridBagConstraints ngc = new GridBagConstraints();
        ngc.fill = GridBagConstraints.HORIZONTAL;
        ngc.weightx = 0.65;
        ngc.insets = new Insets(0, 0, 0, 8);
        hostField = createStyledField("IP Server", "127.0.0.1");
        netRow.add(buildFieldGroup("🌐  ĐỊA CHỈ IP", hostField), ngc);

        ngc.weightx = 0.35;
        ngc.insets = new Insets(0, 0, 0, 0);
        portField = createStyledField("Port", "9876");
        netRow.add(buildFieldGroup("🔌  PORT", portField), ngc);
        card.add(netRow, gc);

        // ── Status label ──────────────────────────────────────
        gc.gridy++;
        gc.insets = new Insets(0, 0, 10, 0);
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(loadFont(12f));
        statusLabel.setForeground(ACCENT);
        card.add(statusLabel, gc);

        // ── Buttons Action Section ────────────────────────────
        // 1. Nút Tạo Phòng Mới
        gc.gridy++;
        gc.insets = new Insets(0, 0, 10, 0);
        createRoomBtn = createActionButton("⊕  TẠO PHÒNG MỚI", ACCENT_GREEN, 44);
        createRoomBtn.addActionListener(e -> attemptCreateRoom());
        card.add(createRoomBtn, gc);

        // 2. Row Vào Phòng Theo Mã: [ Mã 4 ký tự ] [ Vào ]
        gc.gridy++;
        gc.insets = new Insets(0, 0, 10, 0);
        JPanel joinRow = new JPanel(new GridBagLayout());
        joinRow.setOpaque(false);
        GridBagConstraints jgc = new GridBagConstraints();
        jgc.fill = GridBagConstraints.BOTH;
        jgc.weightx = 0.65;
        jgc.insets = new Insets(0, 0, 0, 8);

        roomCodeField = createStyledField("Mã phòng (VD: AB3F)", "");
        // Tự động viết hoa và giới hạn 4 ký tự
        roomCodeField.setFont(loadFont(14f).deriveFont(Font.BOLD));
        roomCodeField.setForeground(ACCENT2);
        joinRow.add(roomCodeField, jgc);

        jgc.weightx = 0.35;
        jgc.insets = new Insets(0, 0, 0, 0);
        joinRoomBtn = createActionButton("→  VÀO PHÒNG", ACCENT_BLUE, 42);
        joinRoomBtn.addActionListener(e -> attemptJoinRoom());
        joinRow.add(joinRoomBtn, jgc);
        card.add(joinRow, gc);

        // 3. Nút Kết Nối Nhanh
        gc.gridy++;
        gc.insets = new Insets(0, 0, 0, 0);
        quickJoinBtn = createActionButton("⚡  KẾT NỐI NHANH", ACCENT, 44);
        quickJoinBtn.addActionListener(e -> attemptQuickJoin());
        card.add(quickJoinBtn, gc);

        // Hỗ trợ nhấn Enter
        nameField.addActionListener(e -> attemptQuickJoin());
        roomCodeField.addActionListener(e -> attemptJoinRoom());

        add(card);
    }

    private void attemptQuickJoin() {
        if (!validateInputs()) return;
        setButtonsEnabled(false);
        showStatus("🔄 Đang tìm phòng...", TEXT_SEC);
        if (callback != null) {
            callback.onQuickJoin(getNameInput(), getHostInput(), getPortInput());
        }
    }

    private void attemptCreateRoom() {
        if (!validateInputs()) return;
        setButtonsEnabled(false);
        showStatus("🔄 Đang tạo phòng mới...", TEXT_SEC);
        if (callback != null) {
            callback.onCreateRoom(getNameInput(), getHostInput(), getPortInput());
        }
    }

    private void attemptJoinRoom() {
        if (!validateInputs()) return;
        String code = roomCodeField.getText().trim().toUpperCase();
        if (code.isEmpty()) {
            showStatus("⚠ Vui lòng nhập mã phòng!", ACCENT);
            return;
        }
        setButtonsEnabled(false);
        showStatus("🔄 Đang vào phòng " + code + "...", TEXT_SEC);
        if (callback != null) {
            callback.onJoinRoom(code, getNameInput(), getHostInput(), getPortInput());
        }
    }

    private boolean validateInputs() {
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();

        if (name.isEmpty()) { showStatus("⚠ Vui lòng nhập tên người chơi!", ACCENT); return false; }
        if (host.isEmpty()) { showStatus("⚠ Vui lòng nhập địa chỉ Server!", ACCENT); return false; }

        try {
            int port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showStatus("⚠ Cổng kết nối không hợp lệ! (Ví dụ: 9876)", ACCENT);
            return false;
        }
        return true;
    }

    public String getNameInput() { return nameField.getText().trim(); }
    public String getHostInput() { return hostField.getText().trim(); }
    public int getPortInput() { return Integer.parseInt(portField.getText().trim()); }

    public void showStatus(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setForeground(color);
        });
    }

    public void setButtonsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            quickJoinBtn.setEnabled(enabled);
            createRoomBtn.setEnabled(enabled);
            joinRoomBtn.setEnabled(enabled);
        });
    }

    public void setConnectEnabled(boolean enabled) {
        setButtonsEnabled(enabled);
    }

    private JPanel buildFieldGroup(String labelText, JTextField field) {
        JPanel group = new JPanel(new BorderLayout(0, 4));
        group.setOpaque(false);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(loadFont(11f).deriveFont(Font.BOLD));
        lbl.setForeground(TEXT_SEC);
        group.add(lbl, BorderLayout.NORTH);
        group.add(field, BorderLayout.CENTER);
        return group;
    }

    private JTextField createStyledField(String placeholder, String defaultValue) {
        JTextField field = new JTextField(defaultValue) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FIELD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(isFocusOwner() ? ACCENT : FIELD_BD);
                g2.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setForeground(TEXT_PRI);
        field.setCaretColor(ACCENT);
        field.setFont(loadFont(13f));
        field.setBorder(new EmptyBorder(8, 12, 8, 12));
        field.setToolTipText(placeholder);
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { field.repaint(); }
            @Override public void focusLost(FocusEvent e)   { field.repaint(); }
        });
        return field;
    }

    private JButton createActionButton(String text, Color baseColor, int height) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isEnabled() ? (getModel().isPressed() ? baseColor.darker()
                                        : getModel().isRollover() ? baseColor.brighter() : baseColor)
                                       : new Color(0x3a, 0x48, 0x56);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(loadFont(13f).deriveFont(Font.BOLD));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(300, height));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOT);
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
        // Decor circles
        g2.setColor(new Color(0xFF, 0x6B, 0x35, 0x14));
        g2.fillOval(60, 60, 140, 140);
        g2.setColor(new Color(0xFF, 0xD7, 0x00, 0x10));
        g2.fillOval(getWidth() - 100, getHeight() - 100, 160, 160);
        g2.dispose();
        super.paintComponent(g);
    }

    private Font loadFont(float size) {
        return new Font("Segoe UI", Font.PLAIN, (int) size);
    }
}
