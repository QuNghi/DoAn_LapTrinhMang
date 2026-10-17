package com.bomberman.client;

import com.bomberman.common.GameConstants;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Canvas vẽ toàn bộ game: bản đồ, nhân vật, bom, vật phẩm, hiệu ứng lửa.
 * Thiết kế tham khảo Bomb It / Classic Bomberman.
 * Vẽ bằng active rendering (Swing Timer 60 FPS).
 */
public class GamePanel extends JPanel implements KeyListener {

    // ── Màu sắc bản đồ (Bomb It style) ───────────────────────
    private static final Color COL_BG         = new Color(0x1a1a2e);
    private static final Color COL_GRASS      = new Color(0x2d5a27);
    private static final Color COL_GRASS_ALT  = new Color(0x3a6b31);
    private static final Color COL_HARD_WALL  = new Color(0x5a5a7a);
    private static final Color COL_HARD_WALL2 = new Color(0x4a4a6a);
    private static final Color COL_SOFT_BRICK = new Color(0xc0522a);
    private static final Color COL_SOFT_BRIC2 = new Color(0xd4623a);
    private static final Color COL_BOMB_BODY  = new Color(0x1a1a1a);
    private static final Color COL_BOMB_FUSE  = new Color(0xFF6600);
    private static final Color COL_FLAME_C    = new Color(0xFFFFAA);
    private static final Color COL_FLAME_M    = new Color(0xFF8800);
    private static final Color COL_FLAME_E    = new Color(0xFF2200);
    private static final Color COL_HUD_BG     = new Color(0x0d, 0x1b, 0x2a, 230);
    private static final Color COL_ACCENT     = new Color(0xFF6B35);

    // Màu nhân vật (4 players) tham khảo Bomb It
    private static final Color[] PLAYER_COLORS = {
        new Color(0x4FC3F7), // Player 1 – Xanh dương
        new Color(0xEF5350), // Player 2 – Đỏ
        new Color(0x66BB6A), // Player 3 – Xanh lá
        new Color(0xFFA726), // Player 4 – Cam
    };
    private static final Color[] PLAYER_DARK = {
        new Color(0x0277BD),
        new Color(0xB71C1C),
        new Color(0x1B5E20),
        new Color(0xE65100),
    };

    // ── Kích thước ô lưới & Sidebar bên trái ─────────────────
    private static final int TILE_SIZE     = 48;
    private static final int SIDEBAR_WIDTH = 230;

    // ── Tham chiếu ────────────────────────────────────────────
    private final ClientGameState state;
    private final GameClient client;
    private final GameApp app;

    // ── Timer render 60 FPS ───────────────────────────────────
    private final Timer renderTimer;

    // ── Trạng thái phím ───────────────────────────────────────
    private final Set<Integer> pressedKeys = new HashSet<>();
    private boolean lastMoveSent = false;
    private String lastDirection = "";

    // ── Animation ─────────────────────────────────────────────
    private long frameCount = 0;
    private int legFrame = 0; // 0..7 để animate chân nhân vật

    // ── Restart Vote UI ───────────────────────────────────────
    private final java.util.List<long[]> restartClickAreas = new java.util.ArrayList<>();
    private boolean restartVoteYesSent = false;
    private boolean restartVoteNoSent  = false;

    public GamePanel(ClientGameState state, GameClient client, GameApp app) {
        this.state = state;
        this.client = client;
        this.app = app;

        setFocusable(true);
        addKeyListener(this);
        setBackground(COL_BG);

        int panelW = GameConstants.MAP_WIDTH * TILE_SIZE + SIDEBAR_WIDTH;
        int panelH = GameConstants.MAP_HEIGHT * TILE_SIZE;
        setPreferredSize(new Dimension(panelW, panelH));

        // Render loop 60 FPS
        renderTimer = new javax.swing.Timer(16, e -> {
            frameCount++;
            legFrame = (int)((frameCount / 4) % 8); // ~7.5 Hz leg cycle
            state.cleanupExpiredExplosions();
            processKeyInput();
            repaint();
        });
    }

