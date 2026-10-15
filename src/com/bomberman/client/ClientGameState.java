package com.bomberman.client;

import com.bomberman.common.GameConstants;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lưu trữ toàn bộ trạng thái thế giới game phía Client.
 * Được cập nhật liên tục từ các gói tin Server gửi về.
 */
public class ClientGameState {

    // ───── Trạng thái kết nối ─────
    public volatile int myPlayerId = -1;
    public volatile boolean connected = false;
    public volatile boolean gameStarted = false;
    public volatile boolean gameOver = false;
    public volatile int winnerPlayerId = -1;
    public volatile String winnerName = "";
    public volatile int winnerRange = 0;
    public volatile int winnerCount = 0;
    public volatile int winnerSpeed = 0;
    public volatile int winnerShield = 0;
    public volatile int winnerGun = 0;

    // ───── Đếm ngược trận đấu ─────
    public volatile int countdownSeconds = -1;
    public volatile String countdownText = "";
    public volatile long countdownStartTime = 0;

    public PlayerData getMyPlayer() {
        return players.get(myPlayerId);
    }

    // ───── Bản đồ ─────
    public volatile int mapWidth = GameConstants.MAP_WIDTH;
    public volatile int mapHeight = GameConstants.MAP_HEIGHT;
    public volatile int[][] mapGrid = new int[GameConstants.MAP_HEIGHT][GameConstants.MAP_WIDTH];

    // ───── Danh sách đối tượng game ─────
    public final Map<Integer, PlayerData> players = new ConcurrentHashMap<>();
    public final List<BombData> bombs = new CopyOnWriteArrayList<>();
    public final List<ItemData> items = new CopyOnWriteArrayList<>();

    // ───── Hiệu ứng nổ (flame cells) ─────
    public final List<ExplosionEffect> explosions = new CopyOnWriteArrayList<>();

    // ───── Thứ tự người sống sót ─────
    public final List<Integer> eliminationOrder = new CopyOnWriteArrayList<>(); // ID người bị loại theo thứ tự

    // ───── Tick mới nhất ─────
    public volatile long latestTick = 0;

    // ──────────────────────────────────────────────────────────
    //  Data classes (inner)
    // ──────────────────────────────────────────────────────────

    public static class PlayerData {
        public int id;
        public String name = "";
        public double x, y;
        public boolean alive;
        public int score;
        public String direction;
        public boolean hasShield;
        public int weaponAmmo;
        public int kills = 0;
        public int pickedBombRange = 0;
        public int pickedBombCount = 0;
        public int pickedSpeed = 0;
        public int pickedShield = 0;
        public int pickedWeapon = 0;

        public int getGridX() { return (int) Math.round(x); }
        public int getGridY() { return (int) Math.round(y); }
    }

    public static class BombData {
        public String id;
        public int gridX, gridY;
        public long countdownMs;
    }

    public static class ItemData {
        public String id;
        public int typeCode;
        public int gridX, gridY;
    }

    public static class ExplosionEffect {
        public final List<int[]> flameCells; // [x, y]
        public long startTimeMs;
        public static final long DURATION_MS = GameConstants.EXPLOSION_DURATION_MS;

        public ExplosionEffect(List<int[]> flameCells) {
            this.flameCells = flameCells;
            this.startTimeMs = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - startTimeMs > DURATION_MS;
        }

