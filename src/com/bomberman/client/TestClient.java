package com.bomberman.client;

import com.bomberman.common.GameConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Client dòng lệnh (Console Mock Client) dùng để kiểm thử nhanh Server UDP.
 * Giúp Thành viên 1 tự test độc lập toàn bộ chức năng Server trước khi ghép UI.
 * Đồng thời là mã mẫu cho Thành viên 3 tham khảo cách giao tiếp UDP.
 */
public class TestClient {
    private final String serverHost;
    private final int serverPort;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private volatile boolean running = true;
    private int myPlayerId = -1;

    public TestClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public void start(String playerName) throws Exception {
        socket = new DatagramSocket();
        serverAddress = InetAddress.getByName(serverHost);

        // Luồng nhận phản hồi từ Server
        Thread receiverThread = new Thread(this::listenForServerPackets, "Client-Receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();

        // Luồng gửi Heartbeat (PING) định kỳ mỗi 3 giây
        Thread heartbeatThread = new Thread(this::heartbeatLoop, "Client-Heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        // 1. Gửi gói JOIN lên Server
        sendPacket("JOIN|" + playerName);
        System.out.printf("[CLIENT] Đã gửi yêu cầu JOIN|%s tới %s:%d%n", playerName, serverHost, serverPort);

        // Đọc lệnh từ người dùng để kiểm thử di chuyển và đặt bom
        printInstructions();
        Scanner scanner = new Scanner(System.in);

        while (running) {
            String input = scanner.nextLine().trim().toLowerCase();
            switch (input) {
                case "w":
                case "up":
                    sendPacket("MOVE|UP");
                    break;
                case "s":
                case "down":
                    sendPacket("MOVE|DOWN");
                    break;
                case "a":
                case "left":
                    sendPacket("MOVE|LEFT");
                    break;
                case "d":
                case "right":
                    sendPacket("MOVE|RIGHT");
                    break;
                case "x":
                case "stop":
                    sendPacket("MOVE|STOP");
                    break;
                case "b":
                case "bomb":
                    sendPacket("BOMB");
                    break;
                case "f":
                case "shoot":
                case "use":
                    sendPacket("USE_WEAPON");
                    break;
                case "space":
                case "action":
                    sendPacket("ACTION");
                    break;
                case "start":
                    sendPacket("START_GAME");
                    break;
                case "yes":
                    sendPacket("RESTART_VOTE|YES");
                    break;
                case "no":
                    sendPacket("RESTART_VOTE|NO");
                    break;
                case "q":
                case "quit":
                    sendPacket("QUIT");
                    running = false;
                    socket.close();
                    System.out.println("[CLIENT] Đã thoát.");
                    return;
                default:
                    if (input.startsWith("create ")) {
                        String rName = input.substring(7).trim();
                        sendPacket("CREATE_ROOM|" + rName);
                    } else if (input.startsWith("join ")) {
                        String[] p = input.substring(5).trim().split("\\s+");
                        if (p.length >= 2) {
                            sendPacket("JOIN_ROOM|" + p[0].toUpperCase() + "|" + p[1]);
                        } else if (p.length == 1) {
                            sendPacket("JOIN_ROOM|" + p[0].toUpperCase() + "|Player");
                        }
                    } else {
                        System.out.println("[CLIENT] Lệnh không hợp lệ. Gõ w/a/s/d di chuyển, action (hoặc b/f), start, yes/no, q thoát.");
                    }
            }
        }
    }

    private void sendPacket(String message) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[CLIENT] Lỗi gửi gói: " + e.getMessage());
        }
    }

