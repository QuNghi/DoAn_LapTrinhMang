package com.bomberman.server.model;

import com.bomberman.common.GameConstants;
import com.bomberman.common.TileType;

import java.util.Random;

/**
 * Quản lý lưới ma trận bản đồ trò chơi.
 */
public class GameMap {
    private final int width;
    private final int height;
    private final int[][] grid;
    private int initialSoftBricks = 0;
    private int remainingSoftBricks = 0;

    public GameMap() {
        this(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
    }

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new int[height][width];
        initMap();
    }

    /**
     * Khởi tạo bản đồ Bomberman chuẩn cổ điển:
     * - Viền ngoài là tường cứng (HARD_WALL = 1)
     * - Các cột trụ bên trong đặt xen kẽ ở các ô chẵn (HARD_WALL = 1)
     * - Chừa khoảng trống an toàn tại 4 góc cho người chơi xuất phát
     * - Các vị trí còn lại rải gạch mềm (SOFT_BRICK = 2)
     */
    public void initMap() {
        Random random = new Random(12345); // Seed cố định để bản đồ cân bằng hoặc ngẫu nhiên

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Viền ngoài bản đồ
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[y][x] = TileType.HARD_WALL.getCode();
                }
                // Các cột đá cố định xen kẽ
                else if (x % 2 == 0 && y % 2 == 0) {
                    grid[y][x] = TileType.HARD_WALL.getCode();
                }
                // Các ô còn lại
                else {
                    grid[y][x] = TileType.EMPTY.getCode();
                }
            }
        }

        // Chừa khoảng trống cho 3-4 vị trí xuất phát (Góc trên-trái, trên-phải, dưới-trái, dưới-phải)
        // Player 1: (1, 1), (1, 2), (2, 1)
        // Player 2: (13, 1), (12, 1), (13, 2)
        // Player 3: (1, 11), (1, 10), (2, 11)
        // Player 4: (13, 11), (13, 10), (12, 11)
        boolean[][] safeSpawn = new boolean[height][width];
        markSafeZone(safeSpawn, 1, 1);
        markSafeZone(safeSpawn, width - 2, 1);
        markSafeZone(safeSpawn, 1, height - 2);
        markSafeZone(safeSpawn, width - 2, height - 2);

        // Rải gạch mềm (SOFT_BRICK) với xác suất 70% vào các ô còn lại
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (grid[y][x] == TileType.EMPTY.getCode() && !safeSpawn[y][x]) {
                    if (random.nextDouble() < 0.70) {
                        grid[y][x] = TileType.SOFT_BRICK.getCode();
                    }
                }
            }
        }

        // Đếm tổng số ô gạch mềm ban đầu
        initialSoftBricks = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == TileType.SOFT_BRICK.getCode()) {
                    initialSoftBricks++;
                }
            }
        }
        remainingSoftBricks = initialSoftBricks;
    }

    private void markSafeZone(boolean[][] safe, int cx, int cy) {
        safe[cy][cx] = true;
        if (cy - 1 >= 0) safe[cy - 1][cx] = true;
        if (cy + 1 < height) safe[cy + 1][cx] = true;
        if (cx - 1 >= 0) safe[cy][cx - 1] = true;
        if (cx + 1 < width) safe[cy][cx + 1] = true;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return TileType.HARD_WALL.getCode();
        }
        return grid[y][x];
    }

    public void setTile(int x, int y, int tileCode) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            grid[y][x] = tileCode;
        }
    }

    public boolean isWalkable(int x, int y) {
        int tile = getTile(x, y);
        return tile == TileType.EMPTY.getCode();
    }

    public boolean destroyBrick(int x, int y) {
        if (getTile(x, y) == TileType.SOFT_BRICK.getCode()) {
            grid[y][x] = TileType.EMPTY.getCode();
            if (remainingSoftBricks > 0) {
                remainingSoftBricks--;
            }
            return true;
        }
        return false;
    }

    public int getInitialSoftBricks() {
        return initialSoftBricks;
    }

    public int getRemainingSoftBricks() {
        return remainingSoftBricks;
    }

    /**
     * Kiểm tra xem tỉ lệ gạch mềm còn lại có dưới ngưỡng cho trước (ví dụ 0.30 = 30%) hay không.
     */
    public boolean isSoftBricksBelowThreshold(double thresholdRatio) {
        if (initialSoftBricks <= 0) return false;
        return ((double) remainingSoftBricks / initialSoftBricks) < thresholdRatio;
    }

    /**
     * Chuyển toàn bộ ma trận thành chuỗi ký tự liền nhau để gửi trong gói tin MAP_INIT.
     * Ví dụ: 1111111111111111002220000000001...
     */
    public String toFlatString() {
        StringBuilder sb = new StringBuilder(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(grid[y][x]);
            }
        }
        return sb.toString();
    }
}