        public float getProgress() {
            float p = (System.currentTimeMillis() - startTimeMs) / (float) DURATION_MS;
            return Math.min(1f, p);
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Các phương thức parse gói tin từ Server
    // ──────────────────────────────────────────────────────────

    /**
     * Xử lý MAP_INIT|width|height|flatString
     */
    public void parseMapInit(String[] parts) {
        if (parts.length < 4) return;
        try {
            mapWidth = Integer.parseInt(parts[1]);
            mapHeight = Integer.parseInt(parts[2]);
            String flat = parts[3];
            mapGrid = new int[mapHeight][mapWidth];
            int idx = 0;
            for (int y = 0; y < mapHeight; y++) {
                for (int x = 0; x < mapWidth; x++) {
                    if (idx < flat.length()) {
                        mapGrid[y][x] = flat.charAt(idx++) - '0';
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[STATE] Lỗi parse MAP_INIT: " + e.getMessage());
        }
    }

    /**
     * Xử lý STATE|tick|players_data|bombs_data|items_data
     */
    public void parseState(String[] parts) {
        if (parts.length < 4) return;
        try {
            latestTick = Long.parseLong(parts[1]);

            // --- Players ---
            if (!parts[2].equals("NONE") && !parts[2].isEmpty()) {
                for (String pStr : parts[2].split(";")) {
                    String[] f = pStr.split(",");
                    if (f.length < 8) continue;
                    int id = Integer.parseInt(f[0]);
                    PlayerData pd = players.computeIfAbsent(id, k -> {
                        PlayerData np = new PlayerData();
                        np.id = k;
                        return np;
                    });
                    pd.x = Double.parseDouble(f[1]);
                    pd.y = Double.parseDouble(f[2]);
                    pd.alive = f[3].equals("1");
                    pd.score = Integer.parseInt(f[4]);
                    pd.direction = f[5];
                    pd.hasShield = f[6].equals("1");
                    pd.weaponAmmo = Integer.parseInt(f[7]);

                    if (f.length >= 14) {
                        pd.kills = Integer.parseInt(f[8]);
                        pd.pickedBombRange = Integer.parseInt(f[9]);
                        pd.pickedBombCount = Integer.parseInt(f[10]);
                        pd.pickedSpeed = Integer.parseInt(f[11]);
                        pd.pickedShield = Integer.parseInt(f[12]);
                        pd.pickedWeapon = Integer.parseInt(f[13]);
                    }
                }
            }

            // --- Bombs ---
            bombs.clear();
            if (!parts[3].equals("NONE") && !parts[3].isEmpty()) {
                for (String bStr : parts[3].split(";")) {
                    String[] f = bStr.split(",");
                    if (f.length < 4) continue;
                    BombData bd = new BombData();
                    bd.id = f[0];
                    bd.gridX = Integer.parseInt(f[1]);
                    bd.gridY = Integer.parseInt(f[2]);
                    bd.countdownMs = Long.parseLong(f[3]);
                    bombs.add(bd);
                }
            }

            // --- Items ---
            items.clear();
            if (parts.length > 4 && !parts[4].equals("NONE") && !parts[4].isEmpty()) {
                for (String iStr : parts[4].split(";")) {
                    String[] f = iStr.split(",");
                    if (f.length < 4) continue;
                    ItemData item = new ItemData();
                    item.id = f[0];
                    item.typeCode = Integer.parseInt(f[1]);
                    item.gridX = Integer.parseInt(f[2]);
                    item.gridY = Integer.parseInt(f[3]);
                    items.add(item);
                }
            }
        } catch (Exception e) {
            System.err.println("[STATE] Lỗi parse STATE: " + e.getMessage());
        }
    }

    /**
     * Xử lý TILE_UPDATE|x|y|tileCode
     */
    public void parseTileUpdate(String[] parts) {
        if (parts.length < 4) return;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int code = Integer.parseInt(parts[3]);
            if (y >= 0 && y < mapHeight && x >= 0 && x < mapWidth) {
                mapGrid[y][x] = code;
            }
        } catch (Exception ignored) {}
    }

    /**
     * Xử lý EXPLODE|bombId|x1,y1;x2,y2;...
     */
    public void parseExplode(String[] parts) {
        if (parts.length < 3) return;
        List<int[]> cells = new ArrayList<>();
        for (String cellStr : parts[2].split(";")) {
            String[] xy = cellStr.split(",");
            if (xy.length == 2) {
                try {
                    cells.add(new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])});
                } catch (Exception ignored) {}
            }
        }
        if (!cells.isEmpty()) {
            explosions.add(new ExplosionEffect(cells));
        }
    }

    /**
     * Xử lý ITEM_SPAWN|itemId|typeCode|gridX|gridY
     */
    public void parseItemSpawn(String[] parts) {
        if (parts.length < 5) return;
        try {
            ItemData item = new ItemData();
            item.id = parts[1];
            item.typeCode = Integer.parseInt(parts[2]);
            item.gridX = Integer.parseInt(parts[3]);
            item.gridY = Integer.parseInt(parts[4]);
            items.removeIf(i -> i.id.equals(item.id));
            items.add(item);
        } catch (Exception ignored) {}
    }

