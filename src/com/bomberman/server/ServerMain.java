package com.bomberman.server;

import com.bomberman.common.GameConstants;
import com.bomberman.server.logic.GameEngine;
import com.bomberman.server.network.PlayerSession;

import java.util.Scanner;

/**
 * Lớp khởi chạy chính của Server Bomberman (Thành viên 1).
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
        System.out.println("   BOMBERMAN MULTIPLAYER UDP SERVER - BACKEND CORE        ");
        System.out.println("   Thành viên 1: The Network & Server Lead                ");
        System.out.println("==========================================================");
        System.out.println("Khởi động Server tại cổng: " + port);

        try {
            GameEngine engine = new GameEngine(port);
            engine.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nĐang tắt Server an toàn...");
                engine.stop();
            }));

            System.out.println("Server đã sẵn sàng! Gõ lệnh hỗ trợ:");
            System.out.println("  'start'  -> Bắt đầu trận đấu ngay với số người hiện có");
            System.out.println("  'status' -> Xem danh sách người chơi đang kết nối");
            System.out.println("  'stop'   -> Dừng Server và thoát");
            System.out.println("----------------------------------------------------------");

            Scanner scanner = new Scanner(System.in);
            while (true) {
                String cmd = scanner.nextLine().trim().toLowerCase();
                if (cmd.equals("stop") || cmd.equals("exit")) {
                    engine.stop();
                    System.out.println("Server đã dừng.");
                    System.exit(0);
                } else if (cmd.equals("start")) {
                    engine.startGame();
                } else if (cmd.equals("status")) {
                    System.out.println("--- DANH SÁCH NGƯỜI CHƠI (" + engine.getSessionsById().size() + ") ---");
                    for (PlayerSession session : engine.getSessionsById().values()) {
                        System.out.printf("  Player ID: %d | Tên: %-10s | Sống: %-5s | Tọa độ: (%.2f, %.2f) | IP: %s:%d%n",
                                session.getPlayer().getId(),
                                session.getPlayer().getName(),
                                session.getPlayer().isAlive(),
                                session.getPlayer().getX(),
                                session.getPlayer().getY(),
                                session.getAddress().getHostAddress(),
                                session.getPort());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[FATAL] Lỗi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
