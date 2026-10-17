package com.bomberman.server;

import com.bomberman.common.GameConstants;
import com.bomberman.server.model.Player;
import com.bomberman.server.model.Room;
import com.bomberman.server.network.PlayerSession;
import com.bomberman.server.network.UdpServer;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý nhiều phòng chơi đồng thời trong hệ thống Multi-Room.
 * Chịu trách nhiệm phân phối gói tin UDP tới đúng GameEngine của từng phòng.
 */
public class RoomManager {
    public interface RoomChangeListener {
        void onRoomsChanged();
    }

    private final UdpServer server;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoomCode = new ConcurrentHashMap<>();
    private final List<RoomChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final SecureRandom random = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Loại bỏ I, O, 0, 1 tránh nhầm lẫn

    public RoomManager(UdpServer server) {
        this.server = server;
        this.server.setListener(this::handlePacket);
    }

    public void addListener(RoomChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RoomChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (RoomChangeListener listener : listeners) {
            try {
                listener.onRoomsChanged();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized String generateRoomCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(GameConstants.ROOM_CODE_LENGTH);
            for (int i = 0; i < GameConstants.ROOM_CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (rooms.containsKey(code));
        return code;
    }

    /**
     * Tạo một phòng trống thủ công (từ ServerFrame GUI hoặc Console).
     */
    public synchronized Room createEmptyRoom() {
        String code = generateRoomCode();
        Room room = new Room(code, server);
        rooms.put(code, room);
        System.out.println("[ROOM-MANAGER] Tạo phòng trống thủ công: [" + code + "]");
        notifyListeners();
        return room;
    }

    /**
     * Tạo một phòng chơi mới với người tạo là Host.
     */
    public synchronized Room createRoom(String playerName, InetAddress address, int port) {
        String sessionKey = address.getHostAddress() + ":" + port;
        // Nếu client đã ở trong phòng khác, rời phòng đó trước
        leaveRoom(sessionKey);

        String code = generateRoomCode();
        Room room = new Room(code, server);
        rooms.put(code, room);
        sessionToRoomCode.put(sessionKey, code);

        System.out.printf("[ROOM-MANAGER] Tạo phòng mới [%s] bởi %s (%s:%d)%n",
                code, playerName, address.getHostAddress(), port);

        room.getEngine().handleJoin(playerName, address, port, sessionKey);
        notifyListeners();
        return room;
    }

    /**
     * Tham gia phòng theo mã code 4 ký tự.
     */
    public synchronized boolean joinRoom(String roomCode, String playerName, InetAddress address, int port) {
        String sessionKey = address.getHostAddress() + ":" + port;
        leaveRoom(sessionKey);

        String code = roomCode.trim().toUpperCase();
        Room room = rooms.get(code);

        if (room == null) {
            server.send("ERROR|Mã phòng " + code + " không tồn tại!", address, port);
            return false;
        }

        if (room.getGameState() != 0) {
            server.send("ERROR|Phòng " + code + " đã bắt đầu trận đấu!", address, port);
            return false;
        }

        if (room.getPlayerCount() >= GameConstants.MAX_PLAYERS) {
            server.send("ERROR|Phòng " + code + " đã đủ " + GameConstants.MAX_PLAYERS + " người!", address, port);
            return false;
        }

        sessionToRoomCode.put(sessionKey, code);
        room.getEngine().handleJoin(playerName, address, port, sessionKey);
        notifyListeners();
        return true;
    }

    /**
     * Kết nối nhanh: tìm phòng đang chờ có chỗ trống, nếu không có thì tự tạo phòng mới.
     */
    public synchronized void quickJoin(String playerName, InetAddress address, int port) {
        String sessionKey = address.getHostAddress() + ":" + port;
        leaveRoom(sessionKey);

        for (Room room : rooms.values()) {
            if (room.getGameState() == 0 && room.getPlayerCount() < GameConstants.MAX_PLAYERS) {
                sessionToRoomCode.put(sessionKey, room.getRoomCode());
                room.getEngine().handleJoin(playerName, address, port, sessionKey);
                notifyListeners();
                return;
            }
        }

        // Không có phòng nào sẵn sàng → Tạo phòng mới
        createRoom(playerName, address, port);
    }

    /**
     * Xử lý khi người chơi rời phòng.
     */
    public synchronized void leaveRoom(String sessionKey) {
        String code = sessionToRoomCode.remove(sessionKey);
        if (code != null) {
            Room room = rooms.get(code);
            if (room != null) {
                room.getEngine().handleQuit(sessionKey);
                if (room.getPlayerCount() == 0) {
                    System.out.println("[ROOM-MANAGER] Phòng [" + code + "] không còn người chơi, đang giải phóng tài nguyên...");
                    room.stop();
                    rooms.remove(code);
                }
                notifyListeners();
            }
        }
    }

    /**
     * Dispatcher chính: Nhận mọi gói tin từ UdpServer và định tuyến tới phòng tương ứng.
     */
    public void handlePacket(String message, InetAddress address, int port) {
        String sessionKey = address.getHostAddress() + ":" + port;
        String[] parts = message.split("\\|", -1);
        if (parts.length == 0) return;

        String cmd = parts[0].trim().toUpperCase();

        switch (cmd) {
            case "CREATE_ROOM": {
                String name = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : "Player";
                createRoom(name, address, port);
                break;
            }

            case "JOIN_ROOM": {
                String code = parts.length > 1 ? parts[1].trim().toUpperCase() : "";
                String name = (parts.length > 2 && !parts[2].trim().isEmpty()) ? parts[2].trim() : "Player";
                joinRoom(code, name, address, port);
                break;
            }

            case "JOIN": {
                String name = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : "Player";
                quickJoin(name, address, port);
                break;
            }

            case "LEAVE_ROOM":
            case "QUIT": {
                leaveRoom(sessionKey);
                break;
            }

            case "KICK_PLAYER": {
                String code = sessionToRoomCode.get(sessionKey);
                if (code != null && parts.length > 1) {
                    try {
                        int targetId = Integer.parseInt(parts[1].trim());
                        Room room = rooms.get(code);
                        if (room != null) {
                            boolean isHost = false;
                            for (PlayerSession ps : room.getSessions()) {
                                if (ps.getSessionKey().equals(sessionKey) && ps.isHost()) {
                                    isHost = true;
                                    break;
                                }
                            }
                            if (isHost) {
                                kickPlayer(code, targetId);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                break;
            }

            default: {
                // Các gói tin trong trận: MOVE, ACTION, BOMB, USE_WEAPON, START_GAME, RESTART_VOTE, PING
                String code = sessionToRoomCode.get(sessionKey);
                if (code != null) {
                    Room room = rooms.get(code);
                    if (room != null) {
                        room.getEngine().handlePacket(message, address, port);
                    }
                }
                break;
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Management API (Dùng cho Server GUI và Console)
    // ──────────────────────────────────────────────────────────

    public Collection<Room> getAllRooms() {
        return Collections.unmodifiableCollection(rooms.values());
    }

    public Room getRoom(String code) {
        return rooms.get(code.toUpperCase());
    }

    public synchronized void forceStartRoom(String code) {
        Room room = rooms.get(code.toUpperCase());
        if (room != null && room.getGameState() == 0) {
            room.getEngine().startGame();
            notifyListeners();
        }
    }

    public synchronized void deleteRoom(String code) {
        Room room = rooms.remove(code.toUpperCase());
        if (room != null) {
            // Ngắt tất cả người chơi trong phòng
            for (PlayerSession ps : room.getSessions()) {
                sessionToRoomCode.remove(ps.getSessionKey());
                server.send("QUIT_ACK|ROOM_CLOSED", ps.getAddress(), ps.getPort());
            }
            room.stop();
            notifyListeners();
            System.out.println("[ROOM-MANAGER] Đã xóa phòng [" + code + "] thủ công.");
        }
    }

    public synchronized void kickPlayer(String code, int playerId) {
        Room room = rooms.get(code.toUpperCase());
        if (room != null) {
            for (PlayerSession ps : room.getSessions()) {
                if (ps.getPlayer().getId() == playerId) {
                    sessionToRoomCode.remove(ps.getSessionKey());
                    server.send("QUIT_ACK|KICKED", ps.getAddress(), ps.getPort());
                    room.getEngine().handleQuit(ps.getSessionKey());
                    notifyListeners();
                    System.out.printf("[ROOM-MANAGER] Đã kick Player %d khỏi phòng [%s].%n", playerId, code);
                    break;
                }
            }
        }
    }

    public int getTotalPlayers() {
        int count = 0;
        for (Room r : rooms.values()) {
            count += r.getPlayerCount();
        }
        return count;
    }

    public void stopAll() {
        for (Room room : rooms.values()) {
            room.stop();
        }
        rooms.clear();
        sessionToRoomCode.clear();
        server.stop();
    }
}