    /**
     * Xử lý ITEM_COLLECT|itemId|playerId|typeCode|typeName
     */
    public void parseItemCollect(String[] parts) {
        if (parts.length < 2) return;
        String itemId = parts[1];
        items.removeIf(i -> i.id.equals(itemId));
    }

    /**
     * Xử lý ITEM_DESTROYED|itemId
     */
    public void parseItemDestroyed(String[] parts) {
        if (parts.length < 2) return;
        items.removeIf(i -> i.id.equals(parts[1]));
    }

    /**
     * Xử lý PLAYER_DIED|playerId|killerId
     * Ghi nhận thứ tự bị loại.
     */
    public void parsePlayerDied(String[] parts) {
        if (parts.length < 2) return;
        try {
            int deadId = Integer.parseInt(parts[1]);
            PlayerData pd = players.get(deadId);
            if (pd != null) pd.alive = false;
            if (!eliminationOrder.contains(deadId)) {
                eliminationOrder.add(deadId);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Làm sạch các hiệu ứng nổ đã hết hạn.
     */
    public void cleanupExpiredExplosions() {
        explosions.removeIf(ExplosionEffect::isExpired);
    }

    /**
     * Đặt tên người chơi (nhận từ JOIN_ACK).
     */
    public void setPlayerName(int id, String name) {
        PlayerData pd = players.computeIfAbsent(id, k -> {
            PlayerData np = new PlayerData();
            np.id = k;
            return np;
        });
        pd.name = name;
    }

    /**
     * Xử lý COUNTDOWN|seconds|START?
     */
    public void parseCountdown(String[] parts) {
        if (parts.length < 2) return;
        try {
            countdownSeconds = Integer.parseInt(parts[1]);
            countdownStartTime = System.currentTimeMillis();
            if (parts.length > 2 && parts[2].equalsIgnoreCase("START")) {
                countdownText = "";
                countdownSeconds = -1;
                gameStarted = true;
            } else if (countdownSeconds > 0) {
                countdownText = String.valueOf(countdownSeconds);
            } else {
                countdownText = "";
                countdownSeconds = -1;
            }
        } catch (Exception ignored) {}
    }

    /**
     * Xử lý GAME_OVER|winnerId|winnerName|summaryItems
     */
    public void parseGameOver(String[] parts) {
        gameOver = true;
        if (parts.length > 1) {
            try { winnerPlayerId = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
        }
        if (parts.length > 2) {
            winnerName = parts[2];
        }
        if (parts.length > 3) {
            String[] it = parts[3].split(",");
            if (it.length >= 5) {
                try {
                    winnerRange = Integer.parseInt(it[0]);
                    winnerCount = Integer.parseInt(it[1]);
                    winnerSpeed = Integer.parseInt(it[2]);
                    winnerShield = Integer.parseInt(it[3]);
                    winnerGun = Integer.parseInt(it[4]);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Xử lý PLAYER_LEFT|playerId|playerName
     */
    public void parsePlayerLeft(String[] parts) {
        if (parts.length < 2) return;
        try {
            int leftId = Integer.parseInt(parts[1]);
            PlayerData pd = players.get(leftId);
            if (pd != null) {
                pd.alive = false;
                pd.name = (pd.name.isEmpty() ? "P" + leftId : pd.name) + " (Rời)";
            }
        } catch (Exception ignored) {}
    }

    /**
     * Reset toàn bộ state về trạng thái ban đầu.
     */
    public void reset() {
        players.clear();
        bombs.clear();
        items.clear();
        explosions.clear();
        eliminationOrder.clear();
        mapGrid = new int[GameConstants.MAP_HEIGHT][GameConstants.MAP_WIDTH];
        gameStarted = false;
        gameOver = false;
        winnerPlayerId = -1;
        winnerName = "";
        winnerRange = 0;
        winnerCount = 0;
        winnerSpeed = 0;
        winnerShield = 0;
        winnerGun = 0;
        countdownSeconds = -1;
        countdownText = "";
        countdownStartTime = 0;
        latestTick = 0;
    }
}