    public void startRendering() {
        renderTimer.start();
        requestFocusInWindow();

        // Mouse listener cho các nút click trên overlay game over (Thoát phòng / Chơi tiếp)
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!state.gameOver) return;
                for (long[] area : restartClickAreas) {
                    int ax = (int)area[0], ay = (int)area[1], aw = (int)area[2], ah = (int)area[3], type = (int)area[4];
                    if (e.getX() >= ax && e.getX() <= ax+aw && e.getY() >= ay && e.getY() <= ay+ah) {
                        if (type == 1) { // Chơi tiếp -> quay về phòng chờ
                            if (client != null) client.sendPlayAgain();
                        } else if (type == 2) { // Thoát phòng -> ra sảnh chính
                            if (client != null) client.sendLeaveRoom();
                            app.showLobby();
                        }
                    }
                }
            }
        });
    }

    public void stopRendering() {
        renderTimer.stop();
        // Reset vote flags khi rời game
        restartVoteYesSent = false;
        restartVoteNoSent = false;
    }

    // ──────────────────────────────────────────────────────────
    //  Xử lý phím (gửi MOVE liên tục khi giữ phím)
    // ──────────────────────────────────────────────────────────

    private void processKeyInput() {
        if (!state.connected || state.gameOver) return;
        // Khóa di chuyển khi đang đếm ngược 3 giây
        if (state.countdownSeconds > 0) return;

        String dir = null;
        if (pressedKeys.contains(KeyEvent.VK_W) || pressedKeys.contains(KeyEvent.VK_UP))    dir = "UP";
        if (pressedKeys.contains(KeyEvent.VK_S) || pressedKeys.contains(KeyEvent.VK_DOWN))  dir = "DOWN";
        if (pressedKeys.contains(KeyEvent.VK_A) || pressedKeys.contains(KeyEvent.VK_LEFT))  dir = "LEFT";
        if (pressedKeys.contains(KeyEvent.VK_D) || pressedKeys.contains(KeyEvent.VK_RIGHT)) dir = "RIGHT";

        if (dir != null) {
            if (!dir.equals(lastDirection)) {
                client.sendMove(dir);
                lastDirection = dir;
            } else if (frameCount % 2 == 0) { // gửi mỗi ~33ms
                client.sendMove(dir);
            }
            lastMoveSent = true;
        } else if (lastMoveSent) {
            client.sendStop();
            lastDirection = "";
            lastMoveSent = false;
        }
    }

    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            app.showLobby();
            return;
        }

        // Khóa phím khi đang đếm ngược 3 giây
        if (state.countdownSeconds > 0) return;

        pressedKeys.add(e.getKeyCode());
        System.out.println("[CLIENT-KEY] Nhấn: " + KeyEvent.getKeyText(e.getKeyCode()) + " (code=" + e.getKeyCode() + ")");

        // SPACE: gom cả bom và súng – server tự quyết dùng loại nào
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (client != null) client.sendAction();
        }
    }

    @Override public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
        System.out.println("[CLIENT-KEY] Thả: " + KeyEvent.getKeyText(e.getKeyCode()));
    }

    @Override public void keyTyped(KeyEvent e) {}

    // ──────────────────────────────────────────────────────────
    //  Vẽ
    // ──────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,   RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_QUALITY);

        // ── 1. Vẽ Sidebar bên trái (Thông tin cá nhân các người chơi) ──
        drawSidebar(g2);

        // ── 2. Dịch sang bên phải để vẽ bản đồ game ──
        g2.translate(SIDEBAR_WIDTH, 0);

        // ── Nền cỏ + bản đồ ──
        drawMap(g2);

        // ── Items (vật phẩm buff) ──
        drawItems(g2);

        // ── Bom ──
        drawBombs(g2);

        // ── Lửa nổ ──
        drawExplosions(g2);

        // ── Nhân vật ──
        drawPlayers(g2);

        // ── Màn hình đếm ngược 3 giây ──
        drawCountdownOverlay(g2);

        // ── Animation viên đạn ──
        drawBullets(g2);

        // ── 3. Trả lại tọa độ gốc để vẽ các overlay phủ toàn bộ cửa sổ ──
        g2.translate(-SIDEBAR_WIDTH, 0);

        // Victory/Defeat flash 3 giây (hiện trước bảng kết quả)
        if (state.showVictoryScreen) {
            long elapsed = System.currentTimeMillis() - state.victoryScreenStartMs;
            if (elapsed >= ClientGameState.VICTORY_SCREEN_DURATION_MS) {
                // Hết 3 giây → ẩn victory screen, hiện bảng kết quả
                state.showVictoryScreen = false;
                state.gameOver = true;
            } else {
                drawVictoryDefeatOverlay(g2);
            }
        } else if (state.gameOver) {
            drawGameOverOverlay(g2);
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Sidebar UI bên trái: Thông tin cá nhân của các người chơi
    // ──────────────────────────────────────────────────────────

    private void drawSidebar(Graphics2D g2) {
        // Nền sidebar bên trái
        g2.setColor(new Color(0x0c, 0x15, 0x24));
        g2.fillRect(0, 0, SIDEBAR_WIDTH, getHeight());

        // Viền ngăn cách giữa sidebar và map
        g2.setColor(new Color(0x1e, 0x2d, 0x42));
        g2.fillRect(SIDEBAR_WIDTH - 2, 0, 2, getHeight());

        // Header Sidebar
        g2.setColor(new Color(0x14, 0x22, 0x36));
        g2.fillRect(0, 0, SIDEBAR_WIDTH, 44);
        g2.setColor(COL_ACCENT);
        g2.fillRect(0, 42, SIDEBAR_WIDTH, 2);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        g2.drawString("⚔️ NGƯỜI CHƠI", 14, 27);

        // Subtitle trạng thái
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.setColor(new Color(0x78, 0x90, 0xA8));
        String statusText = state.gameOver ? "KẾT THÚC" : (state.countdownSeconds > 0 ? "CHUẨN BỊ..." : "TRẬN ĐẤU");
        g2.drawString(statusText, SIDEBAR_WIDTH - g2.getFontMetrics().stringWidth(statusText) - 12, 27);

        // Danh sách người chơi (tối đa 4 người)
        int startY = 52;
        int cardH = 132;
        int gap = 8;

        List<ClientGameState.PlayerData> sortedPlayers = new ArrayList<>();
        for (ClientGameState.PlayerData p : state.players.values()) {
            // Không vẽ các thẻ player rác/đã rời trận hoàn toàn
            if (p.name != null && p.name.contains("(Rời)")) {
                continue;
            }
            sortedPlayers.add(p);
        }
        sortedPlayers.sort(Comparator.comparingInt(p -> p.id));

        for (int i = 0; i < sortedPlayers.size() && i < 4; i++) {
            ClientGameState.PlayerData pd = sortedPlayers.get(i);
            int cy = startY + i * (cardH + gap);
            drawPlayerCard(g2, 10, cy, SIDEBAR_WIDTH - 20, cardH, pd);
        }
    }

    private void drawPlayerCard(Graphics2D g2, int x, int y, int w, int h, ClientGameState.PlayerData pd) {
        boolean isMe = (pd.id == state.myPlayerId);
        Color pc = getPlayerColor(pd.id);
        Color pcDark = getPlayerDark(pd.id);

        // Nền thẻ người chơi
        g2.setColor(isMe ? new Color(0x16, 0x28, 0x40) : new Color(0x11, 0x1d, 0x2e));
        g2.fillRoundRect(x, y, w, h, 12, 12);

        // Viền thẻ
        g2.setColor(isMe ? new Color(0xFF, 0xAA, 0x00, 200) : new Color(0x23, 0x34, 0x4a));
        g2.setStroke(new BasicStroke(isMe ? 2f : 1f));
        g2.drawRoundRect(x, y, w, h, 12, 12);

        // 1. Hình ảnh avatar nhân vật
        int avSz = 34;
        int avX = x + 10;
        int avY = y + 10;
        g2.setColor(pd.alive ? pc : new Color(0x3a, 0x3a, 0x3a));
        g2.fillRoundRect(avX, avY, avSz, avSz, 8, 8);
        g2.setColor(pd.alive ? pcDark : new Color(0x22, 0x22, 0x22));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(avX, avY, avSz, avSz, 8, 8);

        // Mắt avatar
        g2.setColor(Color.WHITE);
        g2.fillOval(avX + 8, avY + 11, 5, 5);
        g2.fillOval(avX + 21, avY + 11, 5, 5);
        g2.setColor(Color.BLACK);
        g2.fillOval(avX + 10, avY + 13, 2, 2);
        g2.fillOval(avX + 23, avY + 13, 2, 2);

        // 2. Tên nhân vật
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        String displayName = pd.name.isEmpty() ? "P" + pd.id : pd.name;
        if (displayName.length() > 9) displayName = displayName.substring(0, 8) + "…";
        g2.setColor(pd.alive ? (isMe ? ACCENT_YELLOW : Color.WHITE) : new Color(0x77, 0x77, 0x77));
        g2.drawString(displayName, x + 50, y + 24);

        if (isMe) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(new Color(0x00, 0xE5, 0xFF));
            g2.drawString("• BẠN", x + 50, y + 37);
        }

        // Trạng thái sống / KO / khiên
        if (!pd.alive) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(new Color(0xFF, 0x44, 0x44));
            g2.drawString("☠️ BỊ LOẠI", x + w - 66, y + 24);
        } else if (pd.hasShield) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(new Color(0xFF, 0xD7, 0x00));
            g2.drawString("🛡️", x + w - 30, y + 24);
        }

        // Đường gạch ngăn cách nhỏ
        g2.setColor(new Color(0x1e, 0x2b, 0x3d));
        g2.drawLine(x + 10, y + 48, x + w - 10, y + 48);

        // 3. Số mạng hạ gục
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(new Color(0xFF, 0x66, 0x44));
        g2.drawString("💀 Hạ gục: " + pd.kills + " mạng", x + 10, y + 66);

        // 4. Các vật phẩm đã nhận được
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(new Color(0x88, 0x99, 0xaa));
        g2.drawString("Vật phẩm đã nhặt:", x + 10, y + 83);

        // Hàng icon vật phẩm: Bom, Lửa, Tốc độ, Khiên, Súng
        int itX = x + 8;
        int itY = y + 92;
        drawItemBadge(g2, "💣", pd.pickedBombCount, itX, itY, new Color(0x1e, 0x2b, 0x3d));
        drawItemBadge(g2, "🔥", pd.pickedBombRange, itX + 38, itY, new Color(0x38, 0x20, 0x12));
        drawItemBadge(g2, "⚡", pd.pickedSpeed, itX + 76, itY, new Color(0x38, 0x2c, 0x10));
        drawItemBadge(g2, "🛡️", pd.pickedShield, itX + 114, itY, new Color(0x12, 0x2e, 0x2e));
        drawItemBadge(g2, "🔫", pd.pickedWeapon, itX + 152, itY, new Color(0x28, 0x12, 0x30));
    }

    private void drawItemBadge(Graphics2D g2, String icon, int count, int x, int y, Color bg) {
        g2.setColor(bg);
        g2.fillRoundRect(x, y, 35, 24, 6, 6);
        g2.setColor(count > 0 ? Color.WHITE : new Color(0x55, 0x66, 0x77));
        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 10));
        g2.drawString(icon, x + 3, y + 16);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.drawString(String.valueOf(count), x + 19, y + 16);
    }

    // ──────────────────────────────────────────────────────────
    //  Bản đồ
    // ──────────────────────────────────────────────────────────

    private void drawMap(Graphics2D g2) {
        int[][] grid = state.mapGrid;
        int rows = state.mapHeight;
        int cols = state.mapWidth;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int px = col * TILE_SIZE;
                int py = row * TILE_SIZE;
                int tile = (grid != null && row < grid.length && col < grid[row].length)
                        ? grid[row][col] : 0;

                switch (tile) {
                    case 1: // HARD_WALL
                        drawHardWall(g2, px, py);
                        break;
                    case 2: // SOFT_BRICK
                        drawGrass(g2, px, py, row, col);
                        drawSoftBrick(g2, px, py);
                        break;
                    default: // EMPTY
                        drawGrass(g2, px, py, row, col);
                        break;
                }
            }
        }
    }

    private void drawGrass(Graphics2D g2, int px, int py, int row, int col) {
        // Nền cỏ 2 tông xen kẽ
        Color c = ((row + col) % 2 == 0) ? COL_GRASS : COL_GRASS_ALT;
        g2.setColor(c);
        g2.fillRect(px, py, TILE_SIZE, TILE_SIZE);
        // Highlight nhẹ góc trên-trái tạo cảm giác ánh sáng
        g2.setColor(new Color(255, 255, 255, 14));
        g2.fillRect(px, py, TILE_SIZE, 2);
        g2.fillRect(px, py, 2, TILE_SIZE);
    }

    private void drawHardWall(Graphics2D g2, int px, int py) {
        // ── Khối đá 3D: gradient từ xám sáng → xám tối ──
        GradientPaint gp = new GradientPaint(
            px, py,    new Color(0x72728a),
            px + TILE_SIZE, py + TILE_SIZE, new Color(0x3a3a50));
        g2.setPaint(gp);
        g2.fillRect(px, py, TILE_SIZE, TILE_SIZE);

        // Viền ngoài tối
        g2.setColor(new Color(0x25253a));
        g2.fillRect(px, py + TILE_SIZE - 3, TILE_SIZE, 3); // đáy
        g2.fillRect(px + TILE_SIZE - 3, py, 3, TILE_SIZE); // phải

        // Highlight góc trên-trái (ánh sáng)
        g2.setColor(new Color(255, 255, 255, 70));
        g2.fillRect(px, py, TILE_SIZE, 3);
        g2.fillRect(px, py, 3, TILE_SIZE);

        // Vân đá: vài stroke ngang/dọc ngẫu nhiên theo row/col
        int seed = (px * 7 + py * 13) & 0xFF;
        g2.setColor(new Color(0, 0, 0, 28 + (seed & 0x1F)));
        g2.setStroke(new BasicStroke(1f));
        int v1 = py + 8  + (seed % 10);
        int v2 = py + 22 + (seed % 12);
        int v3 = py + 36 + (seed % 8);
        g2.drawLine(px + 4, v1, px + TILE_SIZE - 6, v1);
        g2.drawLine(px + 4, v2, px + TILE_SIZE - 8, v2);
        g2.drawLine(px + 4, v3, px + TILE_SIZE - 4, v3);
        int hx = px + 10 + (seed % 14);
        g2.drawLine(hx, py + 4, hx, py + TILE_SIZE - 6);

        // Viền mỏng
        g2.setColor(new Color(0x1a1a28));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(px, py, TILE_SIZE - 1, TILE_SIZE - 1);
    }

    private void drawSoftBrick(Graphics2D g2, int px, int py) {
        // ── Gạch xây thật: 3 hàng gạch với mortar gap ──
        final int MORTAR = 2;          // độ dày mortar (vữa)
        final int BRICK_H = 13;        // chiều cao mỗi viên gạch
        final int[] OFFSETS = {0, 6};  // offset ngang xen kẽ giữa hàng chẵn/lẻ
        final Color MORTAR_COL  = new Color(0x6b3318);
        final Color BRICK_A     = new Color(0xc0522a);
        final Color BRICK_B     = new Color(0xcf5e30);
        final Color BRICK_HIGH  = new Color(255, 255, 255, 55);
        final Color BRICK_DARK  = new Color(0, 0, 0, 55);

        // Nền mortar
        g2.setColor(MORTAR_COL);
        g2.fillRect(px + 1, py + 1, TILE_SIZE - 2, TILE_SIZE - 2);

        int innerW = TILE_SIZE - 2;
        int totalH = TILE_SIZE - 2;
        int row = 0;
        for (int y = py + 1; y < py + totalH; y += BRICK_H + MORTAR, row++) {
            int h = Math.min(BRICK_H, py + totalH - y);
            if (h <= 0) break;
            int offset = OFFSETS[row % 2];
            // Vẽ từng viên gạch trong hàng
            for (int x = px + 1 - offset; x < px + 1 + innerW; x += 20 + MORTAR) {
                int bx = Math.max(px + 1, x);
                int bw = Math.min(20, px + 1 + innerW - bx);
                if (bw <= 0) continue;
                // Màu gạch xen kẽ theo vị trí
                g2.setColor(((bx + row) % 2 == 0) ? BRICK_A : BRICK_B);
                g2.fillRect(bx, y, bw, h);
                // Highlight trên viên gạch
                g2.setColor(BRICK_HIGH);
                g2.fillRect(bx, y, bw, 2);
                // Shadow dưới viên gạch
                g2.setColor(BRICK_DARK);
                g2.fillRect(bx, y + h - 2, bw, 2);
            }
        }

        // Viền ngoài ô gạch
        g2.setColor(new Color(0x4a1a08));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(px + 1, py + 1, TILE_SIZE - 3, TILE_SIZE - 3);
    }

    // ──────────────────────────────────────────────────────────
    //  Items (vật phẩm buff)
    // ──────────────────────────────────────────────────────────

    private static final Color[] ITEM_COLORS = {
        new Color(0xFF6B35), // 1 = BOMB_RANGE   – Cam
        new Color(0x00E5FF), // 2 = SPEED_BOOST  – Cyan
        new Color(0x69F0AE), // 3 = BOMB_COUNT   – Xanh lá
        new Color(0xFFD700), // 4 = SHIELD        – Vàng
        new Color(0xFF4081), // 5 = WEAPON_GUN   – Hồng
    };
    private static final String[] ITEM_ICONS = {"💥", "💨", "💣", "🛡", "🔫"};

    private void drawItems(Graphics2D g2) {
        double pulse = 1.0 + 0.08 * Math.sin(frameCount * 0.12);
        for (ClientGameState.ItemData item : state.items) {
            int px = item.gridX * TILE_SIZE;
            int py = item.gridY * TILE_SIZE;
            int idx = item.typeCode - 1;
            if (idx < 0 || idx >= ITEM_COLORS.length) idx = 0;
            Color c = ITEM_COLORS[idx];

            int sz = (int)(TILE_SIZE * 0.6 * pulse);
            int ox = (TILE_SIZE - sz) / 2;
            int oy = (TILE_SIZE - sz) / 2;

            // Glow
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
            g2.fillOval(px + ox - 4, py + oy - 4, sz + 8, sz + 8);

            // Thân vật phẩm
            g2.setColor(c);
            g2.fillRoundRect(px + ox, py + oy, sz, sz, sz / 3, sz / 3);
            g2.setColor(c.darker());
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(px + ox, py + oy, sz, sz, sz / 3, sz / 3);

            // Icon emoji
            if (idx < ITEM_ICONS.length) {
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, sz / 2));
                FontMetrics fm = g2.getFontMetrics();
                String icon = ITEM_ICONS[idx];
                int tx = px + ox + (sz - fm.stringWidth(icon)) / 2;
                int ty = py + oy + (sz + fm.getAscent() - fm.getDescent()) / 2 - 1;
                g2.drawString(icon, tx, ty);
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Bom
    // ──────────────────────────────────────────────────────────

    private void drawBombs(Graphics2D g2) {
        for (ClientGameState.BombData bomb : state.bombs) {
            int px = bomb.gridX * TILE_SIZE;
            int py = bomb.gridY * TILE_SIZE;

            // Pulse dựa vào countdown (nhanh hơn khi sắp nổ)
            double speed = bomb.countdownMs < 1000 ? 0.4 : 0.12;
            double pulse = 1.0 + 0.12 * Math.sin(frameCount * speed);
            int sz = (int)(TILE_SIZE * 0.72 * pulse);
            int ox = (TILE_SIZE - sz) / 2;
            int oy = (TILE_SIZE - sz) / 2;

            // Shadow
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillOval(px + ox + 3, py + oy + sz - 4, sz - 4, 6);

            // Thân bom
            g2.setColor(COL_BOMB_BODY);
            g2.fillOval(px + ox, py + oy, sz, sz);
            g2.setColor(new Color(0x333333));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(px + ox, py + oy, sz, sz);

            // Highlight trên bom
            g2.setColor(new Color(0x666666));
            g2.fillOval(px + ox + sz / 6, py + oy + sz / 8, sz / 3, sz / 4);

            // Ngòi nổ (fuse)
            int fx = px + ox + sz / 2;
            int fy = py + oy;
            g2.setColor(new Color(0x8B4513));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new QuadCurve2D.Float(fx, fy, fx + 8, fy - 10, fx + 4, fy - 18));

            // Tia lửa ngòi (nhấp nháy)
            if ((frameCount / 4) % 2 == 0) {
                g2.setColor(COL_BOMB_FUSE);
                g2.fillOval(fx + 2, fy - 21, 6, 6);
                g2.setColor(ACCENT_YELLOW);
                g2.fillOval(fx + 3, fy - 20, 4, 4);
            }
        }
    }

    private static final Color ACCENT_YELLOW = new Color(0xFFDD00);

    // ──────────────────────────────────────────────────────────
    //  Hiệu ứng lửa nổ
    // ──────────────────────────────────────────────────────────

    private void drawExplosions(Graphics2D g2) {
        for (ClientGameState.ExplosionEffect ex : state.explosions) {
            float progress = ex.getProgress();
            // Fade out ở cuối
            float alpha = progress < 0.7f ? 1.0f : 1.0f - (progress - 0.7f) / 0.3f;

            for (int[] cell : ex.flameCells) {
                drawFlameCell(g2, cell[0] * TILE_SIZE, cell[1] * TILE_SIZE, progress, alpha);
            }
        }
    }

    private void drawFlameCell(Graphics2D g2, int px, int py, float progress, float alpha) {
        int margin = 4;
        int sz = TILE_SIZE - margin * 2;

        // Lớp ngoài
        Color outer = blendColor(COL_FLAME_E, COL_FLAME_M, progress);
        g2.setColor(withAlpha(outer, (int)(alpha * 200)));
        g2.fillRoundRect(px + margin, py + margin, sz, sz, 10, 10);

        // Lớp giữa
        g2.setColor(withAlpha(COL_FLAME_M, (int)(alpha * 220)));
        g2.fillRoundRect(px + margin + 4, py + margin + 4, sz - 8, sz - 8, 8, 8);

        // Lõi trắng
        g2.setColor(withAlpha(COL_FLAME_C, (int)(alpha * 255)));
        g2.fillRoundRect(px + margin + 8, py + margin + 8, sz - 16, sz - 16, 6, 6);
    }

    private Color blendColor(Color a, Color b, float t) {
        int r = (int)(a.getRed()   * (1-t) + b.getRed()   * t);
        int g = (int)(a.getGreen() * (1-t) + b.getGreen() * t);
        int bl= (int)(a.getBlue()  * (1-t) + b.getBlue()  * t);
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(alpha));
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    // ──────────────────────────────────────────────────────────
    //  Nhân vật
    // ──────────────────────────────────────────────────────────

    private void drawPlayers(Graphics2D g2) {
        for (ClientGameState.PlayerData pd : state.players.values()) {
            int px = (int)(pd.x * TILE_SIZE);
            int py = (int)(pd.y * TILE_SIZE);
            Color pc = getPlayerColor(pd.id);
            Color pcDark = getPlayerDark(pd.id);

            // Khi người chơi bị tiêu diệt hoặc đã rời khỏi phòng: không để lại bóng mờ trong sân đấu
            if (!pd.alive || (pd.name != null && pd.name.contains("(Rời)"))) {
                continue;
            }

            drawCharacter(g2, px, py, pc, pcDark, pd, true);

            // Shield glow
            if (pd.hasShield) {
                double shieldPulse = 0.85 + 0.15 * Math.sin(frameCount * 0.15);
                int sr = (int)(TILE_SIZE * 0.55 * shieldPulse);
                int sox = TILE_SIZE / 2 - sr;
                int soy = TILE_SIZE / 2 - sr;
                g2.setColor(new Color(0xFF, 0xD7, 0x00, 60));
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(px + sox, py + soy, sr * 2, sr * 2);
                g2.setColor(new Color(0xFF, 0xD7, 0x00, 30));
                g2.drawOval(px + sox - 2, py + soy - 2, sr * 2 + 4, sr * 2 + 4);
            }

            // Hiện rõ tên người chơi đã nhập trên đầu nhân vật
            String pName = (pd.name != null && !pd.name.isEmpty()) ? pd.name : ("P" + pd.id);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();
            int nameW = fm.stringWidth(pName);
            int nameX = px + (TILE_SIZE - nameW) / 2;
            int nameY = py - 4;

            // Background pill mờ
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(nameX - 4, nameY - 10, nameW + 8, 13, 6, 6);
            if (pd.id == state.myPlayerId) {
                g2.setColor(ACCENT_YELLOW);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(nameX - 4, nameY - 10, nameW + 8, 13, 6, 6);
                g2.drawString(pName, nameX, nameY);
            } else {
                g2.setColor(Color.WHITE);
                g2.drawString(pName, nameX, nameY);
            }
        }
    }

    private void drawCharacter(Graphics2D g2, int px, int py, Color pc, Color pcDark,
                                ClientGameState.PlayerData pd, boolean alive) {
        // ── Nhân vật 2.5D: Helmet + Body + Arms + Legs ──
        boolean moving = pd.direction != null && !pd.direction.equals("STOP");
        boolean hasGun = pd.weaponAmmo > 0;
        String dir = pd.direction != null ? pd.direction : "DOWN";

        // Thông số kích thước
        int bodyW = 28;
        int bodyH = 26;
        int bx = px + (TILE_SIZE - bodyW) / 2;  // X giữa ô
        int by = px + (TILE_SIZE - bodyH) / 2;  // dùng py bên dưới
        bx = px + (TILE_SIZE - bodyW) / 2;
        by = py + (TILE_SIZE - bodyH) / 2 - 2;

        // ── 1. Shadow dưới chân ──
        g2.setColor(new Color(0, 0, 0, 50));
        g2.fillOval(px + 8, py + TILE_SIZE - 7, TILE_SIZE - 16, 5);

        // ── 2. Hai chân (animate khi di chuyển) ──
        int legY = by + bodyH - 2;
        int legW = 8; int legH = 9;
        // offset chân trái/phải dao động khi đi
        int legOff = moving ? (int)(3 * Math.sin(legFrame * Math.PI / 4.0)) : 0;
        // Chân trái
        g2.setColor(pcDark);
        g2.fillRoundRect(bx + 3, legY - legOff, legW, legH + legOff, 4, 4);
        // Chân phải
        g2.fillRoundRect(bx + bodyW - legW - 3, legY + legOff, legW, legH - legOff, 4, 4);
        // Viền chân
        g2.setColor(pcDark.darker());
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(bx + 3, legY - legOff, legW, legH + legOff, 4, 4);
        g2.drawRoundRect(bx + bodyW - legW - 3, legY + legOff, legW, legH - legOff, 4, 4);

        // ── 3. Thân chính (body) ──
        GradientPaint bodyGrad = new GradientPaint(
            bx, by, pc.brighter(),
            bx, by + bodyH, pc.darker());
        g2.setPaint(bodyGrad);
        g2.fillRoundRect(bx, by, bodyW, bodyH, 10, 10);
        g2.setColor(pcDark);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(bx, by, bodyW, bodyH, 10, 10);

        // ── 4. Hai tay (arms) ──
        int armW = 6; int armH = 14;
        int armY = by + 5;
        // Arm trái
        g2.setColor(pc);
        g2.fillRoundRect(bx - armW + 1, armY, armW, armH, 4, 4);
        g2.setColor(pcDark);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(bx - armW + 1, armY, armW, armH, 4, 4);
        // Arm phải (+ hình khẩu súng nếu có)
        g2.setColor(pc);
        g2.fillRoundRect(bx + bodyW - 1, armY, armW, armH, 4, 4);
        g2.setColor(pcDark);
        g2.drawRoundRect(bx + bodyW - 1, armY, armW, armH, 4, 4);
        if (hasGun) {
            // Vẽ nòng súng nhỏ bên phải
            g2.setColor(new Color(0x333333));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (dir) {
                case "RIGHT": g2.drawLine(bx + bodyW + armW, armY + 4, bx + bodyW + armW + 8, armY + 4); break;
                case "LEFT":  g2.drawLine(bx - armW + 1, armY + 4, bx - armW - 7, armY + 4); break;
                case "UP":    g2.drawLine(bx + bodyW/2, by - 2, bx + bodyW/2, by - 10); break;
                default:      g2.drawLine(bx + bodyW/2, by + bodyH + armH/2, bx + bodyW/2, by + bodyH + armH/2 + 8); break;
            }
        }

        // ── 5. Helmet (mũ) ──
        Color helmetColor = pcDark;
        g2.setColor(helmetColor);
        g2.fillRoundRect(bx + 2, by - 8, bodyW - 4, 12, 8, 8);
        // Dải helmet
        g2.setColor(pc.brighter());
        g2.fillRect(bx + 2, by - 3, bodyW - 4, 4);
        // Viễn helmet
        g2.setColor(new Color(0, 0, 0, 80));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(bx + 2, by - 8, bodyW - 4, 12, 8, 8);

        // ── 6. Mắt (theo hướng) ──
        drawEyes(g2, bx, by, dir, bodyW, bodyH);
    }

    private void drawEyes(Graphics2D g2, int bx, int by, String dir, int bodyW, int bodyH) {
        int eyeR = 4; // bán kính mắt
        int cx = bx + bodyW / 2;
        int cy = by + bodyH / 2 - 2;

        int ex1 = cx - 6, ey1 = cy;
        int ex2 = cx + 2, ey2 = cy;
        switch (dir == null ? "DOWN" : dir) {
            case "UP":
                ex1 = cx - 6; ey1 = by + 6;
                ex2 = cx + 2; ey2 = by + 6;
                break;
            case "LEFT":
                ex1 = bx + 4; ey1 = cy - 4;
                ex2 = bx + 4; ey2 = cy + 3;
                break;
            case "RIGHT":
                ex1 = bx + bodyW - 9; ey1 = cy - 4;
                ex2 = bx + bodyW - 9; ey2 = cy + 3;
                break;
            default: // DOWN
                ex1 = cx - 6; ey1 = cy + 3;
                ex2 = cx + 2; ey2 = cy + 3;
        }
        // Lòng trắng mắt
        g2.setColor(Color.WHITE);
        g2.fillOval(ex1 - 1, ey1 - 1, eyeR + 2, eyeR + 2);
        g2.fillOval(ex2 - 1, ey2 - 1, eyeR + 2, eyeR + 2);
        // Con ngươi
        g2.setColor(new Color(0x111111));
        g2.fillOval(ex1, ey1, eyeR, eyeR);
        g2.fillOval(ex2, ey2, eyeR, eyeR);
        // Ánh sáng mắt
        g2.setColor(Color.WHITE);
        g2.fillOval(ex1 + 1, ey1 + 1, 2, 2);
        g2.fillOval(ex2 + 1, ey2 + 1, 2, 2);
    }

    // ──────────────────────────────────────────────────────────
    //  Màn hình đếm ngược 3 giây
    // ──────────────────────────────────────────────────────────

    private void drawCountdownOverlay(Graphics2D g2) {
        // Ẩn hoàn toàn màn hình đếm ngược và thông báo bắt đầu sau khi bắt đầu game
        if (state.gameStarted || state.countdownSeconds <= 0) {
            return;
        }

        int mapW = GameConstants.MAP_WIDTH * TILE_SIZE;
        int mapH = GameConstants.MAP_HEIGHT * TILE_SIZE;

        // Mờ nhẹ bản đồ trong thời gian đếm ngược 3..2..1
        g2.setColor(new Color(0, 0, 0, 110));
        g2.fillRect(0, 0, mapW, mapH);

        int cx = mapW / 2;
        int cy = mapH / 2;

        String numStr = String.valueOf(state.countdownSeconds);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 88));
        FontMetrics fm = g2.getFontMetrics();
        int tx = cx - fm.stringWidth(numStr) / 2;

        g2.setColor(new Color(0, 0, 0, 200));
        g2.drawString(numStr, tx + 5, cy + 5);
        g2.setColor(ACCENT_YELLOW);
        g2.drawString(numStr, tx, cy);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g2.setColor(Color.WHITE);
        String sub = "TRẬN ĐẤU BẮT ĐẦU SAU...";
        fm = g2.getFontMetrics();
        g2.drawString(sub, cx - fm.stringWidth(sub) / 2, cy + 45);
    }

    // ──────────────────────────────────────────────────────────
    //  UI Kết quả: Chỉ hiển thị nhân vật + tên người thắng + vật phẩm nhặt được
    // ──────────────────────────────────────────────────────────

    private void drawGameOverOverlay(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();

        // Mờ tối toàn bộ màn hình
        g2.setColor(new Color(0, 0, 0, 210));
        g2.fillRect(0, 0, w, h);

        int cx = w / 2;
        int cy = h / 2;

        int cw = 460, ch = 360;
        int crx = cx - cw / 2, cry = cy - ch / 2;

        // Card nền kết quả
        g2.setColor(new Color(0x0e, 0x18, 0x2a));
        g2.fillRoundRect(crx, cry, cw, ch, 20, 20);
        g2.setColor(new Color(0xFF, 0xD7, 0x00));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(crx, cry, cw, ch, 20, 20);

        // Tiêu đề
        g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g2.setColor(new Color(0xFF, 0xD7, 0x00));
        String title = "🏆 CHIẾN THẮNG TRẬN ĐẤU! 🏆";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, cx - fm.stringWidth(title) / 2, cry + 38);

        // 1. Hình ảnh nhân vật chiến thắng (phóng to)
        int avSz = 52;
        int avX = cx - avSz / 2;
        int avY = cry + 50;
        int winId = state.winnerPlayerId > 0 ? state.winnerPlayerId : 1;
        Color winColor = getPlayerColor(winId);
        Color winDark = getPlayerDark(winId);

        g2.setColor(winColor);
        g2.fillRoundRect(avX, avY, avSz, avSz, 14, 14);
        g2.setColor(winDark);
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(avX, avY, avSz, avSz, 14, 14);

        // Mắt nhân vật
        g2.setColor(Color.WHITE);
        g2.fillOval(avX + 11, avY + 16, 8, 8);
        g2.fillOval(avX + 33, avY + 16, 8, 8);
        g2.setColor(Color.BLACK);
        g2.fillOval(avX + 14, avY + 19, 4, 4);
        g2.fillOval(avX + 36, avY + 19, 4, 4);

        // Vương miện
        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        g2.drawString("👑", avX + 13, avY - 2);

        // 2. Tên người thắng cuộc
        g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
        g2.setColor(Color.WHITE);
        String wName = (state.winnerName.isEmpty() ? "Player " + winId : state.winnerName);
        fm = g2.getFontMetrics();
        g2.drawString(wName, cx - fm.stringWidth(wName) / 2, cry + 125);

        // 3. Thông tin về các vật phẩm nhặt được
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(new Color(0x90, 0xA4, 0xAE));
        String subItems = "— VẬT PHẨM ĐÃ NHẶT TRONG TRẬN —";
        fm = g2.getFontMetrics();
        g2.drawString(subItems, cx - fm.stringWidth(subItems) / 2, cry + 150);

        int itemBoxY = cry + 162;
        int itemW = 74, itemH = 64;
        int itemGap = 6;
        int startItemX = cx - (itemW * 5 + itemGap * 4) / 2;

        drawWinnerItemBox(g2, startItemX, itemBoxY, itemW, itemH, "💣", "Thêm bom", state.winnerCount);
        drawWinnerItemBox(g2, startItemX + (itemW + itemGap), itemBoxY, itemW, itemH, "🔥", "Tầm nổ", state.winnerRange);
        drawWinnerItemBox(g2, startItemX + (itemW + itemGap) * 2, itemBoxY, itemW, itemH, "⚡", "Tốc độ", state.winnerSpeed);
        drawWinnerItemBox(g2, startItemX + (itemW + itemGap) * 3, itemBoxY, itemW, itemH, "🛡️", "Khiên", state.winnerShield);
        drawWinnerItemBox(g2, startItemX + (itemW + itemGap) * 4, itemBoxY, itemW, itemH, "🔫", "Súng", state.winnerGun);

        // Hai nút hành động khi kết thúc trận: THOÁT PHÒNG và CHƠI TIẾP
        restartClickAreas.clear();
        int btnY = cry + ch - 76;
        int btnW = 160, btnH = 40;
        int btn1X = cx - btnW - 10;
        int btn2X = cx + 10;

        // Nút Thoát Phòng (type 2)
        g2.setColor(new Color(0xe7, 0x4c, 0x3c));
        g2.fillRoundRect(btn1X, btnY, btnW, btnH, 10, 10);
        g2.setColor(new Color(0xc0, 0x39, 0x2b));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(btn1X, btnY, btnW, btnH, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        String noLabel = "🚪 THOÁT PHÒNG";
        FontMetrics fmn = g2.getFontMetrics();
        g2.drawString(noLabel, btn1X + (btnW - fmn.stringWidth(noLabel)) / 2, btnY + btnH / 2 + 5);
        restartClickAreas.add(new long[]{btn1X, btnY, btnW, btnH, 2});

        // Nút Chơi Tiếp (type 1)
        g2.setColor(new Color(0x27, 0xae, 0x60));
        g2.fillRoundRect(btn2X, btnY, btnW, btnH, 10, 10);
        g2.setColor(new Color(0x21, 0x96, 0x53));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(btn2X, btnY, btnW, btnH, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        String yesLabel = "🔄 CHƠI TIẾP";
        FontMetrics fmy = g2.getFontMetrics();
        g2.drawString(yesLabel, btn2X + (btnW - fmy.stringWidth(yesLabel)) / 2, btnY + btnH / 2 + 5);
        restartClickAreas.add(new long[]{btn2X, btnY, btnW, btnH, 1});

        // Hướng dẫn
        g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        g2.setColor(new Color(0x78, 0x90, 0x9C));
        String hint = "[ Chọn 'Chơi tiếp' để quay về phòng chờ hoặc 'Thoát phòng' để ra sảnh ]";
        fm = g2.getFontMetrics();
        g2.drawString(hint, cx - fm.stringWidth(hint) / 2, cry + ch - 16);
    }

    private void drawWinnerItemBox(Graphics2D g2, int x, int y, int w, int h, String icon, String label, int count) {
        g2.setColor(new Color(0x16, 0x24, 0x36));
        g2.fillRoundRect(x, y, w, h, 10, 10);
        g2.setColor(new Color(0x27, 0x3b, 0x54));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, w, h, 10, 10);

        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        g2.drawString(icon, x + w / 2 - 9, y + 24);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(new Color(0xB0, 0xBE, 0xC5));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + 42);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2.setColor(count > 0 ? ACCENT_YELLOW : new Color(0x54, 0x6E, 0x7A));
        String countStr = "x" + count;
        fm = g2.getFontMetrics();
        g2.drawString(countStr, x + (w - fm.stringWidth(countStr)) / 2, y + 60);
    }

    private Color getPlayerColor(int id) {
        // Math.floorMod đảm bảo kết quả luôn >= 0 dù id = 0 hoặc âm
        return PLAYER_COLORS[Math.floorMod(id - 1, PLAYER_COLORS.length)];
    }

    private Color getPlayerDark(int id) {
        return PLAYER_DARK[Math.floorMod(id - 1, PLAYER_DARK.length)];
    }

    // ──────────────────────────────────────────────────────────
    //  Animation viên đạn bay (motion blur trail)
    // ──────────────────────────────────────────────────────────

    private void drawBullets(Graphics2D g2) {
        for (ClientGameState.BulletData bd : state.bullets) {
            float progress = bd.getProgress();
            float curX = bd.getCurrentX();
            float curY = bd.getCurrentY();

            float nx = 0, ny = 0;
            if (bd.isRealtime) {
                if ("RIGHT".equalsIgnoreCase(bd.direction)) nx = 1;
                else if ("LEFT".equalsIgnoreCase(bd.direction)) nx = -1;
                else if ("UP".equalsIgnoreCase(bd.direction)) ny = -1;
                else if ("DOWN".equalsIgnoreCase(bd.direction)) ny = 1;
            } else {
                float dx = bd.toX - bd.fromX;
                float dy = bd.toY - bd.fromY;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 0) {
                    nx = dx / len;
                    ny = dy / len;
                }
            }

            int trailCount = 5;
            for (int t = 0; t < trailCount; t++) {
                float trailX, trailY;
                if (bd.isRealtime) {
                    trailX = curX - nx * (t + 1) * 0.12f;
                    trailY = curY - ny * (t + 1) * 0.12f;
                } else {
                    float trailProgress = progress - (t + 1) * 0.06f;
                    if (trailProgress < 0) continue;
                    trailX = bd.fromX + (bd.toX - bd.fromX) * trailProgress;
                    trailY = bd.fromY + (bd.toY - bd.fromY) * trailProgress;
                }

                int trailRadius = (int)(5 - t * 0.8f);
                int alpha = (int)(200 - t * 35);
                if (trailRadius <= 0 || alpha <= 0) continue;

                int tpx = (int)(trailX * TILE_SIZE) + TILE_SIZE / 2;
                int tpy = (int)(trailY * TILE_SIZE) + TILE_SIZE / 2;

                // Màu trail: vàng → trắng ở đầu
                g2.setColor(new Color(0xFF, 0xCC + t * 6, 0x00, Math.min(255, alpha)));
                g2.fillOval(tpx - trailRadius, tpy - trailRadius, trailRadius * 2, trailRadius * 2);
            }

            // Vẽ đầu đạn chính (hình elipse nhỏ sáng)
            int bpx = (int)(curX * TILE_SIZE) + TILE_SIZE / 2;
            int bpy = (int)(curY * TILE_SIZE) + TILE_SIZE / 2;

            // Glow ngoài
            g2.setColor(new Color(0xFF, 0xE0, 0x00, 100));
            g2.fillOval(bpx - 7, bpy - 7, 14, 14);

            // Lõi sáng
            g2.setColor(new Color(0xFF, 0xFF, 0xFF, 230));
            g2.fillOval(bpx - 4, bpy - 4, 8, 8);

            // Đường hướng bay nhỏ
            g2.setColor(new Color(0xFF, 0xD7, 0x00, 180));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(
                bpx - (int)(nx * 8), bpy - (int)(ny * 8),
                bpx + (int)(nx * 4), bpy + (int)(ny * 4)
            );
        }
        // Reset stroke
        g2.setStroke(new BasicStroke(1f));
    }

    // ──────────────────────────────────────────────────────────
    //  Victory / Defeat overlay 3 giây (hiện trước bảng kết quả)
    // ──────────────────────────────────────────────────────────

    private void drawVictoryDefeatOverlay(Graphics2D g2) {
        boolean iWon = (state.winnerPlayerId > 0 && state.winnerPlayerId == state.myPlayerId);
        int w = getWidth();
        int h = getHeight();
        long elapsed = System.currentTimeMillis() - state.victoryScreenStartMs;

        // Bounce cho winner, shake cho loser
        double offset = 0;
        if (iWon) {
            // Bounce: sin wave đi lên xuống
            offset = 12 * Math.sin(elapsed / 200.0);
        } else {
            // Shake: rung ngang
            offset = 6 * Math.sin(elapsed / 60.0);
        }

        // Mờ tối nền
        float fadeIn = Math.min(1f, elapsed / 400f);
        g2.setColor(new Color(0, 0, 0, (int)(180 * fadeIn)));
        g2.fillRect(0, 0, w, h);

        int cx = w / 2;
        int cy = h / 2;

        if (iWon) {
            // Vòng vàng pulse
            double pulse = 0.9 + 0.1 * Math.sin(elapsed / 150.0);
            int r = (int)(100 * pulse);
            g2.setColor(new Color(0xFF, 0xD7, 0x00, 40));
            g2.fillOval(cx - r - 20, (int)(cy - r - 20 + offset), (r + 20) * 2, (r + 20) * 2);
            g2.setColor(new Color(0xFF, 0xD7, 0x00, 80));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(cx - r, (int)(cy - r + offset), r * 2, r * 2);

            // Text chiến thắng
            g2.setFont(new Font("Segoe UI", Font.BOLD, 52));
            String text = "\uD83C\uDFC6 CHIẾN THẮNG!";
            FontMetrics fm = g2.getFontMetrics();
            // shadow
            g2.setColor(new Color(0, 0, 0, 180));
            g2.drawString(text, cx - fm.stringWidth(text) / 2 + 4, (int)(cy + 4 + offset));
            // text
            g2.setColor(new Color(0xFF, 0xD7, 0x00));
            g2.drawString(text, cx - fm.stringWidth(text) / 2, (int)(cy + offset));

            // Tên người chơi bên dưới
            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
            String sub = state.winnerName.isEmpty() ? "" : state.winnerName;
            fm = g2.getFontMetrics();
            g2.setColor(Color.WHITE);
            g2.drawString(sub, cx - fm.stringWidth(sub) / 2, (int)(cy + 52 + offset));
        } else {
            // Text thất bại
            int sx = (int) offset; // shake horizontal
            g2.setFont(new Font("Segoe UI", Font.BOLD, 52));
            String text = "\uD83D\uDC80 THẤT BẠI...";
            FontMetrics fm = g2.getFontMetrics();
            // shadow
            g2.setColor(new Color(0, 0, 0, 180));
            g2.drawString(text, cx - fm.stringWidth(text) / 2 + sx + 4, cy + 4);
            // text
            g2.setColor(new Color(0xFF, 0x44, 0x44));
            g2.drawString(text, cx - fm.stringWidth(text) / 2 + sx, cy);

            // Tên người thắng
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            String sub = (state.winnerPlayerId > 0 && !state.winnerName.isEmpty())
                ? "Người chiến thắng: " + state.winnerName
                : "";
            fm = g2.getFontMetrics();
            g2.setColor(new Color(0x90, 0xA4, 0xAE));
            g2.drawString(sub, cx - fm.stringWidth(sub) / 2 + sx, cy + 50);
        }

        // Countdown nhỏ bên dưới "Kết quả còn X giây..."
        long remaining = (ClientGameState.VICTORY_SCREEN_DURATION_MS - elapsed) / 1000 + 1;
        if (remaining > 0) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g2.setColor(new Color(0x78, 0x90, 0xA0, 180));
            String hint = "Kết quả sau " + remaining + " giây...";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(hint, cx - fm.stringWidth(hint) / 2, cy + 100);
        }
    }
}

