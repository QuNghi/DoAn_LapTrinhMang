package com.bomberman.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Màn hình Lobby – người chơi nhập tên, IP Server và Port rồi kết nối.
 * Thiết kế tham khảo Bomb It: nền tối gradient, bo góc, hiệu ứng hover.
 */
public class LobbyPanel extends JPanel {

    // ── Callback ──────────────────────────────────────────────
    public interface ConnectCallback {
        void onConnect(String playerName, String host, int port);
    }

    // ── UI Fields ─────────────────────────────────────────────
    private final JTextField nameField;
    private final JTextField hostField;
    private final JTextField portField;
    private final JButton connectBtn;
    private final JLabel statusLabel;
    private final ConnectCallback callback;

    // ── Màu sắc ───────────────────────────────────────────────
    private static final Color BG_TOP    = new Color(0x0d1b2a);
    private static final Color BG_BOT    = new Color(0x1b2838);
    private static final Color CARD_BG   = new Color(0x1e2d3d);
    private static final Color ACCENT    = new Color(0xFF6B35);
    private static final Color ACCENT2   = new Color(0xFFD700);
    private static final Color TEXT_PRI  = new Color(0xFFFFFF);
    private static final Color TEXT_SEC  = new Color(0xA8B8C8);
    private static final Color FIELD_BG  = new Color(0x0d1b2a);
    private static final Color FIELD_BD  = new Color(0x2e4a62);

    public LobbyPanel(ConnectCallback callback) {
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
                g2.fillRoundRect(0, 0, getWidth(), 4, 4, 4);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(40, 50, 40, 50));
        card.setPreferredSize(new Dimension(440, 480));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 0, 20, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0;

        // ── Title ─────────────────────────────────────────────
        JPanel titlePanel = new JPanel(new BorderLayout(0, 4));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("💣 BOMB IT", SwingConstants.CENTER);
        titleLabel.setFont(loadFont(42f).deriveFont(Font.BOLD));
        titleLabel.setForeground(ACCENT);
        titlePanel.add(titleLabel, BorderLayout.CENTER);

        JLabel subLabel = new JLabel("MULTIPLAYER", SwingConstants.CENTER);
        subLabel.setFont(loadFont(13f).deriveFont(Font.PLAIN));
        subLabel.setForeground(TEXT_SEC);
        titlePanel.add(subLabel, BorderLayout.SOUTH);

        card.add(titlePanel, gc);

        // ── Divider ───────────────────────────────────────────
        gc.gridy++;
        gc.insets = new Insets(0, 0, 28, 0);
        JPanel divider = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(FIELD_BD);
                g2.fillRect(0, 0, getWidth(), 1);
                g2.dispose();
            }
        };
        divider.setOpaque(false);
        divider.setPreferredSize(new Dimension(300, 1));
        card.add(divider, gc);

        // ── Name field ────────────────────────────────────────
        gc.gridy++;
        gc.insets = new Insets(0, 0, 16, 0);
        nameField = createStyledField("Tên người chơi", "Player1");
        card.add(buildFieldGroup("👤  TÊN NGƯỜI CHƠI", nameField), gc);

        // ── Host field ────────────────────────────────────────
        gc.gridy++;
        hostField = createStyledField("Địa chỉ Server", "127.0.0.1");
        card.add(buildFieldGroup("🌐  ĐỊA CHỈ SERVER (IP)", hostField), gc);

        // ── Port field ────────────────────────────────────────
        gc.gridy++;
        portField = createStyledField("Cổng kết nối", "9876");
        card.add(buildFieldGroup("🔌  CỔNG (PORT)", portField), gc);

        // ── Status label ──────────────────────────────────────
        gc.gridy++;
        gc.insets = new Insets(0, 0, 8, 0);
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(loadFont(12f));
        statusLabel.setForeground(ACCENT);
        card.add(statusLabel, gc);

        // ── Connect button ────────────────────────────────────
        gc.gridy++;
        gc.insets = new Insets(0, 0, 0, 0);
        connectBtn = new JButton("⚡  KẾT NỐI VÀO PHÒNG") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? ACCENT.darker()
                        : getModel().isRollover() ? ACCENT.brighter()
                        : ACCENT;
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        connectBtn.setFont(loadFont(15f).deriveFont(Font.BOLD));
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setOpaque(false);
        connectBtn.setContentAreaFilled(false);
        connectBtn.setBorderPainted(false);
        connectBtn.setFocusPainted(false);
        connectBtn.setPreferredSize(new Dimension(300, 50));
        connectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        connectBtn.addActionListener(e -> attemptConnect());

        // Cho phép nhấn Enter để kết nối
        nameField.addActionListener(e -> attemptConnect());
        hostField.addActionListener(e -> attemptConnect());
        portField.addActionListener(e -> attemptConnect());

        card.add(connectBtn, gc);

        // ── Thêm card vào panel ───────────────────────────────
        add(card);
    }

    // ──────────────────────────────────────────────────────────
    //  Override paintComponent – vẽ nền gradient
    // ──────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Nền gradient dọc
        GradientPaint gp = new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOT);
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Vẽ các vòng tròn trang trí
        g2.setColor(new Color(0xFF6B35, true).brighter());
        drawGlowCircle(g2, 80, 80, 120, new Color(0xFF, 0x6B, 0x35, 0x18));
        drawGlowCircle(g2, getWidth() - 80, getHeight() - 80, 160, new Color(0xFF, 0xD7, 0x00, 0x10));

        g2.dispose();
        super.paintComponent(g);
    }

    private void drawGlowCircle(Graphics2D g2, int cx, int cy, int r, Color color) {
        g2.setColor(color);
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    // ──────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────

    private JPanel buildFieldGroup(String labelText, JTextField field) {
        JPanel group = new JPanel(new BorderLayout(0, 6));
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
                // Border
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
        field.setFont(loadFont(14f));
        field.setBorder(new EmptyBorder(10, 14, 10, 14));
        field.setPreferredSize(new Dimension(300, 44));
        field.setToolTipText(placeholder);

        // Repaint on focus change to update border color
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { field.repaint(); }
            @Override public void focusLost(FocusEvent e)   { field.repaint(); }
        });
        return field;
    }

    private void attemptConnect() {
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();

        if (name.isEmpty()) { showStatus("⚠ Vui lòng nhập tên người chơi!", ACCENT); return; }
        if (host.isEmpty()) { showStatus("⚠ Vui lòng nhập địa chỉ Server!", ACCENT); return; }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            showStatus("⚠ Cổng không hợp lệ! (Ví dụ: 9876)", ACCENT);
            return;
        }

        connectBtn.setEnabled(false);
        showStatus("🔄 Đang kết nối...", TEXT_SEC);

        if (callback != null) {
            callback.onConnect(name, host, port);
        }
    }

    public void showStatus(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setForeground(color);
        });
    }

    public void setConnectEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> connectBtn.setEnabled(enabled));
    }

    // Load font (fallback về SansSerif nếu không có file riêng)
    private Font loadFont(float size) {
        return new Font("Segoe UI", Font.PLAIN, (int) size);
    }
}
