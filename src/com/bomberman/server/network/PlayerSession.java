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

    @Override
    public String toString() {
        return String.format("Session[PlayerID=%d, Name=%s, Address=%s:%d]",
                player.getId(), player.getName(), address.getHostAddress(), port);
    }
}
