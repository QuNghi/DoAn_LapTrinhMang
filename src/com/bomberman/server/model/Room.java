package com.bomberman.server.model;

import com.bomberman.server.logic.GameEngine;
import com.bomberman.server.network.PlayerSession;
import com.bomberman.server.network.UdpServer;

import java.util.Collection;

/**
 * Đại diện cho một phòng chơi độc lập trong hệ thống Multi-Room.
 * Mỗi phòng sở hữu một GameEngine riêng biệt chạy trên worker thread riêng.
 */
public class Room {
    private final String roomCode;
    private final GameEngine engine;
    private final long createdAt;

    public Room(String roomCode, UdpServer server) {
        this.roomCode = roomCode;
        this.createdAt = System.currentTimeMillis();
        this.engine = new GameEngine(server, roomCode);
        try {
            this.engine.start();
        } catch (Exception e) {
            System.err.println("[ROOM] Lỗi khởi động GameEngine cho phòng " + roomCode + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getRoomCode() {
        return roomCode;
    }

    public GameEngine getEngine() {
        return engine;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getPlayerCount() {
        return engine.getSessionsById().size();
    }

    public int getHostPlayerId() {
        return engine.getHostPlayerId();
    }

    public Collection<PlayerSession> getSessions() {
        return engine.getSessionsById().values();
    }

    public int getGameState() {
        return engine.getGameState();
    }

    public String getStateName() {
        switch (engine.getGameState()) {
            case 0: return "WAITING";
            case 1: return "COUNTDOWN";
            case 2: return "PLAYING";
            case 3: return "ENDED";
            default: return "UNKNOWN";
        }
    }

    public void stop() {
        engine.stop();
    }
}
