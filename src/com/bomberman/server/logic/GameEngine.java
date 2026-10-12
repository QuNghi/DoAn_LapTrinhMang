package com.bomberman.server.logic;

import com.bomberman.common.Direction;
import com.bomberman.common.GameConstants;
import com.bomberman.common.TileType;
import com.bomberman.server.model.Bomb;
import com.bomberman.server.model.GameMap;
import com.bomberman.server.model.Player;
import com.bomberman.server.network.PlayerSession;
import com.bomberman.server.network.UdpServer;

import java.awt.Point;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Động cơ điều phối game và Game Loop chính trên Server (Authoritative Server).
 */
public class GameEngine {
    private final UdpServer server;
    private final GameMap map;

    private final Map<String, PlayerSession> sessionsByAddress = new ConcurrentHashMap<>();
    private final Map<Integer, PlayerSession> sessionsById = new ConcurrentHashMap<>();
    private final List<Bomb> bombs = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService gameLoopExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);
    private final AtomicInteger bombCounter = new AtomicInteger(1);

    private volatile boolean gameStarted = false;
    private volatile boolean gameOver = false;
    private long currentTick = 0;

    // Tọa độ xuất phát chuẩn 4 góc cho tối đa 4 người chơi
    private final double[][] spawnCoords = {
            {1.0, 1.0},                                  // Player 1: Góc trên-trái
            {GameConstants.MAP_WIDTH - 2.0, 1.0},        // Player 2: Góc trên-phải
            {1.0, GameConstants.MAP_HEIGHT - 2.0},       // Player 3: Góc dưới-trái
            {GameConstants.MAP_WIDTH - 2.0, GameConstants.MAP_HEIGHT - 2.0} // Player 4
    };

    public GameEngine(int port) {
        this.map = new GameMap();
        this.server = new UdpServer(port, this::handlePacket);
    }

    public void start() throws Exception {
        server.start();

        // Bắt đầu nhịp Game Loop định kỳ (30 Ticks/giây ~ 33ms)
        gameLoopExecutor.scheduleAtFixedRate(this::tick, 0, GameConstants.TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        System.out.println("[GAME-ENGINE] Đã khởi động Game Loop thành công với tần số " + GameConstants.TICK_RATE + " Ticks/s.");
    }

    public void stop() {
        gameLoopExecutor.shutdown();
        server.stop();
        System.out.println("[GAME-ENGINE] Đã dừng Game Loop và Server.");
    }

    /**
     * Xử lý gói tin nhận từ Client.
     */
    public void handlePacket(String message, InetAddress address, int port) {
        String sessionKey = address.getHostAddress() + ":" + port;
        String[] parts = message.split("\\|", -1);
        if (parts.length == 0) return;

        String command = parts[0].trim().toUpperCase();

        switch (command) {
            case "JOIN":
                String playerName = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : "Player";
                handleJoin(playerName, address, port, sessionKey);
                break;

            case "MOVE":
                if (parts.length > 1) {
                    handleMove(parts[1].trim(), sessionKey);
                }
                break;

            case "BOMB":
                handlePlaceBomb(sessionKey);
                break;

            case "PING":
                PlayerSession pingSession = sessionsByAddress.get(sessionKey);
                if (pingSession != null) {
                    pingSession.updateHeartbeat();
                }
                break;

            case "QUIT":
                handleQuit(sessionKey);
                break;

            default:
                System.out.println("[GAME-ENGINE] Gói tin không xác định: " + message);
        }
    }

    private synchronized void handleJoin(String playerName, InetAddress address, int port, String sessionKey) {
        if (sessionsByAddress.containsKey(sessionKey)) {
            PlayerSession existing = sessionsByAddress.get(sessionKey);
            server.send(String.format("JOIN_ACK|%d|SUCCESS|Already joined as Player %d", existing.getPlayer().getId(), existing.getPlayer().getId()), address, port);
            return;
        }

        if (sessionsById.size() >= GameConstants.MAX_PLAYERS) {
            server.send("JOIN_ACK|0|FULL|Room is full (Max " + GameConstants.MAX_PLAYERS + " players)", address, port);
            return;
        }

        int id = nextPlayerId.getAndIncrement();
        int spawnIndex = (id - 1) % spawnCoords.length;
        double spawnX = spawnCoords[spawnIndex][0];
        double spawnY = spawnCoords[spawnIndex][1];

        Player newPlayer = new Player(id, playerName, spawnX, spawnY);
        PlayerSession session = new PlayerSession(newPlayer, address, port);

        sessionsByAddress.put(sessionKey, session);
        sessionsById.put(id, session);

        System.out.printf("[GAME-ENGINE] Người chơi %s (ID=%d) đã tham gia từ %s:%d tại tọa độ (%.1f, %.1f)%n",
                playerName, id, address.getHostAddress(), port, spawnX, spawnY);

        // Gửi phản hồi JOIN_ACK và khởi tạo bản đồ cho người chơi mới
        server.send(String.format("JOIN_ACK|%d|SUCCESS|Joined as %s", id, playerName), address, port);
        server.send(String.format("MAP_INIT|%d|%d|%s", map.getWidth(), map.getHeight(), map.toFlatString()), address, port);

        // Nếu phòng đạt đủ người chơi (hoặc từ 2 người trở lên) thì kích hoạt bắt đầu game
        if (!gameStarted && sessionsById.size() >= GameConstants.MAX_PLAYERS) {
            startGame();
        }
    }

    public synchronized void startGame() {
        if (gameStarted) return;
        gameStarted = true;
        gameOver = false;
        System.out.println("[GAME-ENGINE] >>> TRẬN ĐẤU CHÍNH THỨC BẮT ĐẦU VỚI " + sessionsById.size() + " NGƯỜI CHƠI! <<<");
        server.broadcast("GAME_START|" + sessionsById.size(), sessionsById.values());
    }

    private void handleMove(String dirStr, String sessionKey) {
        PlayerSession session = sessionsByAddress.get(sessionKey);
        if (session == null) return;
        session.updateHeartbeat();

        Player player = session.getPlayer();
        if (!player.isAlive()) return;

        Direction direction = Direction.fromString(dirStr);
        player.setDirection(direction);
    }

    private void handlePlaceBomb(String sessionKey) {
        PlayerSession session = sessionsByAddress.get(sessionKey);
        if (session == null) return;
        session.updateHeartbeat();

        Player player = session.getPlayer();
        if (!player.isAlive() || !player.canPlaceBomb()) return;

        int gx = player.getGridX();
        int gy = player.getGridY();

        // Kiểm tra xem ô này đã có bom hay chưa
        for (Bomb b : bombs) {
            if (b.getGridX() == gx && b.getGridY() == gy && !b.isExploded()) {
                return; // Đã có bom, không được đặt đè
            }
        }

        String bombId = "B" + bombCounter.getAndIncrement();
        Bomb bomb = new Bomb(bombId, player.getId(), gx, gy, GameConstants.BOMB_COUNTDOWN_MS, player.getBombRadius());
        bombs.add(bomb);
        player.onBombPlaced();

        System.out.printf("[GAME-ENGINE] Player %d (%s) đặt quả bom %s tại ô (%d, %d)%n",
                player.getId(), player.getName(), bombId, gx, gy);
    }

    private void handleQuit(String sessionKey) {
        PlayerSession session = sessionsByAddress.remove(sessionKey);
        if (session != null) {
            sessionsById.remove(session.getPlayer().getId());
            session.getPlayer().setAlive(false);
            System.out.printf("[GAME-ENGINE] Người chơi %s (ID=%d) đã thoát game.%n",
                    session.getPlayer().getName(), session.getPlayer().getId());
            server.broadcast("PLAYER_DIED|" + session.getPlayer().getId() + "|0", sessionsById.values());
        }
    }

    /**
     * Vòng lặp Game Loop của Server (chạy mỗi 33ms).
     */
    private void tick() {
        try {
            currentTick++;
            long now = System.currentTimeMillis();

            // 1. Kiểm tra timeout mất kết nối của client
            checkClientTimeouts(now);

            // 2. Cập nhật vị trí di chuyển các nhân vật
            updatePlayerMovement();

            // 3. Cập nhật bộ đếm thời gian của các quả bom và kích nổ
            updateBombs();

            // 4. Kiểm tra điều kiện kết thúc trận đấu (Game Over)
            if (gameStarted && !gameOver) {
                checkGameOver();
            }

            // 5. Broadcast gói tin WORLD STATE về cho toàn bộ client
            broadcastWorldState();

        } catch (Exception e) {
            System.err.println("[GAME-ENGINE] Lỗi trong Game Loop Tick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkClientTimeouts(long now) {
        List<String> timedOutKeys = new ArrayList<>();
        for (Map.Entry<String, PlayerSession> entry : sessionsByAddress.entrySet()) {
            if (entry.getValue().isTimedOut(now)) {
                timedOutKeys.add(entry.getKey());
            }
        }
        for (String key : timedOutKeys) {
            System.out.println("[GAME-ENGINE] Client bị timeout (mất kết nối): " + key);
            handleQuit(key);
        }
    }

    private void updatePlayerMovement() {
        for (PlayerSession session : sessionsById.values()) {
            Player p = session.getPlayer();
            if (!p.isAlive()) continue;

            Direction dir = p.getDirection();
            if (dir == Direction.STOP) continue;

            double step = GameConstants.PLAYER_SPEED;
            double dx = dir.getDx() * step;
            double dy = dir.getDy() * step;

            double newX = p.getX() + dx;
            double newY = p.getY() + dy;

            if (CollisionDetector.canMove(map, bombs, p.getX(), p.getY(), newX, newY)) {
                p.setX(newX);
                p.setY(newY);
            }
        }
    }

    private void updateBombs() {
        List<Bomb> explodedBombs = new ArrayList<>();
        List<Player> allPlayers = new ArrayList<>();
        for (PlayerSession s : sessionsById.values()) {
            allPlayers.add(s.getPlayer());
        }

        for (Bomb bomb : bombs) {
            if (bomb.update(GameConstants.TICK_INTERVAL_MS)) {
                explodedBombs.add(bomb);
            }
        }

        for (Bomb bomb : explodedBombs) {
            // Xử lý nổ
            ExplosionHandler.ExplosionResult result = ExplosionHandler.processExplosion(map, bombs, allPlayers, bomb);

            // Hoàn lại số lượng bom cho chủ nhân
            PlayerSession ownerSession = sessionsById.get(bomb.getOwnerId());
            if (ownerSession != null) {
                ownerSession.getPlayer().onBombExploded();
            }

            // Xây dựng chuỗi danh sách các ô lửa nổ: x1,y1;x2,y2;...
            StringBuilder flameSb = new StringBuilder();
            for (int i = 0; i < result.flameCells.size(); i++) {
                Point pt = result.flameCells.get(i);
                flameSb.append(pt.x).append(",").append(pt.y);
                if (i < result.flameCells.size() - 1) {
                    flameSb.append(";");
                }
            }
            server.broadcast(String.format("EXPLODE|%s|%s", bomb.getId(), flameSb), sessionsById.values());

            // Broadcast các ô gạch vỡ biến thành ô trống
            for (Point brick : result.destroyedBricks) {
                server.broadcast(String.format("TILE_UPDATE|%d|%d|%d", brick.x, brick.y, TileType.EMPTY.getCode()), sessionsById.values());
            }

            // Broadcast thông báo người chơi bị tiêu diệt
            for (Player deadPlayer : result.killedPlayers) {
                System.out.printf("[GAME-ENGINE] Người chơi %s (ID=%d) đã bị tiêu diệt bởi bom của Player %d!%n",
                        deadPlayer.getName(), deadPlayer.getId(), bomb.getOwnerId());
                server.broadcast(String.format("PLAYER_DIED|%d|%d", deadPlayer.getId(), bomb.getOwnerId()), sessionsById.values());
            }
        }

        // Loại bỏ các quả bom đã nổ xong khỏi danh sách
        bombs.removeIf(Bomb::isExploded);
    }

    private void checkGameOver() {
        int aliveCount = 0;
        Player lastSurvivor = null;

        for (PlayerSession session : sessionsById.values()) {
            if (session.getPlayer().isAlive()) {
                aliveCount++;
                lastSurvivor = session.getPlayer();
            }
        }

        if (aliveCount <= 1 && sessionsById.size() > 1) {
            gameOver = true;
            if (lastSurvivor != null) {
                System.out.printf("[GAME-ENGINE] >>> TRẬN ĐẤU KẾT THÚC! NGƯỜI CHIẾN THẮNG LÀ: %s (ID=%d) <<<%n",
                        lastSurvivor.getName(), lastSurvivor.getId());
                server.broadcast(String.format("GAME_OVER|%d|%s", lastSurvivor.getId(), lastSurvivor.getName()), sessionsById.values());
            } else {
                System.out.println("[GAME-ENGINE] >>> TRẬN ĐẤU KẾT THÚC HÒA! KHÔNG CÒN AI SỐNG SÓT! <<<");
                server.broadcast("GAME_OVER|0|NONE", sessionsById.values());
            }
        }
    }

    private void broadcastWorldState() {
        if (sessionsById.isEmpty()) return;

        // Xây dựng chuỗi players_data: id,x,y,alive,score,direction;...
        StringBuilder pSb = new StringBuilder();
        int pIndex = 0;
        for (PlayerSession session : sessionsById.values()) {
            pSb.append(session.getPlayer().toStateString());
            if (pIndex < sessionsById.size() - 1) {
                pSb.append(";");
            }
            pIndex++;
        }

        // Xây dựng chuỗi bombs_data: bombId,gridX,gridY,countdownMs;...
        StringBuilder bSb = new StringBuilder();
        if (bombs.isEmpty()) {
            bSb.append("NONE");
        } else {
            for (int i = 0; i < bombs.size(); i++) {
                bSb.append(bombs.get(i).toStateString());
                if (i < bombs.size() - 1) {
                    bSb.append(";");
                }
            }
        }

        String statePacket = String.format("STATE|%d|%s|%s", currentTick, pSb, bSb);
        server.broadcast(statePacket, sessionsById.values());
    }

    public GameMap getMap() {
        return map;
    }

    public Map<Integer, PlayerSession> getSessionsById() {
        return Collections.unmodifiableMap(sessionsById);
    }

    public boolean isGameStarted() {
        return gameStarted;
    }
}