    private void listenForServerPackets() {
        byte[] buffer = new byte[GameConstants.BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                handleReceivedMessage(msg);
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    private void handleReceivedMessage(String msg) {
        String[] parts = msg.split("\\|", -1);
        String header = parts[0];

        switch (header) {
            case "JOIN_ACK":
                myPlayerId = Integer.parseInt(parts[1]);
                System.out.printf(">>> [SERVER REP] GIA NHẬP THÀNH CÔNG! Player ID của bạn: %d | Trạng thái: %s | Tin nhắn: %s%n",
                        myPlayerId, parts[2], parts[3]);
                break;

            case "MAP_INIT":
                System.out.printf(">>> [SERVER REP] NHẬN BẢN ĐỒ: %s x %s (Kích thước chuỗi ma trận: %d)%n",
                        parts[1], parts[2], parts[3].length());
                break;

            case "GAME_START":
                System.out.println(">>> [SERVER REP] TRẬN ĐẤU ĐÃ BẮT ĐẦU! Tổng số người chơi: " + parts[1]);
                break;

            case "STATE":
                // In định kỳ mỗi 30 tick để tránh tràn màn hình console
                long tick = Long.parseLong(parts[1]);
                if (tick % 30 == 0) {
                    String itemsStr = parts.length > 4 ? parts[4] : "NONE";
                    System.out.printf("[STATE Tick %d] Players: %s | Bombs: %s | Items: %s%n", tick, parts[2], parts[3], itemsStr);
                }
                break;

            case "EXPLODE":
                System.out.printf(">>> [BOOM!] BOM NỔ: ID=%s | Vùng lửa: %s%n", parts[1], parts[2]);
                break;

            case "TILE_UPDATE":
                System.out.printf(">>> [MAP UPDATE] Ô (%s, %s) biến thành loại %s%n", parts[1], parts[2], parts[3]);
                break;

            case "ITEM_SPAWN":
                System.out.printf(">>> [VẬT PHẨM MỚI] %s (Loại %s) rơi ra tại ô (%s, %s)%n", parts[1], parts[2], parts[3], parts[4]);
                break;

            case "ITEM_COLLECT":
                System.out.printf(">>> [NHẶT BUFF] Player %s đã nhặt vật phẩm %s (Loại: %s)%n", parts[2], parts[1], parts[4]);
                break;

            case "ITEM_DESTROYED":
                System.out.printf(">>> [VẬT PHẨM CHÁY] Vật phẩm %s đã bị lửa bom thiêu rụi!%n", parts[1]);
                break;

            case "SHIELD_BROKEN":
                System.out.printf(">>> [KHIÊN VỠ!] Khiên của Player %s (%s) vừa đỡ 1 đòn chí mạng và vỡ!%n", parts[1], parts[2]);
                break;

            case "WEAPON_USED":
                System.out.printf(">>> [BẮN SÚNG!] Player %s khai hỏa %s! Đạn còn: %s | Trúng: %s (TargetID: %s)%n",
                        parts[1], parts[2], parts[3], parts[5], parts[6]);
                break;

            case "PLAYER_DIED":
                System.out.printf(">>> [DEAD] Player %s đã bị tiêu diệt bởi Player %s!%n", parts[1], parts[2]);
                break;

            case "GAME_OVER":
                System.out.printf(">>> [GAME OVER] Trận đấu kết thúc! Người thắng cuộc: ID %s (%s)%n", parts[1], parts[2]);
                break;

            default:
                System.out.println("[SERVER] " + msg);
        }
    }

    private void heartbeatLoop() {
        while (running) {
            try {
                Thread.sleep(3000); // 3s gửi ping 1 lần
                sendPacket("PING");
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void printInstructions() {
        System.out.println("==========================================================");
        System.out.println("PHÍM ĐIỀU KHIỂN CLIENT:");
        System.out.println("  'w' -> Đi lên (UP)");
        System.out.println("  's' -> Đi xuống (DOWN)");
        System.out.println("  'a' -> Sang trái (LEFT)");
        System.out.println("  'd' -> Sang phải (RIGHT)");
        System.out.println("  'x' -> Dừng lại (STOP)");
        System.out.println("  'b' -> Đặt bom (BOMB)");
        System.out.println("  'f' -> Bắn súng vũ khí (USE_WEAPON - Bomb IT style)");
        System.out.println("  'q' -> Thoát (QUIT)");
        System.out.println("==========================================================");
    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = GameConstants.DEFAULT_PORT;
        String name = "Player_" + (int)(Math.random() * 900 + 100);

        if (args.length > 0) name = args[0];
        if (args.length > 1) host = args[1];
        if (args.length > 2) port = Integer.parseInt(args[2]);

        try {
            TestClient client = new TestClient(host, port);
            client.start(name);
        } catch (Exception e) {
            System.err.println("Lỗi khởi chạy client: " + e.getMessage());
        }
    }
}
