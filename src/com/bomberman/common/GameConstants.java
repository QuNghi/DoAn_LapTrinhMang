package com.bomberman.common;

/**
 * Các hằng số cấu hình toàn bộ trò chơi Bomberman UDP.
 * Được dùng chung giữa Server và Client để đồng bộ thông số kỹ thuật.
 */
public final class GameConstants {
    private GameConstants() {}

    // Cổng mạng UDP
    public static final int DEFAULT_PORT = 9876;
    public static final int BUFFER_SIZE = 1024;

    // Kích thước bản đồ chuẩn (15 cột x 13 dòng)
    public static final int MAP_WIDTH = 15;
    public static final int MAP_HEIGHT = 13;

    // Nhịp game loop của Server
    public static final int TICK_RATE = 30; // 30 ticks mỗi giây
    public static final long TICK_INTERVAL_MS = 1000 / TICK_RATE; // ~33ms

    // Thông số nhân vật
    public static final double PLAYER_SPEED = 0.12; // bước di chuyển mỗi tick
    public static final double PLAYER_BOUNDING_BOX = 0.6; // kích thước vùng va chạm nhân vật

    // Thông số bom
    public static final long BOMB_COUNTDOWN_MS = 3000; // 3 giây đếm ngược
    public static final int DEFAULT_BOMB_RADIUS = 2; // Tầm nổ 2 ô
    public static final long EXPLOSION_DURATION_MS = 700; // Hiệu ứng lửa tồn tại 700ms

    // Giới hạn phòng chơi
    public static final int MAX_PLAYERS = 3;
    public static final long HEARTBEAT_TIMEOUT_MS = 8000; // 8 giây không có ping thì coi là ngắt kết nối
}
