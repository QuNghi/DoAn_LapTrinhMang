package com.bomberman.server.network;

import com.bomberman.common.GameConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Lớp mạng tầng truyền tải UDP phía Server.
 * Chịu trách nhiệm mở DatagramSocket, nhận và gửi các gói tin DatagramPacket.
 */
public class UdpServer {
    public interface PacketListener {
        void onPacketReceived(String message, InetAddress address, int port);
    }

    private final int port;
    private final PacketListener listener;
    private DatagramSocket socket;
    private Thread receiveThread;
    private volatile boolean running;

    public UdpServer(int port, PacketListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public synchronized void start() throws SocketException {
        if (running) return;

        socket = new DatagramSocket(port);
        running = true;

        receiveThread = new Thread(this::receiveLoop, "UdpServer-Receiver");
        receiveThread.setDaemon(true);
        receiveThread.start();

        System.out.println("[SERVER-UDP] Đã mở socket UDP lắng nghe tại cổng " + port);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        System.out.println("[SERVER-UDP] Đã đóng socket UDP.");
    }

    private void receiveLoop() {
        byte[] buffer = new byte[GameConstants.BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                if (!message.isEmpty() && listener != null) {
                    listener.onPacketReceived(message, packet.getAddress(), packet.getPort());
                }
            } catch (SocketException e) {
                if (!running) {
                    break; // Socket bị đóng chủ động
                }
                System.err.println("[SERVER-UDP] Lỗi socket: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("[SERVER-UDP] Lỗi đọc gói tin: " + e.getMessage());
            }
        }
    }

    /**
     * Gửi tin nhắn chuỗi UTF-8 tới một địa chỉ đích cụ thể.
     */
    public void send(String message, InetAddress address, int targetPort) {
        if (socket == null || socket.isClosed()) return;
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, targetPort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[SERVER-UDP] Lỗi gửi tin nhắn tới " + address + ":" + targetPort + " - " + e.getMessage());
        }
    }

    /**
     * Broadcast gói tin tới tất cả Client đang kết nối.
     */
    public void broadcast(String message, Collection<PlayerSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return;
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        for (PlayerSession session : sessions) {
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length, session.getAddress(), session.getPort());
                socket.send(packet);
            } catch (IOException e) {
                System.err.println("[SERVER-UDP] Lỗi broadcast tới " + session + ": " + e.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}
