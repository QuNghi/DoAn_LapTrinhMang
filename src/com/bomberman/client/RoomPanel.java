package com.bomberman.client;

import com.bomberman.common.GameConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Màn hình Phòng Chờ (Room Waiting Lobby).
 * Hiển thị mã phòng 4 ký tự, danh sách người chơi, nút Bắt đầu (cho Host) và Rời phòng.
 * Thành viên có nút chuyển đổi trạng thái Sẵn Sàng / Chưa Sẵn Sàng.
 */
public class RoomPanel extends JPanel {

    public interface RoomActionCallback {
        void onStartGame();
        void onLeaveRoom();
        void onKickPlayer(int playerId);
        void onSelectMap(int mapType);
        void onReadyToggle();
    }

    private final ClientGameState state;
    private final RoomActionCallback callback;

    private final JLabel roomCodeLabel;
    private final JLabel hostBadgeLabel;
    private final JLabel statusLabel;
    private final JComboBox<String> mapSelector;
    private final JButton startBtn;
    private final JButton leaveBtn;
    private final JButton copyCodeBtn;
    private final JPanel playerListPanel;

    private final Timer refreshTimer;
    private boolean updatingMapCombo = false;

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
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(20, 36, 20, 36));
        card.setPreferredSize(new Dimension(560, 580));

        // ── TOP: Tiêu đề + Mã phòng + Chọn Bản Đồ ──────────────
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        JLabel header = new JLabel("PHONG CHO TRAN DAU");
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setForeground(TEXT_SEC);

        JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        codeRow.setOpaque(false);

        JLabel codePrefix = new JLabel("MA PHONG:");
        codePrefix.setFont(new Font("Segoe UI", Font.BOLD, 16));
        codePrefix.setForeground(TEXT_PRI);

        roomCodeLabel = new JLabel("----");
        roomCodeLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        roomCodeLabel.setForeground(ACCENT_GOLD);

        copyCodeBtn = createSmallButton("[COPY] Sao chep", new Color(0x27, 0x3b, 0x54));
        copyCodeBtn.addActionListener(e -> copyRoomCode());

        codeRow.add(codePrefix);
        codeRow.add(roomCodeLabel);
        codeRow.add(copyCodeBtn);

        hostBadgeLabel = new JLabel("[HOST] Ban la Chu Phong (Room Host)");
        hostBadgeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hostBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        hostBadgeLabel.setForeground(ACCENT_GOLD);

        // Hàng chọn bản đồ (Host chọn hoặc hiển thị cho thành viên)
        JPanel mapRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        mapRow.setOpaque(false);
        JLabel mapLbl = new JLabel("[MAP] Ban Do:");
        mapLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        mapLbl.setForeground(TEXT_SEC);

        mapSelector = new JComboBox<>(new String[] {
            "[RND] Ngau Nhien (Random)",
            "[1] Co Dien (Classic)",
            "[2] Mo Rong (Open Battle)",
            "[3] Me Cung (Labyrinth)",
            "[4] Giao Tranh (Crossfire)"
        });
        mapSelector.setFont(new Font("Segoe UI", Font.BOLD, 12));
        mapSelector.setBackground(SLOT_BG);
        mapSelector.setForeground(Color.WHITE);
        mapSelector.setFocusable(false);
        mapSelector.setPreferredSize(new Dimension(220, 28));

        // Custom renderer để đảm bảo text hiển thị đúng màu bất kể L&F
        mapSelector.setRenderer(new BasicComboBoxRenderer() {
            @Override
            @SuppressWarnings("rawtypes")
            public Component getListCellRendererComponent(JList list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? new Color(0x2a4a6a) : SLOT_BG);
                setForeground(Color.WHITE);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return this;
            }
        });

        mapSelector.addActionListener(e -> {
            if (!updatingMapCombo && this.state.imHost && callback != null) {
                callback.onSelectMap(mapSelector.getSelectedIndex());
            }
        });

        mapRow.add(mapLbl);
        mapRow.add(mapSelector);

        topPanel.add(header);
        topPanel.add(Box.createVerticalStrut(2));
        topPanel.add(codeRow);
        topPanel.add(hostBadgeLabel);
        topPanel.add(Box.createVerticalStrut(4));
        topPanel.add(mapRow);

        card.add(topPanel, BorderLayout.NORTH);

        // ── CENTER: Danh sách 4 slot người chơi ────────────────
        playerListPanel = new JPanel(new GridLayout(4, 1, 0, 8));
        playerListPanel.setOpaque(false);
        card.add(playerListPanel, BorderLayout.CENTER);

        // ── BOTTOM: Nút hành động + trạng thái ────────────────
        JPanel botPanel = new JPanel();
        botPanel.setLayout(new BoxLayout(botPanel, BoxLayout.Y_AXIS));
        botPanel.setOpaque(false);

        statusLabel = new JLabel("Dang cho nguoi choi tham gia... (Can toi thieu 2 nguoi)", SwingConstants.CENTER);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        statusLabel.setForeground(TEXT_SEC);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        btnRow.setOpaque(false);

        leaveBtn = createStyledButton("<< ROI PHONG", ACCENT_RED, 130, 40);
        leaveBtn.addActionListener(e -> {
            if (callback != null) callback.onLeaveRoom();
        });

        startBtn = createStyledButton("[>] BAT DAU NGAY", ACCENT_GREEN, 180, 40);
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

        // Cập nhật bộ chọn bản đồ
        updatingMapCombo = true;
        int mType = state.selectedMapType;
        if (mType >= 0 && mType < mapSelector.getItemCount() && mapSelector.getSelectedIndex() != mType) {
            mapSelector.setSelectedIndex(mType);
        }
        mapSelector.setEnabled(isHost);
        mapSelector.setToolTipText(isHost ? "Chon ban do cho tran dau" : "Chi Chu phong moi co quyen chon ban do");
        updatingMapCombo = false;

        // Cập nhật danh sách người chơi
        playerListPanel.removeAll();
        List<ClientGameState.RoomSlot> slots = state.roomSlots;
        int count = slots.size();

        if (count > 0) {
            for (int i = 0; i < GameConstants.MAX_PLAYERS; i++) {
                if (i < count) {
                    ClientGameState.RoomSlot s = slots.get(i);
                    boolean isMe = (state.myPlayerId > 0 && s.id == state.myPlayerId) ||
                                   (count == 1 && isHost);
                    playerListPanel.add(buildPlayerSlot(i + 1, s.id, s.name, s.isHost, isMe, true, s.isReady));
                } else {
                    playerListPanel.add(buildPlayerSlot(i + 1, -1, "Dang cho nguoi choi...", false, false, false, false));
                }
            }
        } else {
            // Fallback nếu chưa nhận được RoomSlot
            List<String> names = state.roomPlayerNames;
            int n = names.size();
            for (int i = 0; i < GameConstants.MAX_PLAYERS; i++) {
                if (i < n) {
                    String rawName = names.get(i);
                    boolean slotIsHost = rawName.contains("[HOST]") || (i == 0 && isHost);
                    String displayName = rawName.replace("[HOST]", "").trim();
                    boolean isMe = (state.myPlayerId > 0 && displayName.equals(state.myPlayerName)) ||
                                   (n == 1 && isHost);
                    playerListPanel.add(buildPlayerSlot(i + 1, -1, displayName, slotIsHost, isMe, true, slotIsHost));
                } else {
                    playerListPanel.add(buildPlayerSlot(i + 1, -1, "Dang cho nguoi choi...", false, false, false, false));
                }
            }
        }

        int currentPlayers = Math.max(count, state.roomPlayerNames.size());

        // Đếm số người sẵn sàng (host luôn được tính là ready)
        long readyCount = slots.stream().filter(s -> s.isHost || s.isReady).count();
        if (count == 0) readyCount = (isHost && currentPlayers > 0) ? 1 : 0;

        // Cập nhật nút Start
        if (isHost) {
            startBtn.setVisible(true);
            boolean canStart = currentPlayers >= GameConstants.MIN_PLAYERS_TO_START
                    && readyCount >= GameConstants.MIN_PLAYERS_TO_START;
            if (canStart) {
                startBtn.setEnabled(true);
                startBtn.setText("[>] BAT DAU (" + currentPlayers + "/" + GameConstants.MAX_PLAYERS + ")");
                statusLabel.setText("[OK] Du dieu kien! Bam Bat Dau de vao tran.");
                statusLabel.setForeground(ACCENT_GREEN);
            } else if (currentPlayers < GameConstants.MIN_PLAYERS_TO_START) {
                startBtn.setEnabled(false);
                startBtn.setText("[>] BAT DAU NGAY");
                statusLabel.setText("[...] Can it nhat " + GameConstants.MIN_PLAYERS_TO_START + " nguoi choi!");
                statusLabel.setForeground(TEXT_SEC);
            } else {
                startBtn.setEnabled(false);
                startBtn.setText("[>] BAT DAU NGAY");
                statusLabel.setText("[...] Cho thanh vien san sang... (" + readyCount + "/" + currentPlayers + ")");
                statusLabel.setForeground(ACCENT_GOLD);
            }
        } else {
            startBtn.setVisible(false);
            statusLabel.setText("[...] Dang cho Chu phong bat dau tran dau...");
            statusLabel.setForeground(ACCENT_GOLD);
        }

        // Cập nhật toàn bộ panel
        revalidate();
        repaint();
    }

    private JPanel buildPlayerSlot(int slotNum, int playerId, String name, boolean isHost,
                                    boolean isMe, boolean occupied, boolean isReady) {
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

        // Bên phải: Huy hiệu Host / YOU / Ready / Nút Kick
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.setOpaque(false);

        if (isHost) {
            JLabel hostBadge = new JLabel("[HOST]");
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

        if (occupied && !isHost) {
            if (isMe) {
                // Nút toggle sẵn sàng (chỉ hiện cho thành viên - không phải host)
                String readyText = isReady ? "[v] SAN SANG" : "[x] CHUA SAN SANG";
                Color readyColor = isReady ? ACCENT_GREEN : ACCENT_RED;
                JButton readyBtn = createSmallButton(readyText, readyColor);
                readyBtn.setPreferredSize(new Dimension(130, 26));
                readyBtn.addActionListener(e -> {
                    if (callback != null) callback.onReadyToggle();
                });
                right.add(readyBtn);
            } else {
                // Hiện trạng thái ready của thành viên khác
                JLabel readyLbl = new JLabel(isReady ? "[v] SAN SANG" : "... CHO");
                readyLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                readyLbl.setForeground(isReady ? ACCENT_GREEN : TEXT_SEC);
                right.add(readyLbl);
            }
        }

        // Quyền Kick cho Chủ Phòng (không kick chính mình)
        if (occupied && !isMe && state.imHost && playerId > 0) {
            JButton kickBtn = createSmallButton("[x] Kick", new Color(0xC0, 0x39, 0x2B));
            kickBtn.setPreferredSize(new Dimension(65, 26));
            kickBtn.setToolTipText("Kick " + name + " khoi phong");
            kickBtn.addActionListener(e -> {
                int opt = JOptionPane.showConfirmDialog(this,
                        "Ban co chac muon kick nguoi choi [" + name + "] khoi phong?",
                        "Xac nhan Kick", JOptionPane.YES_NO_OPTION);
                if (opt == JOptionPane.YES_OPTION && callback != null) {
                    callback.onKickPlayer(playerId);
                }
            });
            right.add(kickBtn);
        }

        slot.add(right, BorderLayout.EAST);
        return slot;
    }

    private void copyRoomCode() {
        String code = state.myRoomCode;
        if (!code.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
            copyCodeBtn.setText("[v] Da chep!");
            Timer t = new Timer(1500, e -> copyCodeBtn.setText("[COPY] Sao chep"));
            t.setRepeats(false);
            t.start();
        }
    }

    private JButton createStyledButton(String text, Color baseColor, int w, int h) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Màu nền khi disabled sáng hơn để text trắng vẫn đọc được trên CARD_BG tối
                Color c = isEnabled() ? (getModel().isRollover() ? baseColor.brighter() : baseColor)
                                      : new Color(0x55, 0x6a, 0x7a);
                g2.setColor(c);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                // Viền mỏng để phân biệt với nền khi disabled
                if (!isEnabled()) {
                    g2.setColor(new Color(0x7a, 0x9a, 0xb2));
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12));
                }
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
        btn.setForeground(Color.WHITE);
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
