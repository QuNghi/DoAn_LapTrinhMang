package com.bomberman.server;

import com.bomberman.common.GameConstants;
import com.bomberman.server.model.Player;
import com.bomberman.server.model.Room;
import com.bomberman.server.network.PlayerSession;
import com.bomberman.server.network.UdpServer;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.util.Scanner;

/**
 * Lớp khởi chạy chính của Server Bomberman (Hỗ trợ Multi-Room và ServerFrame GUI).
 */
public class ServerMain {
    public static void main(String[] args) {
        int port = GameConstants.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("[WARN] Cổng không hợp lệ, sử dụng cổng mặc định: " + port);
            }
        }

        System.out.println("==========================================================");
        System.out.println("   BOMBERMAN MULTIPLAYER UDP SERVER - MULTI-ROOM ENGINE   ");
        System.out.println("==========================================================");
        System.out.println("Khởi động Server tại cổng: " + port);

        try {
            // Khởi tạo UDP Socket và Bộ quản lý đa phòng
            UdpServer udpServer = new UdpServer(port, null);
            RoomManager roomManager = new RoomManager(udpServer);
            udpServer.start();

            // Khởi động GUI ServerFrame nếu hỗ trợ hiển thị (desktop environment)
            final int serverPort = port;
            if (!GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        // Sử dụng Metal L&F để đảm bảo màu sắc tùy chỉnh hoạt động đúng
                    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                    } catch (Exception ignored) {}
                    ServerFrame frame = new ServerFrame(roomManager, serverPort);
                    frame.setVisible(true);
                });
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nĐang tắt Server an toàn...");
                roomManager.stopAll();
            }));

            System.out.println("Server đã sẵn sàng! Gõ lệnh hỗ trợ:");
            System.out.println("  'rooms'              -> Liệt kê danh sách tất cả các phòng");
            System.out.println("  'room <code> delete' -> Xóa phòng chỉ định (giải phóng phòng treo)");
            System.out.println("  'status'             -> Xem tổng quan hệ thống & người chơi");
            System.out.println("  'stop' / 'exit'      -> Dừng Server và thoát");
            System.out.println("----------------------------------------------------------");

            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                String[] tokens = line.split("\\s+");
                String cmd = tokens[0].toLowerCase();

                if (cmd.equals("stop") || cmd.equals("exit")) {
                    roomManager.stopAll();
                    System.out.println("Server đã dừng.");
                    System.exit(0);
                } else if (cmd.equals("rooms")) {
                    System.out.println("--- DANH SÁCH CÁC PHÒNG (" + roomManager.getAllRooms().size() + ") ---");
                    for (Room r : roomManager.getAllRooms()) {
                        System.out.printf("  Phòng: [%-4s] | Trạng thái: %-9s | Người chơi: %d/%d | Host: P%d | Map: %s%n",
                                r.getRoomCode(), r.getStateName(), r.getPlayerCount(),
                                GameConstants.MAX_PLAYERS, r.getHostPlayerId(), r.getMapName());
                    }
                } else if (cmd.equals("status")) {
                    System.out.println("--- TỔNG QUAN HỆ THỐNG ---");
                    System.out.println("  Tổng số phòng: " + roomManager.getAllRooms().size());
                    System.out.println("  Tổng số người chơi: " + roomManager.getTotalPlayers());
                    for (Room r : roomManager.getAllRooms()) {
                        System.out.println("  [Phòng " + r.getRoomCode() + " - " + r.getStateName() + " - " + r.getMapName() + "]:");
                        for (PlayerSession session : r.getSessions()) {
                            Player p = session.getPlayer();
                            System.out.printf("    Player ID: %d | Tên: %-10s | Host: %-5s | Sống: %-5s | Tọa độ: (%.2f, %.2f) | IP: %s:%d%n",
                                    p.getId(), p.getName(), session.isHost(), p.isAlive(), p.getX(), p.getY(),
                                    session.getAddress().getHostAddress(), session.getPort());
                        }
                    }
                } else if (cmd.equals("room") && tokens.length >= 3) {
                    String roomCode = tokens[1].toUpperCase();
                    String action = tokens[2].toLowerCase();

                    if (action.equals("delete")) {
                        roomManager.deleteRoom(roomCode);
                        System.out.println("[CMD] Đã xóa phòng [" + roomCode + "].");
                    } else if (action.equals("start") || action.equals("kick")) {
                        System.out.println("[INFO] Quyền bắt đầu trận đấu và kick người chơi đã được giao cho Client Chủ phòng quản lý.");
                    } else {
                        System.out.println("[ERROR] Cú pháp lệnh room không đúng. Sử dụng: 'room <code> delete'");
                    }
                } else {
                    System.out.println("[WARN] Lệnh không nhận diện: " + line);
                }
            }
        } catch (Exception e) {
            System.err.println("[FATAL] Lỗi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
