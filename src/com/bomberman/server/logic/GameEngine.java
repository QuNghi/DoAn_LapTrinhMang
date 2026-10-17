package com.bomberman.server.logic;

import com.bomberman.common.Direction;
import com.bomberman.common.GameConstants;
import com.bomberman.common.ItemType;
import com.bomberman.common.TileType;
import com.bomberman.server.model.Bomb;
import com.bomberman.server.model.GameMap;
import com.bomberman.server.model.Item;
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
    private final List<Item> items = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService gameLoopExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);
    private final AtomicInteger bombCounter = new AtomicInteger(1);
    private final AtomicInteger itemCounter = new AtomicInteger(1);

    // 0=WAITING (chờ start/chờ đủ 4 người), 1=COUNTDOWN (đếm ngược 3s), 2=PLAYING, 3=ENDED
    private volatile int gameState = 0;
    private volatile boolean gameStarted = false;
    private volatile boolean gameOver = false;
    private long currentTick = 0;
    private long seqNumber = 0; // Số thứ tự của STATE packet

    // Tọa độ xuất phát chuẩn 4 góc cho tối đa 4 người chơi
    private final double[][] spawnCoords = {
            {1.0, 1.0},                                  // Player 1: Góc trên-trái
            {GameConstants.MAP_WIDTH - 2.0, 1.0},        // Player 2: Góc trên-phải
            {1.0, GameConstants.MAP_HEIGHT - 2.0},       // Player 3: Góc dưới-trái
            {GameConstants.MAP_WIDTH - 2.0, GameConstants.MAP_HEIGHT - 2.0} // Player 4
    };

    private final String roomCode;
    private final boolean standaloneServer;

    public GameEngine(int port) {
        this.roomCode = "MAIN";
        this.standaloneServer = true;
        this.map = new GameMap();
        this.server = new UdpServer(port, this::handlePacket);
    }

    public GameEngine(UdpServer server, String roomCode) {
        this.roomCode = roomCode;
        this.standaloneServer = false;
        this.map = new GameMap();
        this.server = server;
    }

    public void start() throws Exception {
        if (standaloneServer) {
            server.start();
        }

        // Bắt đầu nhịp Game Loop định kỳ (30 Ticks/giây ~ 33ms)
        gameLoopExecutor.scheduleAtFixedRate(this::tick, 0, GameConstants.TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        System.out.println("[GAME-ENGINE] [" + roomCode + "] Đã khởi động Game Loop thành công với tần số " + GameConstants.TICK_RATE + " Ticks/s.");
    }

    public void stop() {
        gameLoopExecutor.shutdown();
        if (standaloneServer) {
            server.stop();
        }
        System.out.println("[GAME-ENGINE] [" + roomCode + "] Đã dừng Game Loop.");
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

            case "USE_WEAPON":
                handleUseWeapon(sessionKey);
                break;

            case "ACTION":
                handleAction(sessionKey);
                break;

            case "PING":
                PlayerSession pingSession = sessionsByAddress.get(sessionKey);
                if (pingSession != null) {
                    pingSession.updateHeartbeat();
                }
                break;

            case "QUIT":
            case "LEAVE_ROOM":
                handleQuit(sessionKey);
                break;

            case "START_GAME":
                handleStartGame(sessionKey);
                break;

            case "RESTART_VOTE":
                if (parts.length > 1) handleRestartVote(sessionKey, parts[1].trim().toUpperCase());
                break;

            default:
                System.out.println("[GAME-ENGINE] [" + roomCode + "] Gói tin không xác định: " + message);
        }
    }

    /** Phím Action: có súng thì bắn, không có thì đặt bom */
    private void handleAction(String sessionKey) {
        PlayerSession session = sessionsByAddress.get(sessionKey);
        if (session == null) return;
        if (session.getPlayer().hasWeapon()) {
            handleUseWeapon(sessionKey);
        } else {
            handlePlaceBomb(sessionKey);
        }
    }

    /** Host yêu cầu bắt đầu trận */
    public synchronized void handleStartGame(String sessionKey) {
        PlayerSession session = sessionsByAddress.get(sessionKey);
        if (session == null) return;
        if (gameState != 0) {
            System.out.println("[GAME-ENGINE] START_GAME: game đã bắt đầu hoặc không ở trạng thái WAITING.");
            return;
        }
        if (!session.isHost()) {
            server.send("ERROR|Chỉ chủ phòng mới có thể bắt đầu trận đấu!", session.getAddress(), session.getPort());
            return;
        }
        if (sessionsById.size() < GameConstants.MIN_PLAYERS_TO_START) {
            server.send("ERROR|Chưa đủ " + GameConstants.MIN_PLAYERS_TO_START + " người chơi!", session.getAddress(), session.getPort());
            return;
        }
        System.out.println("[GAME-ENGINE] Host " + session.getPlayer().getName() + " bắt đầu trận!");
        startGame();
    }

    /** Xử lý vote restart sau GAME_OVER */
    private synchronized void handleRestartVote(String sessionKey, String vote) {
        if (gameState != 3) return; // Chỉ chấp nhận sau khi ENDED
        PlayerSession session = sessionsByAddress.get(sessionKey);
        if (session == null) return;
        session.setRestartVote("YES".equals(vote));
        System.out.printf("[GAME-ENGINE] Player %d vote restart: %s%n", session.getPlayer().getId(), vote);

        // Đếm vote
        int yesCount = 0; int totalCount = sessionsById.size();
        for (PlayerSession ps : sessionsById.values()) {
            if (Boolean.TRUE.equals(ps.getRestartVote())) yesCount++;
        }
        // Broadcast trạng thái vote
        server.broadcast("RESTART_VOTE_STATUS|" + yesCount + "|" + totalCount, sessionsById.values());

        if ("NO".equals(vote)) {
            // Người NO → remove khỏi room, về lobby
            sessionsById.remove(session.getPlayer().getId());
            sessionsByAddress.remove(sessionKey);
            server.send("QUIT_ACK|OK", session.getAddress(), session.getPort());
        }

        // Kiểm tra: tất cả đã vote chưa?
        int pendingVotes = 0;
        for (PlayerSession ps : sessionsById.values()) {
            if (ps.getRestartVote() == null) pendingVotes++;
        }
        if (pendingVotes == 0) {
            // Đếm lại YES sau khi loại NO
            int finalYes = 0;
            for (PlayerSession ps : sessionsById.values()) {
                if (Boolean.TRUE.equals(ps.getRestartVote())) finalYes++;
            }
            if (finalYes >= GameConstants.MIN_PLAYERS_TO_START) {
                // Restart game
                System.out.println("[GAME-ENGINE] Chơi lại! " + finalYes + " người đồng ý.");
                server.broadcast("RESTART_ACK|ACCEPTED", sessionsById.values());
                resetGame();
            } else {
                // Không đủ người chơi, tất cả về lobby
                System.out.println("[GAME-ENGINE] Không đủ người chơi lại, tất cả về lobby.");
                server.broadcast("RESTART_ACK|REJECTED", sessionsById.values());
                sessionsById.clear();
                sessionsByAddress.clear();
                resetGame();
            }
        }
    }

    /** Reset lại game để chơi trận mới */
    private synchronized void resetGame() {
        gameOver = false;
        gameStarted = false;
        gameState = 0;
        currentTick = 0;
        seqNumber = 0;
        bombs.clear();
        items.clear();
        map.regenerate();
        // Reset vote
        for (PlayerSession ps : sessionsById.values()) {
            ps.setRestartVote(null);
        }
        // Reset vị trí spawn người chơi
        int idx = 0;
        for (PlayerSession ps : sessionsById.values()) {
            int spawnIdx = idx % spawnCoords.length;
            ps.getPlayer().resetForNewGame(spawnCoords[spawnIdx][0], spawnCoords[spawnIdx][1]);
            idx++;
        }
        // Gửi MAP_INIT mới cho tất cả
        for (PlayerSession ps : sessionsById.values()) {
            server.send(String.format("MAP_INIT|%d|%d|%s", map.getWidth(), map.getHeight(), map.toFlatString()),
                    ps.getAddress(), ps.getPort());
        }
        // Bắt đầu AUTO nếu đủ người chơi
        if (sessionsById.size() >= GameConstants.MIN_PLAYERS_TO_START) {
            startGame();
        }
    }

    public synchronized void handleJoin(String playerName, java.net.InetAddress address, int port, String sessionKey) {
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
        session.setRoomCode(roomCode);
        if (sessionsById.isEmpty()) {
            session.setHost(true);
        }

        sessionsByAddress.put(sessionKey, session);
        sessionsById.put(id, session);

        System.out.printf("[GAME-ENGINE] [%s] Người chơi %s (ID=%d, Host=%b) đã tham gia từ %s:%d tại tọa độ (%.1f, %.1f)%n",
                roomCode, playerName, id, session.isHost(), address.getHostAddress(), port, spawnX, spawnY);

        // Gửi phản hồi JOIN_ACK và khởi tạo bản đồ cho người chơi mới
        server.send(String.format("JOIN_ACK|%d|SUCCESS|Joined as %s", id, playerName), address, port);
        server.send(String.format("MAP_INIT|%d|%d|%s", map.getWidth(), map.getHeight(), map.toFlatString()), address, port);

        if (session.isHost()) {
            server.send(String.format("ROOM_CREATED|%s|%d", roomCode, id), address, port);
        } else {
            server.send(String.format("ROOM_JOINED|%s|%d|%d", roomCode, getHostPlayerId(), sessionsById.size()), address, port);
        }

        // Cập nhật danh sách người chơi trong phòng cho mọi người
        broadcastRoomPlayers();

        // Gửi các vật phẩm đang có sẵn trên sân đấu
        for (Item item : items) {
            server.send(String.format("ITEM_SPAWN|%s|%d|%d|%d",
                    item.getId(), item.getType().getId(), item.getGridX(), item.getGridY()), address, port);
        }

        // Nếu phòng đạt đủ 4 người chơi thì kích hoạt bắt đầu game
        if (gameState == 0 && sessionsById.size() >= GameConstants.MAX_PLAYERS) {
            startGame();
        }
    }

    public synchronized void startGame() {
        if (gameState != 0) return; // Chỉ bắt đầu khi đang ở WAITING
        gameState = 1; // COUNTDOWN
        gameOver = false;
        System.out.println("[GAME-ENGINE] >>> BẮT ĐẦU ĐẾM NGƯỢC 3 GIÂY TRƯỚC TRẬN ĐẤU! <<<");

        // Broadcast đếm ngược ban đầu
        server.broadcast("COUNTDOWN|3", sessionsById.values());

        ScheduledExecutorService countdownExec = Executors.newSingleThreadScheduledExecutor();
        final int[] remainingSeconds = {2};

        countdownExec.scheduleAtFixedRate(() -> {
            try {
                if (remainingSeconds[0] > 0) {
                    server.broadcast("COUNTDOWN|" + remainingSeconds[0], sessionsById.values());
                    System.out.printf("[GAME-ENGINE] Đếm ngược: %d...%n", remainingSeconds[0]);
                    remainingSeconds[0]--;
                } else {
                    // Hết 3 giây -> Chính thức START!
                    server.broadcast("COUNTDOWN|0|START", sessionsById.values());
                    server.broadcast("GAME_START|" + sessionsById.size(), sessionsById.values());
                    gameState = 2; // PLAYING
                    gameStarted = true;
                    System.out.println("[GAME-ENGINE] >>> TRẬN ĐẤU CHÍNH THỨC BẮT ĐẦU! (CHO PHÉP DI CHUYỂN & ĐẶT BOM) <<<");
                    countdownExec.shutdown();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void handleMove(String dirStr, String sessionKey) {
        if (gameState != 2) return; // Chỉ nhận lệnh di chuyển khi trận đấu đang diễn ra (PLAYING)

        PlayerSession session = sessionsByAddress.get(sessionKey);
        if (session == null) return;
        session.updateHeartbeat();

        Player player = session.getPlayer();
        if (!player.isAlive()) return;

        Direction direction = Direction.fromString(dirStr);
        if (direction != player.getDirection()) {
            player.setDirection(direction);
            System.out.printf("[GAME-ENGINE] Player %d (%s) di chuyển: %s | Tọa độ hiện tại: (%.2f, %.2f)%n",
                    player.getId(), player.getName(), direction, player.getX(), player.getY());
        }
    }

    private void handlePlaceBomb(String sessionKey) {
        if (gameState != 2) return; // Chỉ nhận lệnh đặt bom khi trận đấu đang diễn ra (PLAYING)

        PlayerSession session = sessionsByAddress.get(sessionKey);
        if (session == null) return;
        session.updateHeartbeat();

        Player player = session.getPlayer();
        if (!player.isAlive() || !player.canPlaceBomb()) return;

        // Spam bomb protection: không cho đặt 2 bom trong cùng 1 tick
        if (player.getLastBombTick() == currentTick) {
            return;
        }
        player.setLastBombTick(currentTick);

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

        System.out.printf("[GAME-ENGINE] Player %d (%s) đặt quả bom %s tại ô (%d, %d) | Còn %d/%d quả có thể đặt%n",
                player.getId(), player.getName(), bombId, gx, gy, player.getAvailableBombs(), player.getMaxBombs());
    }

    public void handleQuit(String sessionKey) {
        PlayerSession session = sessionsByAddress.remove(sessionKey);
        if (session != null) {
            sessionsById.remove(session.getPlayer().getId());
            session.getPlayer().setAlive(false);
            System.out.printf("[GAME-ENGINE] Người chơi %s (ID=%d) đã thoát game.%n",
                    session.getPlayer().getName(), session.getPlayer().getId());
            server.broadcast("PLAYER_LEFT|" + session.getPlayer().getId() + "|" + session.getPlayer().getName(), sessionsById.values());
            server.broadcast("PLAYER_DIED|" + session.getPlayer().getId() + "|0", sessionsById.values());

            // Chuyển host ngẫu nhiên cho người còn lại nếu host rời phòng
            if (session.isHost() && !sessionsById.isEmpty()) {
                List<PlayerSession> remaining = new ArrayList<>(sessionsById.values());
                PlayerSession newHost = remaining.get(new java.util.Random().nextInt(remaining.size()));
                newHost.setHost(true);
                server.broadcast("PLAYER_HOST_CHANGED|" + newHost.getPlayer().getId(), sessionsById.values());
                System.out.printf("[GAME-ENGINE] Host cũ đã rời, chuyển host ngẫu nhiên cho Player %d (%s)%n",
                        newHost.getPlayer().getId(), newHost.getPlayer().getName());
            }

            broadcastRoomPlayers();

            if (gameState == 2 && !gameOver) {
                checkGameOver();
            }
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

            // 3. Kiểm tra nhặt vật phẩm trên bản đồ
            checkItemPickups();

            // 4. Cập nhật bộ đếm thời gian của các quả bom và kích nổ
            updateBombs();

            // 5. Kiểm tra điều kiện kết thúc trận đấu (Game Over)
            if (gameStarted && !gameOver) {
                checkGameOver();
            }

            // 6. Broadcast gói tin WORLD STATE về cho toàn bộ client
            broadcastWorldState();

        } catch (Exception e) {
            System.err.println("[GAME-ENGINE] Lỗi trong Game Loop Tick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkClientTimeouts(long now) {
        List<String> timedOutKeys = new ArrayList<>();
        for (Map.Entry<String, PlayerSession> entry : sessionsByAddress.entrySet()) {
            PlayerSession ps = entry.getValue();
            // Disconnect timeout 5s trong trận (PLAYING): force kill player sau 5s
            if (gameState == 2) {
                long offlineMs = now - ps.getLastHeartbeat();
                if (offlineMs >= GameConstants.DISCONNECT_TIMEOUT_GAME_MS) {
                    Player p = ps.getPlayer();
                    if (p.isAlive()) {
                        p.setAlive(false);
                        System.out.printf("[GAME-ENGINE] Player %d (%s) mất kết nối quá 5s trong trận → Tự thua!%n",
                                p.getId(), p.getName());
                        server.broadcast(String.format("PLAYER_DIED|%d|0", p.getId()), sessionsById.values());
                        checkGameOver();
                    }
                }
            }
            if (ps.isTimedOut(now)) {
                timedOutKeys.add(entry.getKey());
            }
        }
        for (String key : timedOutKeys) {
            System.out.println("[GAME-ENGINE] Client bị timeout (mất kết nối): " + key);
            handleQuit(key);
        }
    }

    private void updatePlayerMovement() {
        if (gameState != 2) return; // Chỉ cập nhật di chuyển khi đang PLAYING
        for (PlayerSession session : sessionsById.values()) {
            Player p = session.getPlayer();
            if (!p.isAlive()) continue;

            Direction dir = p.getDirection();
            if (dir == Direction.STOP) continue;

            // Sử dụng tốc độ di chuyển hiện tại của người chơi (đã cộng buff nếu có)
            double step = p.getSpeed();
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

    /**
     * Kiểm tra khi người chơi bước vào ô có chứa vật phẩm buff.
     */
    private void checkItemPickups() {
        for (PlayerSession session : sessionsById.values()) {
            Player player = session.getPlayer();
            if (!player.isAlive()) continue;

            int px = player.getGridX();
            int py = player.getGridY();

            for (Item item : items) {
                if (item.getGridX() == px && item.getGridY() == py) {
                    boolean applied = player.applyItem(item.getType());
                    items.remove(item);

                    System.out.printf("[GAME-ENGINE] Player %d (%s) nhặt vật phẩm %s (%s). Kích hoạt=%b%n",
                            player.getId(), player.getName(), item.getId(), item.getType().getDisplayName(), applied);

                    // Broadcast sự kiện nhặt vật phẩm cho các client
                    server.broadcast(String.format("ITEM_COLLECT|%s|%d|%d|%s",
                            item.getId(), player.getId(), item.getType().getId(), item.getType().name()), sessionsById.values());
                    break;
                }
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

        // Duyệt danh sách các quả bom phát nổ (hỗ trợ nổ dây chuyền cascade)
        for (int bIdx = 0; bIdx < explodedBombs.size(); bIdx++) {
            Bomb bomb = explodedBombs.get(bIdx);

            // Xử lý nổ (bao gồm rơi vật phẩm, phá hủy gạch, phá khiên / diệt player, cháy vật phẩm trên sàn)
            ExplosionHandler.ExplosionResult result = ExplosionHandler.processExplosion(
                    map, bombs, allPlayers, items, bomb, itemCounter);

            // Nếu có bom khác bị nổ dây chuyền, thêm vào hàng đợi nổ ngay trong tick này
            for (Bomb triggered : result.triggeredBombs) {
                if (!explodedBombs.contains(triggered)) {
                    explodedBombs.add(triggered);
                }
            }

            // Hoàn lại số lượng bom cho chủ nhân
            PlayerSession ownerSession = sessionsById.get(bomb.getOwnerId());
            if (ownerSession != null) {
                ownerSession.getPlayer().onBombExploded();
                System.out.printf("[GAME-ENGINE] Quả bom %s nổ -> Hoàn lại 1 bom cho Player %d (%s) (Hiện có: %d/%d quả có thể đặt)%n",
                        bomb.getId(), ownerSession.getPlayer().getId(), ownerSession.getPlayer().getName(),
                        ownerSession.getPlayer().getAvailableBombs(), ownerSession.getPlayer().getMaxBombs());
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

            // Xử lý và broadcast các vật phẩm mới rơi ra
            for (Item spawnedItem : result.spawnedItems) {
                items.add(spawnedItem);
                System.out.printf("[GAME-ENGINE] Gạch vỡ làm rơi vật phẩm: %s (%s) tại (%d, %d)%n",
                        spawnedItem.getId(), spawnedItem.getType().getDisplayName(), spawnedItem.getGridX(), spawnedItem.getGridY());
                server.broadcast(String.format("ITEM_SPAWN|%s|%d|%d|%d",
                        spawnedItem.getId(), spawnedItem.getType().getId(), spawnedItem.getGridX(), spawnedItem.getGridY()), sessionsById.values());
            }

            // Xử lý các vật phẩm bị lửa bom thiêu hủy
            for (Item burntItem : result.incineratedItems) {
                items.remove(burntItem);
                System.out.printf("[GAME-ENGINE] Vật phẩm %s tại (%d, %d) bị lửa thiêu cháy!%n",
                        burntItem.getId(), burntItem.getGridX(), burntItem.getGridY());
                server.broadcast(String.format("ITEM_DESTROYED|%s", burntItem.getId()), sessionsById.values());
            }

            // Broadcast thông báo khiên bảo vệ thành công cứu người chơi khỏi chết
            for (Player savedPlayer : result.shieldSavedPlayers) {
                System.out.printf("[GAME-ENGINE] Khiên của %s (ID=%d) đã vỡ để hấp thụ sát thương!%n",
                        savedPlayer.getName(), savedPlayer.getId());
                server.broadcast(String.format("SHIELD_BROKEN|%d|%s", savedPlayer.getId(), savedPlayer.getName()), sessionsById.values());
            }

            // Broadcast thông báo người chơi bị tiêu diệt
            for (Player deadPlayer : result.killedPlayers) {
                PlayerSession killerSession = sessionsById.get(bomb.getOwnerId());
                if (killerSession != null && deadPlayer.getId() != bomb.getOwnerId()) {
                    killerSession.getPlayer().addKill();
                }
                System.out.printf("[GAME-ENGINE] Người chơi %s (ID=%d) đã bị tiêu diệt bởi bom của Player %d!%n",
                        deadPlayer.getName(), deadPlayer.getId(), bomb.getOwnerId());
                server.broadcast(String.format("PLAYER_DIED|%d|%d", deadPlayer.getId(), bomb.getOwnerId()), sessionsById.values());
            }
        }

        // Loại bỏ các quả bom đã nổ xong khỏi danh sách
        bombs.removeIf(Bomb::isExploded);
    }

    /**
     * Xử lý khi người chơi sử dụng vũ khí (Súng Laser / Rocket như game Bomb IT).
     */
    private void handleUseWeapon(String sessionKey) {
        PlayerSession session = sessionsByAddress.get(sessionKey);
        if (session == null) return;
        session.updateHeartbeat();

        Player shooter = session.getPlayer();
        if (!shooter.isAlive() || shooter.getWeapon() == null || shooter.getWeaponAmmo() <= 0) {
            return;
        }

        boolean used = shooter.useWeapon();
        if (!used) return;

        Direction dir = shooter.getDirection() != Direction.STOP ? shooter.getDirection() : shooter.getLastFacingDirection();
        int cx = shooter.getGridX();
        int cy = shooter.getGridY();

        List<Point> trajectory = new ArrayList<>();
        int hitType = 0; // 0 = hụt, 1 = trúng tường cứng, 2 = trúng gạch, 3 = trúng bom, 4 = trúng người chơi
        int hitTargetId = 0;

        int maxRange = 12;
        for (int step = 1; step <= maxRange; step++) {
            int nx = cx + dir.getDx() * step;
            int ny = cy + dir.getDy() * step;

            if (nx < 0 || nx >= map.getWidth() || ny < 0 || ny >= map.getHeight()) {
                break;
            }

            trajectory.add(new Point(nx, ny));

            // 1. Kiểm tra trúng người chơi khác
            boolean hitPlayer = false;
            for (PlayerSession otherSession : sessionsById.values()) {
                Player target = otherSession.getPlayer();
                if (target.getId() != shooter.getId() && target.isAlive() && target.getGridX() == nx && target.getGridY() == ny) {
                    hitType = 4;
                    hitTargetId = target.getId();
                    boolean killed = target.takeDamage();
                    if (killed) {
                        shooter.addKill();
                        System.out.printf("[GAME-ENGINE] Player %d dùng súng bắn hạ Player %d!%n", shooter.getId(), target.getId());
                        server.broadcast(String.format("PLAYER_DIED|%d|%d", target.getId(), shooter.getId()), sessionsById.values());
                    } else {
                        System.out.printf("[GAME-ENGINE] Khiên của Player %d đã đỡ được phát bắn từ Player %d!%n", target.getId(), shooter.getId());
                        server.broadcast(String.format("SHIELD_BROKEN|%d|%s", target.getId(), target.getName()), sessionsById.values());
                    }
                    hitPlayer = true;
                    break;
                }
            }
            if (hitPlayer) break;

            // 2. Kiểm tra trúng tường cứng
            if (map.getTile(nx, ny) == TileType.HARD_WALL.getCode()) {
                hitType = 1;
                break;
            }

            // 3. Kiểm tra trúng gạch mềm (phá hủy gạch)
            if (map.getTile(nx, ny) == TileType.SOFT_BRICK.getCode()) {
                hitType = 2;
                map.destroyBrick(nx, ny);
                server.broadcast(String.format("TILE_UPDATE|%d|%d|%d", nx, ny, TileType.EMPTY.getCode()), sessionsById.values());

                if (Math.random() < GameConstants.ITEM_DROP_RATE) {
                    ItemType droppedType = ExplosionHandler.randomItemType();
                    Item newItem = new Item("IT" + itemCounter.getAndIncrement(), droppedType, nx, ny);
                    items.add(newItem);
                    server.broadcast(String.format("ITEM_SPAWN|%s|%d|%d|%d",
                            newItem.getId(), droppedType.getId(), nx, ny), sessionsById.values());
                }
                break;
            }

            // 4. Kiểm tra trúng bom (kích nổ bom ngay lập tức)
            boolean hitBomb = false;
            for (Bomb b : bombs) {
                if (!b.isExploded() && b.getGridX() == nx && b.getGridY() == ny) {
                    hitType = 3;
                    b.triggerExplosion();
                    hitBomb = true;
                    break;
                }
            }
            if (hitBomb) break;
        }

        // Đóng gói đường bay của đạn x1,y1;x2,y2
        StringBuilder pathSb = new StringBuilder();
        for (int i = 0; i < trajectory.size(); i++) {
            Point pt = trajectory.get(i);
            pathSb.append(pt.x).append(",").append(pt.y);
            if (i < trajectory.size() - 1) pathSb.append(";");
        }

        System.out.printf("[GAME-ENGINE] Player %d (%s) bắn vũ khí! Còn %d lượt. Trúng loại: %d (Mục tiêu: %d)%n",
                shooter.getId(), shooter.getName(), shooter.getWeaponAmmo(), hitType, hitTargetId);

        server.broadcast(String.format("WEAPON_USED|%d|%s|%d|%s|%d|%d",
                shooter.getId(), ItemType.WEAPON_GUN.name(), shooter.getWeaponAmmo(),
                pathSb.length() > 0 ? pathSb.toString() : "NONE", hitType, hitTargetId), sessionsById.values());

        // Broadcast BULLET_START cho Client animate viên đạn
        int endX = trajectory.isEmpty() ? (cx + dir.getDx()) : trajectory.get(trajectory.size() - 1).x;
        int endY = trajectory.isEmpty() ? (cy + dir.getDy()) : trajectory.get(trajectory.size() - 1).y;
        server.broadcast(String.format(java.util.Locale.US, "BULLET_START|%d|%d|%d|%d|%d",
                shooter.getId(), cx, cy, endX, endY), sessionsById.values());
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
            gameState = 3; // ENDED: Dừng nhận gói tin từ client
            int winnerId = (lastSurvivor != null) ? lastSurvivor.getId() : 0;
            if (lastSurvivor != null) {
                System.out.printf("[GAME-ENGINE] >>> TRẬN ĐẤU KẾT THÚC! NGƯỜI CHIẾN THẮNG LÀ: %s (ID=%d) <<<%n",
                        lastSurvivor.getName(), lastSurvivor.getId());
                // Gửi thông tin đầy đủ về người thắng cuộc và vật phẩm nhặt được
                server.broadcast(String.format("GAME_OVER|%d|%s|%s",
                        lastSurvivor.getId(), lastSurvivor.getName(), lastSurvivor.toItemsSummaryString()), sessionsById.values());
            } else {
                System.out.println("[GAME-ENGINE] >>> TRẬN ĐẤU KẾT THÚC HÒA! KHÔNG CÒN AI SỐNG SÓT! <<<");
                server.broadcast("GAME_OVER|0|NONE|0,0,0,0,0", sessionsById.values());
            }
            // Gửi yêu cầu vote restart cho tất cả client
            server.broadcast(String.format("RESTART_VOTE_REQ|%d", winnerId), sessionsById.values());
        }
    }

    private void broadcastWorldState() {
        if (sessionsById.isEmpty()) return;

        // Xây dựng chuỗi players_data: id,x,y,alive,score,direction,hasShield,weaponAmmo;...
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

        // Xây dựng chuỗi items_data: itemId,itemTypeId,gridX,gridY;...
        StringBuilder iSb = new StringBuilder();
        if (items.isEmpty()) {
            iSb.append("NONE");
        } else {
            for (int i = 0; i < items.size(); i++) {
                iSb.append(items.get(i).toStateString());
                if (i < items.size() - 1) {
                    iSb.append(";");
                }
            }
        }

        String statePacket = String.format("STATE|%d|%d|%d|%s|%s|%s",
                seqNumber++ % GameConstants.SEQUENCE_NUMBER_MOD,
                System.currentTimeMillis(), currentTick, pSb, bSb, iSb);
        server.broadcast(statePacket, sessionsById.values());
    }

    /**
     * Broadcast danh sách người chơi trong phòng cho toàn bộ client trong phòng.
     */
    public void broadcastRoomPlayers() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (PlayerSession s : sessionsById.values()) {
            sb.append(s.getPlayer().getName());
            if (s.isHost()) sb.append(" [HOST]");
            if (i < sessionsById.size() - 1) sb.append(";");
            i++;
        }
        server.broadcast("ROOM_PLAYERS|" + sb.toString(), sessionsById.values());
    }

    public int getHostPlayerId() {
        for (PlayerSession s : sessionsById.values()) {
            if (s.isHost()) return s.getPlayer().getId();
        }
        return sessionsById.isEmpty() ? 0 : sessionsById.values().iterator().next().getPlayer().getId();
    }

    public int getGameState() {
        return gameState;
    }

    public String getRoomCode() {
        return roomCode;
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
