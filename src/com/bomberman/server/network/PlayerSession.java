package com.bomberman.server.network;

import com.bomberman.common.GameConstants;
import com.bomberman.server.model.Player;

import java.net.InetAddress;

/**
 * Quản lý thông tin phiên kết nối của một Client với Server.
 */
public class PlayerSession {
    private final Player player;
    private final InetAddress address;
    private final int port;
    private long lastHeartbeat;
    private boolean isHost = false;
    private String roomCode = "";
    private Boolean restartVote = null; // null: chưa vote, true: YES, false: NO

    public PlayerSession(Player player, InetAddress address, int port) {
        this.player = player;
        this.address = address;
        this.port = port;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public Player getPlayer() {
        return player;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public boolean isTimedOut(long now) {
        return (now - lastHeartbeat) > GameConstants.HEARTBEAT_TIMEOUT_MS;
    }

    public String getSessionKey() {
        return address.getHostAddress() + ":" + port;
    }

    public boolean isHost() {
        return isHost;
    }

    public void setHost(boolean host) {
        this.isHost = host;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public Boolean getRestartVote() {
        return restartVote;
    }

    public void setRestartVote(Boolean restartVote) {
        this.restartVote = restartVote;
    }

    /** Trạng thái sẵn sàng của thành viên (Host luôn tính là ready) */
    private boolean ready = false;

    public boolean isReady() {
        return ready || isHost; // Host luôn tính là sẵn sàng
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    @Override
    public String toString() {
        return String.format("Session[PlayerID=%d, Name=%s, Host=%s, Room=%s, Address=%s:%d]",
                player.getId(), player.getName(), isHost, roomCode, address.getHostAddress(), port);
    }
}
