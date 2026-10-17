package com.bomberman.client;

import com.bomberman.common.GameConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Màn hình Phòng Chờ (Room Waiting Lobby).
 * Hiển thị mã phòng 4 ký tự, danh sách người chơi, nút Bắt đầu (cho Host) và Rời phòng.
 */
public class RoomPanel extends JPanel {

    public interface RoomActionCallback {
        void onStartGame();
        void onLeaveRoom();
    }

    private final ClientGameState state;
    private final RoomActionCallback callback;

    private final JLabel roomCodeLabel;
    private final JLabel hostBadgeLabel;
    private final JLabel statusLabel;
    private final JButton startBtn;
    private final JButton leaveBtn;
    private final JButton copyCodeBtn;
    private final JPanel playerListPanel;

    private final Timer refreshTimer;

    // ── Màu sắc chuẩn Bomb It theme ───────────────────────────
    private static final Color BG_TOP       = new Color(0x0d1b2a);
    private static final Color BG_BOT       = new Color(0x1b2838);
    private static final Color CARD_BG      = new Color(0x1e2d3d);
    private static final Color SLOT_BG      = new Color(0x14202e);
    private static final Color SLOT_EMPTY   = new Color(0x0f1824);
    private static final Color ACCENT_ORANGE= new Color(0xFF6B35);
    private static final Color ACCENT_GOLD  = new Color(0xFFD700);
    private static final Color ACCENT_GREEN = new Color(0x2ECC71);
    private static final Color ACCENT_RED   = new Color(0xE74C3C);
    private static final Color TEXT_PRI     = new Color(0xFFFFFF);
    private static final Color TEXT_SEC     = new Color(0xA8B8C8);
    private static final Color FIELD_BD     = new Color(0x2e4a62);

    public RoomPanel(ClientGameState state, RoomActionCallback callback) {
        this.state = state;
        this.callback = callback;

        setLayout(new GridBagLayout());
        setOpaque(false);

        // Card trung tâm
        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                // Viền trên nhấn màu cam
                g2.setColor(ACCENT_ORANGE);
                g2.fillRoundRect(0, 0, getWidth(), 5, 5, 5);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(0, 20));
        card.setBorder(new EmptyBorder(30, 36, 30, 36));
        card.setPreferredSize(new Dimension(540, 520));

        // ── TOP: Tiêu đề + Mã phòng ───────────────────────────
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        JLabel header = new JLabel("PHÒNG CHỜ TRẬN ĐẤU");
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setForeground(TEXT_SEC);

        JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        codeRow.setOpaque(false);

        JLabel codePrefix = new JLabel("MÃ PHÒNG:");
        codePrefix.setFont(new Font("Segoe UI", Font.BOLD, 16));
        codePrefix.setForeground(TEXT_PRI);

        roomCodeLabel = new JLabel("----");
        roomCodeLabel.setFont(new Font("Segoe UI", Font.BOLD, 34));
        roomCodeLabel.setForeground(ACCENT_GOLD);

        copyCodeBtn = createSmallButton("📋 Sao chép", new Color(0x27, 0x3b, 0x54));
        copyCodeBtn.addActionListener(e -> copyRoomCode());

        codeRow.add(codePrefix);
        codeRow.add(roomCodeLabel);
        codeRow.add(copyCodeBtn);

        hostBadgeLabel = new JLabel("👑 Bạn là Chủ Phòng (Room Host)");
        hostBadgeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hostBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        hostBadgeLabel.setForeground(ACCENT_GOLD);

        topPanel.add(header);
        topPanel.add(Box.createVerticalStrut(4));
        topPanel.add(codeRow);
        topPanel.add(hostBadgeLabel);

        card.add(topPanel, BorderLayout.NORTH);

        // ── CENTER: Danh sách 4 slot người chơi ────────────────
        playerListPanel = new JPanel(new GridLayout(4, 1, 0, 8));
        playerListPanel.setOpaque(false);
        card.add(playerListPanel, BorderLayout.CENTER);

        // ── BOTTOM: Nút hành động + trạng thái ────────────────
        JPanel botPanel = new JPanel();
        botPanel.setLayout(new BoxLayout(botPanel, BoxLayout.Y_AXIS));
        botPanel.setOpaque(false);

