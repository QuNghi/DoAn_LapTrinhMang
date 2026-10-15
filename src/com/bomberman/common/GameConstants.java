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

    // Thông số nhân vật (giảm 10% kích thước va chạm: 0.6 -> 0.54, bán kính 0.27)
    public static final double PLAYER_SPEED = 0.12; // bước di chuyển mỗi tick
    public static final double PLAYER_BOUNDING_BOX = 0.54; // kích thước vùng va chạm nhân vật

    // Thông số bom
    public static final long BOMB_COUNTDOWN_MS = 3000; // 3 giây đếm ngược
    public static final int DEFAULT_BOMB_RADIUS = 1; // Bán kính nổ ban đầu: 1 ô ngoài tâm (dấu cộng trong phạm vi 3x3)
    public static final long EXPLOSION_DURATION_MS = 700; // Hiệu ứng lửa tồn tại 700ms

    // Giới hạn phòng chơi
    public static final int MAX_PLAYERS = 4;
    public static final long HEARTBEAT_TIMEOUT_MS = 8000; // 8 giây không có ping thì coi là ngắt kết nối

    // Cấu hình vật phẩm bổ trợ (Items & Buffs)
    public static final double ITEM_DROP_RATE = 0.45; // 45% xác suất rơi vật phẩm khi gạch mềm vỡ
    public static final double ITEM_DROP_RATE_LATE_GAME = 0.60; // 60% xác suất rơi vật phẩm khi còn dưới 30% số ô gạch
    public static final double LATE_GAME_BRICK_THRESHOLD = 0.30; // Ngưỡng 30% số ô gạch có thể phá còn lại
    public static final double SPEED_BOOST_PERCENT = 0.10; // Tăng 10% tốc độ chạy
    public static final double MAX_PLAYER_SPEED = 0.24; // Giới hạn trần tốc độ để tránh xuyên tường
    public static final int WEAPON_DEFAULT_AMMO = 3; // Súng nhặt được có 3 lượt bắn
}
