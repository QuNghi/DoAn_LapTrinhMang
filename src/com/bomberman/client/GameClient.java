package com.bomberman.client;

import com.bomberman.common.GameConstants;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * Quản lý kết nối UDP đến Server (gửi + nhận gói tin).
 * Chạy thread riêng không block UI.
 */
public class GameClient {

    private final String serverHost;
    private final int serverPort;
    private final ClientGameState state;
    private final java.util.function.BiConsumer<String, String[]> onEvent;
    private String myPlayerName = ""; // Lưu tên để gán sau JOIN_ACK

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private volatile boolean running = false;

    private Thread receiverThread;
    private Thread heartbeatThread;

    public GameClient(String serverHost, int serverPort, ClientGameState state,
                      BiConsumer<String, String[]> onEvent) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.state = state;
        this.onEvent = onEvent;
    }

    // ──────────────────────────────────────────────────────────
    //  Kết nối và khởi chạy
    // ──────────────────────────────────────────────────────────

    public void connect(String playerName) throws IOException {
        socket = new DatagramSocket();
        socket.setSoTimeout(0); // blocking receive
        serverAddress = InetAddress.getByName(serverHost);
        running = true;

        // Thread nhận gói tin từ Server
        receiverThread = new Thread(this::receiveLoop, "Client-Receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();

        // Thread gửi heartbeat mỗi 3 giây
        heartbeatThread = new Thread(this::heartbeatLoop, "Client-Heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        // Lưu tên người chơi
        this.myPlayerName = playerName;

        // Gửi JOIN
        sendPacket("JOIN|" + playerName);
        System.out.println("[CLIENT] Đã gửi JOIN|" + playerName + " → " + serverHost + ":" + serverPort);
    }

    public void disconnect() {
        running = false;
        try { sendPacket("QUIT"); } catch (Exception ignored) {}
        if (socket != null && !socket.isClosed()) socket.close();
        System.out.println("[CLIENT] Đã ngắt kết nối.");
    }

    public boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }

    // ──────────────────────────────────────────────────────────
    //  Gửi lệnh lên Server
    // ──────────────────────────────────────────────────────────

    public void sendMove(String direction) {
        System.out.println("[CLIENT-ACTION] Di chuyển: " + direction);
        sendPacket("MOVE|" + direction);
    }

    public void sendStop() {
        System.out.println("[CLIENT-ACTION] Dừng lại: MOVE|STOP");
        sendPacket("MOVE|STOP");
    }

    public void sendBomb() {
        System.out.println("[CLIENT-ACTION] Đặt bom: BOMB");
        sendPacket("BOMB");
    }

    public void sendUseWeapon() {
        System.out.println("[CLIENT-ACTION] Dùng vũ khí: USE_WEAPON");
        sendPacket("USE_WEAPON");
    }

    public void sendPacket(String message) {
        if (socket == null || socket.isClosed()) {
            System.err.println("[CLIENT] Socket chưa sẵn sàng để gửi: " + message);
            return;
        }
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket pkt = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(pkt);
            if (!message.equals("PING")) {
                System.out.println("[CLIENT-SEND] >>> " + message);
            }
        } catch (IOException e) {
            System.err.println("[CLIENT] Lỗi gửi: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Loop nhận gói tin
    // ──────────────────────────────────────────────────────────

    private void receiveLoop() {
        byte[] buffer = new byte[GameConstants.BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                socket.receive(pkt);
                String msg = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(),
                        StandardCharsets.UTF_8).trim();
                processPacket(msg);
            } catch (SocketException e) {
                if (running) System.err.println("[CLIENT] Socket lỗi: " + e.getMessage());
                break;
            } catch (Exception e) {
                if (running) System.err.println("[CLIENT] Nhận lỗi: " + e.getMessage());
            }
        }
    }

    private void processPacket(String msg) {
        String[] parts = msg.split("\\|", -1);
        if (parts.length == 0) return;
        String header = parts[0].trim().toUpperCase();

        // Cập nhật state trực tiếp
        switch (header) {
            case "JOIN_ACK":
                handleJoinAck(parts);
                break;
            case "MAP_INIT":
                state.parseMapInit(parts);
                break;
            case "GAME_START":
                state.gameStarted = true;
                state.connected = true;
                state.countdownSeconds = -1;
                state.countdownText = "";
                break;
            case "STATE":
                state.parseState(parts);
                ClientGameState.PlayerData me = state.getMyPlayer();
                if (me != null && state.latestTick % 30 == 0) {
                    System.out.printf("[CLIENT-RECV-STATE] Tick %d | Vị trí bạn (ID=%d): x=%.2f, y=%.2f, dir=%s%n",
                            state.latestTick, me.id, me.x, me.y, me.direction);
                }
                break;
            case "TILE_UPDATE":
                state.parseTileUpdate(parts);
                break;
            case "EXPLODE":
                state.parseExplode(parts);
                break;
            case "ITEM_SPAWN":
                state.parseItemSpawn(parts);
                break;
            case "ITEM_COLLECT":
                state.parseItemCollect(parts);
                break;
            case "ITEM_DESTROYED":
                state.parseItemDestroyed(parts);
                break;
            case "PLAYER_DIED":
                state.parsePlayerDied(parts);
                break;
            case "COUNTDOWN":
                state.parseCountdown(parts);
                System.out.println("[CLIENT] Đếm ngược trận đấu: " + state.countdownText);
                break;
            case "PLAYER_LEFT":
                state.parsePlayerLeft(parts);
                System.out.println("[CLIENT] Người chơi đã rời phòng: " + (parts.length > 2 ? parts[2] : parts[1]));
                break;
            case "GAME_OVER":
                state.parseGameOver(parts);
                break;
            default:
                break;
        }

        // Thông báo lên UI qua callback
        if (onEvent != null) {
            onEvent.accept(header, parts);
        }
    }

    private void handleJoinAck(String[] parts) {
        if (parts.length < 3) return;
        String status = parts[2];
        if ("SUCCESS".equals(status) && parts.length >= 2) {
            try {
                state.myPlayerId = Integer.parseInt(parts[1]);
                state.connected = true;
                // Gán tên người chơi vào state với ID thật
                state.setPlayerName(state.myPlayerId, myPlayerName);
                System.out.println("[CLIENT] Gia nhập thành công! Player ID = " + state.myPlayerId);
            } catch (Exception ignored) {}
        } else if ("FULL".equals(status)) {
            System.out.println("[CLIENT] Phòng đã đầy!");
        }
    }

    private void handleGameOver(String[] parts) {
        state.gameOver = true;
        if (parts.length >= 3) {
            try {
                state.winnerPlayerId = Integer.parseInt(parts[1]);
                state.winnerName = parts[2];
            } catch (Exception ignored) {}
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Heartbeat
    // ──────────────────────────────────────────────────────────

    private void heartbeatLoop() {
        while (running) {
            try {
                Thread.sleep(3000);
                sendPacket("PING");
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
