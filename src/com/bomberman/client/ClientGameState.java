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
    public volatile String myPlayerName = "";
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

    // ───── Victory / Defeat screen (hiện 3 giây trước kết quả) ─────
    public volatile boolean showVictoryScreen = false;
    public volatile long victoryScreenStartMs = 0;
    public static final long VICTORY_SCREEN_DURATION_MS = 3000;

    // ───── Restart vote ─────
    public volatile boolean restartVoting = false;
    public volatile int restartVotesYes = 0;
    public volatile int restartVotesTotal = 0;

    // ───── Room ─────
    public volatile String myRoomCode = "";
    public volatile boolean imHost = false;
    public final List<String> roomPlayerNames = new CopyOnWriteArrayList<>();
    public final List<RoomSlot> roomSlots = new CopyOnWriteArrayList<>();
    public volatile int selectedMapType = 0;
    public volatile String currentMapName = "Cổ điển (Classic)";

    public static class RoomSlot {
        public int id;
        public String name;
        public boolean isHost;
        public boolean isReady; // true = thành viên đã nhấn sẵn sàng (host luôn tính là ready)

        public RoomSlot(int id, String name, boolean isHost, boolean isReady) {
            this.id = id;
            this.name = name;
            this.isHost = isHost;
            this.isReady = isReady;
        }
    }

    // ───── Sequence number (lọc out-of-order packet) ─────
    public volatile long lastReceivedSeq = -1;

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

    // ───── Animation viên đạn bay ─────
    public final List<BulletData> bullets = new CopyOnWriteArrayList<>();

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

    /**
     * Biểu diễn viên đạn đang bay trên client (animation interpolation).
     * Hỗ trợ cả BULLET_SPAWN thời gian thực và BULLET_START.
     */
    public static class BulletData {
        public String id = "";
        public int shooterId;
        public float fromX, fromY;   // tọa độ xuất phát (tile grid - float)
        public float toX, toY;       // tọa độ kết thúc (nếu có)
        public String direction = "RIGHT";
        public float speed = 0.40f;  // ô / tick
        public long startTimeMs;
        public boolean isRealtime = false;
        public boolean hit = false;
        public static final long DURATION_MS = 280; // 280ms bay hết

        public BulletData(int shooterId, float fromX, float fromY, float toX, float toY) {
            this.shooterId = shooterId;
            this.fromX = fromX; this.fromY = fromY;
            this.toX = toX; this.toY = toY;
            this.startTimeMs = System.currentTimeMillis();
            this.isRealtime = false;
        }

        public BulletData(String id, int shooterId, float startX, float startY, String dir, float speed) {
            this.id = id;
            this.shooterId = shooterId;
            this.fromX = startX; this.fromY = startY;
            this.direction = dir;
            this.speed = speed;
            this.startTimeMs = System.currentTimeMillis();
            this.isRealtime = true;
        }

        public float getProgress() {
            if (!isRealtime) {
                float p = (System.currentTimeMillis() - startTimeMs) / (float) DURATION_MS;
                return Math.min(1f, p);
            }
            // Realtime: đạn bay tối đa 12 ô
            float elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000f;
            float tilesTraveled = speed * 30f * elapsedSec;
            return Math.min(1f, tilesTraveled / 12f);
        }

        public boolean isExpired() {
            if (hit) return true;
            if (!isRealtime) return getProgress() >= 1f;
            return (System.currentTimeMillis() - startTimeMs) > 2000;
        }

        /** Vị trí hiện tại (grid float) interpolate */
        public float getCurrentX() {
            if (!isRealtime) {
                return fromX + (toX - fromX) * getProgress();
            }
            float elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000f;
            float tilesPerSec = speed * 30f;
            float dx = 0;
            if ("RIGHT".equalsIgnoreCase(direction)) dx = 1;
            else if ("LEFT".equalsIgnoreCase(direction)) dx = -1;
            return fromX + dx * tilesPerSec * elapsedSec;
        }

        public float getCurrentY() {
            if (!isRealtime) {
                return fromY + (toY - fromY) * getProgress();
            }
            float elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000f;
            float tilesPerSec = speed * 30f;
            float dy = 0;
            if ("DOWN".equalsIgnoreCase(direction)) dy = 1;
            else if ("UP".equalsIgnoreCase(direction)) dy = -1;
            return fromY + dy * tilesPerSec * elapsedSec;
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
            // Reset chuỗi Sequence Number và các đối tượng tạm thời khi nhận map mới
            lastReceivedSeq = -1;
            latestTick = 0;
            bombs.clear();
            items.clear();
            explosions.clear();
            bullets.clear();
            resetPlayerPositionsToSpawn();
        } catch (Exception e) {
            System.err.println("[STATE] Lỗi parse MAP_INIT: " + e.getMessage());
        }
    }

    /**
     * Xử lý STATE packet.
     * Định dạng mới (7 phần): STATE|seq|timestamp|tick|players_data|bombs_data|items_data
     * Định dạng cũ (5 phần):  STATE|tick|players_data|bombs_data|items_data
     */
    public void parseState(String[] parts) {
        if (parts.length < 4) return;
        try {
            int pIndex = 2;
            int bIndex = 3;
            int iIndex = 4;

            if (parts.length >= 7) {
                // Định dạng mới có Sequence Number + Timestamp
                long seq = Long.parseLong(parts[1]);
                // Lọc out-of-order packet: bỏ qua nếu seq cũ hơn hoặc bằng (xử lý modulo)
                if (lastReceivedSeq >= 0) {
                    // Nếu server vừa reset sequence number về đầu (ván mới / restart game)
                    if (seq < 100 && lastReceivedSeq > 500) {
                        lastReceivedSeq = seq;
                    } else {
                        long diff = (seq - lastReceivedSeq + GameConstants.SEQUENCE_NUMBER_MOD) % GameConstants.SEQUENCE_NUMBER_MOD;
                        if (diff > GameConstants.SEQUENCE_NUMBER_MOD / 2 || diff == 0) {
                            return; // Packet cũ hoặc trùng lặp, bỏ qua
                        }
                        lastReceivedSeq = seq;
                    }
                } else {
                    lastReceivedSeq = seq;
                }
                latestTick = Long.parseLong(parts[3]);
                pIndex = 4;
                bIndex = 5;
                iIndex = 6;
            } else {
                latestTick = Long.parseLong(parts[1]);
            }

            // --- Players ---
            if (parts.length > pIndex && !parts[pIndex].equals("NONE") && !parts[pIndex].isEmpty()) {
                Set<Integer> currentActiveIds = new HashSet<>();
                for (String pStr : parts[pIndex].split(";")) {
                    String[] f = pStr.split(",");
                    if (f.length < 8) continue;
                    int id = Integer.parseInt(f[0]);
                    currentActiveIds.add(id);
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
                // Dọn sạch các nhân vật cũ/đã rời trận không còn được gửi trong STATE của Server
                if (!currentActiveIds.isEmpty()) {
                    players.keySet().removeIf(id -> !currentActiveIds.contains(id));
                }
            }

            // --- Bombs ---
            bombs.clear();
            if (parts.length > bIndex && !parts[bIndex].equals("NONE") && !parts[bIndex].isEmpty()) {
                for (String bStr : parts[bIndex].split(";")) {
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
            if (parts.length > iIndex && !parts[iIndex].equals("NONE") && !parts[iIndex].isEmpty()) {
                for (String iStr : parts[iIndex].split(";")) {
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
        bullets.removeIf(BulletData::isExpired);
    }

    /**
     * Đặt tên người chơi (nhận từ JOIN_ACK).
     */
    public void setPlayerName(int id, String name) {
        if (id == myPlayerId) {
            myPlayerName = name;
        }
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
     * Không set gameOver ngay – chờ sau 3 giây Victory Screen.
     */
    public void parseGameOver(String[] parts) {
        // Hiện Victory/Defeat screen trước
        showVictoryScreen = true;
        victoryScreenStartMs = System.currentTimeMillis();
        // gameOver sẽ được set = true sau 3 giây bởi GamePanel
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
                    winnerRange  = Integer.parseInt(it[0]);
                    winnerCount  = Integer.parseInt(it[1]);
                    winnerSpeed  = Integer.parseInt(it[2]);
                    winnerShield = Integer.parseInt(it[3]);
                    winnerGun    = Integer.parseInt(it[4]);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Xử lý BULLET_START|shooterId|fromX|fromY|toX|toY
     */
    public void parseBulletStart(String[] parts) {
        if (parts.length < 6) return;
        try {
            int sid  = Integer.parseInt(parts[1]);
            float fx = Float.parseFloat(parts[2]);
            float fy = Float.parseFloat(parts[3]);
            float tx = Float.parseFloat(parts[4]);
            float ty = Float.parseFloat(parts[5]);
            bullets.add(new BulletData(sid, fx, fy, tx, ty));
        } catch (Exception ignored) {}
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
     * Xử lý BULLET_SPAWN|bulletId|shooterId|startX|startY|dir|speed
     */
    public void parseBulletSpawn(String[] parts) {
        if (parts.length < 7) return;
        try {
            String bid = parts[1];
            int sid = Integer.parseInt(parts[2]);
            float sx = Float.parseFloat(parts[3]);
            float sy = Float.parseFloat(parts[4]);
            String dir = parts[5];
            float spd = Float.parseFloat(parts[6]);
            bullets.add(new BulletData(bid, sid, sx, sy, dir, spd));
        } catch (Exception ignored) {}
    }

    /**
     * Xử lý BULLET_HIT|bulletId|...
     */
    public void parseBulletHit(String[] parts) {
        if (parts.length < 2) return;
        String bid = parts[1];
        for (BulletData bd : bullets) {
            if (bd.id != null && bd.id.equals(bid)) {
                bd.hit = true;
            }
        }
    }

    /**
     * Xử lý PLAYER_INFO|id|name|isHost
     */
    public void parsePlayerInfo(String[] parts) {
        if (parts.length < 3) return;
        try {
            int id = Integer.parseInt(parts[1]);
            String name = parts[2];
            setPlayerName(id, name);
        } catch (Exception ignored) {}
    }

    /**
     * Xử lý MAP_CHANGED|mapId|mapName
     */
    public void parseMapChanged(String[] parts) {
        if (parts.length < 2) return;
        try {
            selectedMapType = Integer.parseInt(parts[1]);
            if (parts.length > 2) {
                currentMapName = parts[2];
            }
        } catch (Exception ignored) {}
    }

    /**
     * Xử lý ROOM_PLAYERS|id:name:isHost:isReady;... hoặc name1 [HOST];...
     * Định dạng mới gồm 4 trường: id:name:isHost:isReady
     */
    public void parseRoomPlayers(String[] parts) {
        roomPlayerNames.clear();
        roomSlots.clear();
        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
            for (String token : parts[1].split(";")) {
                if (token.trim().isEmpty()) continue;
                if (token.contains(":")) {
                    String[] sub = token.split(":", -1);
                    try {
                        int id = Integer.parseInt(sub[0]);
                        String name = sub.length > 1 ? sub[1] : ("Player" + id);
                        boolean host = sub.length > 2 && "1".equals(sub[2]);
                        boolean ready = sub.length > 3 && "1".equals(sub[3]);
                        roomSlots.add(new RoomSlot(id, name, host, ready));
                        roomPlayerNames.add(name + (host ? " [HOST]" : ""));
                        setPlayerName(id, name);
                    } catch (Exception e) {
                        roomPlayerNames.add(token.trim());
                    }
                } else {
                    roomPlayerNames.add(token.trim());
                }
            }
        }

        // Đồng bộ danh sách players: loại bỏ mọi PlayerData không còn trong roomSlots
        Set<Integer> validSlotIds = new HashSet<>();
        for (RoomSlot slot : roomSlots) {
            validSlotIds.add(slot.id);
        }
        if (!validSlotIds.isEmpty()) {
            players.keySet().removeIf(id -> !validSlotIds.contains(id));
        }
    }

    /**
     * Chuyển trạng thái khi quay về phòng chờ sau trận đấu.
     */
    public void returnToRoomWaiting() {
        gameStarted = false;
        gameOver = false;
        showVictoryScreen = false;
        victoryScreenStartMs = 0;
        restartVoting = false;
        restartVotesYes = 0;
        restartVotesTotal = 0;
        countdownSeconds = -1;
        countdownText = "";
        countdownStartTime = 0;
        latestTick = 0;
        lastReceivedSeq = -1;
        bombs.clear();
        items.clear();
        explosions.clear();
        bullets.clear();
        eliminationOrder.clear();

        // Thu thập các ID đang có trong roomSlots (những người thực sự còn trong phòng)
        Set<Integer> validIds = new HashSet<>();
        for (RoomSlot slot : roomSlots) {
            validIds.add(slot.id);
        }

        // Loại bỏ các PlayerData rác/đã rời khỏi map
        if (!validIds.isEmpty()) {
            players.keySet().removeIf(id -> !validIds.contains(id));
        } else {
            players.clear();
        }

        int idx = 0;
        for (PlayerData pd : players.values()) {
            pd.alive = true;
            pd.kills = 0;
            pd.score = 0;
            pd.hasShield = false;
            pd.weaponAmmo = 0;
            pd.pickedBombRange = 0;
            pd.pickedBombCount = 0;
            pd.pickedSpeed = 0;
            pd.pickedShield = 0;
            pd.pickedWeapon = 0;
            pd.direction = "DOWN";

            // Reset vị trí hiển thị về 4 góc spawn ban đầu
            int spawnIdx = idx % 4;
            pd.x = (spawnIdx == 1 || spawnIdx == 3) ? (GameConstants.MAP_WIDTH - 2.0) : 1.0;
            pd.y = (spawnIdx == 2 || spawnIdx == 3) ? (GameConstants.MAP_HEIGHT - 2.0) : 1.0;
            idx++;
        }
    }

    /**
     * Đưa tất cả người chơi trong phòng về 4 góc xuất phát ban đầu.
     */
    public void resetPlayerPositionsToSpawn() {
        int idx = 0;
        for (PlayerData pd : players.values()) {
            int spawnIdx = idx % 4;
            pd.x = (spawnIdx == 1 || spawnIdx == 3) ? (GameConstants.MAP_WIDTH - 2.0) : 1.0;
            pd.y = (spawnIdx == 2 || spawnIdx == 3) ? (GameConstants.MAP_HEIGHT - 2.0) : 1.0;
            pd.alive = true;
            pd.direction = "DOWN";
            idx++;
        }
    }

    /**
     * Reset các biến trận đấu khi chơi lại trực tiếp trong phòng mà không thoát ra lobby.
     * Giữ nguyên mã phòng, danh sách người chơi, quyền Host và map hiện tại.
     */
    public void resetForNewMatch() {
        gameStarted = false;
        gameOver = false;
        showVictoryScreen = false;
        victoryScreenStartMs = 0;
        restartVoting = false;
        restartVotesYes = 0;
        restartVotesTotal = 0;
        countdownSeconds = -1;
        countdownText = "";
        countdownStartTime = 0;
        latestTick = 0;
        lastReceivedSeq = -1;
        bombs.clear();
        items.clear();
        explosions.clear();
        bullets.clear();
        eliminationOrder.clear();

        // Thu thập các ID hợp lệ từ roomSlots
        Set<Integer> validIds = new HashSet<>();
        for (RoomSlot slot : roomSlots) {
            validIds.add(slot.id);
        }
        if (!validIds.isEmpty()) {
            players.keySet().removeIf(id -> !validIds.contains(id));
        }

        int idx = 0;
        for (PlayerData pd : players.values()) {
            int spawnIdx = idx % 4;
            pd.x = (spawnIdx == 1 || spawnIdx == 3) ? (GameConstants.MAP_WIDTH - 2.0) : 1.0;
            pd.y = (spawnIdx == 2 || spawnIdx == 3) ? (GameConstants.MAP_HEIGHT - 2.0) : 1.0;
            pd.alive = true;
            pd.direction = "DOWN";
            pd.kills = 0;
            pd.score = 0;
            pd.hasShield = false;
            pd.weaponAmmo = 0;
            pd.pickedBombRange = 0;
            pd.pickedBombCount = 0;
            pd.pickedSpeed = 0;
            pd.pickedShield = 0;
            pd.pickedWeapon = 0;
            idx++;
        }
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
        bullets.clear();
        roomPlayerNames.clear();
        roomSlots.clear();
        myRoomCode = "";
        imHost = false;
        mapGrid = new int[GameConstants.MAP_HEIGHT][GameConstants.MAP_WIDTH];
        gameStarted = false;
        gameOver = false;
        showVictoryScreen = false;
        victoryScreenStartMs = 0;
        restartVoting = false;
        restartVotesYes = 0;
        restartVotesTotal = 0;
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
        lastReceivedSeq = -1;
    }
}