        statusLabel = new JLabel("Đang chờ người chơi tham gia... (Cần tối thiểu 2 người)", SwingConstants.CENTER);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        statusLabel.setForeground(TEXT_SEC);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 10));
        btnRow.setOpaque(false);

        leaveBtn = createStyledButton("← RỜI PHÒNG", ACCENT_RED, 140, 42);
        leaveBtn.addActionListener(e -> {
            if (callback != null) callback.onLeaveRoom();
        });

        startBtn = createStyledButton("▶ BẮT ĐẦU NGAY", ACCENT_GREEN, 180, 42);
        startBtn.addActionListener(e -> {
            if (callback != null) callback.onStartGame();
        });

        btnRow.add(leaveBtn);
        btnRow.add(startBtn);

        botPanel.add(statusLabel);
        botPanel.add(Box.createVerticalStrut(8));
        botPanel.add(btnRow);

        card.add(botPanel, BorderLayout.SOUTH);

        add(card);

        // Timer cập nhật giao diện (100ms)
        refreshTimer = new Timer(100, e -> updateUIFromState());
    }

    public void start() {
        refreshTimer.start();
        updateUIFromState();
    }

    public void stop() {
        refreshTimer.stop();
    }

    private void updateUIFromState() {
        // Cập nhật mã phòng
        String code = state.myRoomCode;
        roomCodeLabel.setText(code.isEmpty() ? "----" : code);

        boolean isHost = state.imHost;
        hostBadgeLabel.setVisible(isHost);

        // Cập nhật danh sách người chơi
        List<String> names = state.roomPlayerNames;
        playerListPanel.removeAll();

        int count = names.size();
        for (int i = 0; i < GameConstants.MAX_PLAYERS; i++) {
            if (i < count) {
                String rawName = names.get(i);
                boolean slotIsHost = rawName.contains("[HOST]") || (i == 0 && isHost);
                String displayName = rawName.replace("[HOST]", "").trim();
                boolean isMe = (state.myPlayerId > 0 && displayName.equals(state.myPlayerName)) ||
                               (count == 1 && isHost);

                playerListPanel.add(buildPlayerSlot(i + 1, displayName, slotIsHost, isMe, true));
            } else {
                playerListPanel.add(buildPlayerSlot(i + 1, "Đang chờ người chơi...", false, false, false));
            }
        }

        // Cập nhật nút Start
        if (isHost) {
            startBtn.setVisible(true);
            if (count >= GameConstants.MIN_PLAYERS_TO_START) {
                startBtn.setEnabled(true);
                startBtn.setText("▶ BẮT ĐẦU NGAY (" + count + "/" + GameConstants.MAX_PLAYERS + ")");
                statusLabel.setText("✅ Đã đủ điều kiện! Bấm Bắt Đầu để vào trận.");
                statusLabel.setForeground(ACCENT_GREEN);
            } else {
                startBtn.setEnabled(false);
                startBtn.setText("▶ BẮT ĐẦU NGAY");
                statusLabel.setText("⏳ Đang chờ thêm người chơi... (Cần ít nhất " + GameConstants.MIN_PLAYERS_TO_START + " người)");
                statusLabel.setForeground(TEXT_SEC);
            }
        } else {
            startBtn.setVisible(false);
            statusLabel.setText("⏳ Đang chờ Chủ phòng bắt đầu trận đấu...");
            statusLabel.setForeground(ACCENT_GOLD);
        }

        playerListPanel.revalidate();
        playerListPanel.repaint();
    }

    private JPanel buildPlayerSlot(int slotNum, String name, boolean isHost, boolean isMe, boolean occupied) {
        JPanel slot = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(occupied ? SLOT_BG : SLOT_EMPTY);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                if (isMe) {
                    g2.setColor(ACCENT_ORANGE);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));
                } else {
                    g2.setColor(FIELD_BD);
                    g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));
                }
                g2.dispose();
            }
        };
        slot.setOpaque(false);
        slot.setBorder(new EmptyBorder(8, 16, 8, 16));
        slot.setPreferredSize(new Dimension(460, 48));

        // Bên trái: Avatar + Số thứ tự
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        left.setOpaque(false);

        JLabel numLbl = new JLabel("P" + slotNum);
        numLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        numLbl.setForeground(occupied ? ACCENT_GOLD : new Color(0x55, 0x66, 0x77));

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(new Font("Segoe UI", occupied ? Font.BOLD : Font.ITALIC, 14));
        nameLbl.setForeground(occupied ? TEXT_PRI : new Color(0x66, 0x77, 0x88));

        left.add(numLbl);
        left.add(nameLbl);
        slot.add(left, BorderLayout.WEST);

        // Bên phải: Huy hiệu Host / YOU / Ready
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.setOpaque(false);

        if (isHost) {
            JLabel hostBadge = new JLabel("👑 HOST");
            hostBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
            hostBadge.setForeground(ACCENT_GOLD);
            right.add(hostBadge);
        }
        if (isMe) {
            JLabel meBadge = new JLabel("YOU");
            meBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
            meBadge.setForeground(ACCENT_ORANGE);
            right.add(meBadge);
        }
        if (occupied) {
            JLabel readyLbl = new JLabel("✔ SẴN SÀNG");
            readyLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            readyLbl.setForeground(ACCENT_GREEN);
            right.add(readyLbl);
        }

        slot.add(right, BorderLayout.EAST);
        return slot;
    }

    private void copyRoomCode() {
        String code = state.myRoomCode;
        if (!code.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
            copyCodeBtn.setText("✔ Đã chép!");
            Timer t = new Timer(1500, e -> copyCodeBtn.setText("📋 Sao chép"));
            t.setRepeats(false);
            t.start();
        }
    }

    private JButton createStyledButton(String text, Color baseColor, int w, int h) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = isEnabled() ? (getModel().isRollover() ? baseColor.brighter() : baseColor)
                                      : new Color(0x3a, 0x48, 0x56);
                g2.setColor(c);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setPreferredSize(new Dimension(w, h));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createSmallButton(String text, Color baseColor) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? baseColor.brighter() : baseColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setForeground(TEXT_SEC);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setPreferredSize(new Dimension(95, 28));
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

        // Vòng sáng trang trí
        g2.setColor(new Color(0xFF, 0x6B, 0x35, 0x14));
        g2.fillOval(40, 40, 140, 140);
        g2.setColor(new Color(0xFF, 0xD7, 0x00, 0x10));
        g2.fillOval(getWidth() - 120, getHeight() - 120, 160, 160);

        g2.dispose();
        super.paintComponent(g);
    }
}
