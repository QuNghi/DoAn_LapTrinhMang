package com.bomberman.server.model;

import com.bomberman.common.GameConstants;
import com.bomberman.common.TileType;

import java.util.Random;

/**
 * Quản lý lưới ma trận bản đồ trò chơi.
 * Hỗ trợ nhiều loại bản đồ khác nhau (Classic, Open Arena, Labyrinth, Crossfire).
 * Đảm bảo 100% không chứa khối tàng hình, các ô trống và gạch đồng bộ chuẩn xác.
 */
public class GameMap {
    public static final int MAP_RANDOM    = 0;
    public static final int MAP_CLASSIC   = 1;
    public static final int MAP_OPEN      = 2;
    public static final int MAP_LABYRINTH = 3;
    public static final int MAP_CROSSFIRE = 4;

    private final int width;
    private final int height;
    private final int[][] grid;
    private int initialSoftBricks = 0;
    private int remainingSoftBricks = 0;
    private int currentMapType = MAP_CLASSIC;

    public GameMap() {
        this(GameConstants.MAP_WIDTH, GameConstants.MAP_HEIGHT);
    }

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new int[height][width];
        initMap(MAP_CLASSIC, new Random(12345));
    }

    public static String getMapName(int type) {
        switch (type) {
            case MAP_CLASSIC:   return "Cổ điển (Classic)";
            case MAP_OPEN:      return "Đấu trường Mở (Open Arena)";
            case MAP_LABYRINTH: return "Mê cung (Labyrinth)";
            case MAP_CROSSFIRE: return "Ngã tư (Crossfire)";
            default:            return "Ngẫu nhiên (Random)";
        }
    }

    public String getCurrentMapName() {
        return getMapName(currentMapType);
    }

    public int getCurrentMapType() {
        return currentMapType;
    }

    public void regenerate() {
        initMap(currentMapType, new Random());
    }

    public void regenerate(int mapType) {
        initMap(mapType, new Random());
    }

    public void initMap() {
        initMap(MAP_CLASSIC, new Random(12345));
    }

    public void initMap(Random random) {
        initMap(currentMapType, random);
    }

    public void initMap(int mapType, Random random) {
        int chosenType = mapType;
        if (chosenType <= 0 || chosenType > 4) {
            // Random: chọn ngẫu nhiên 1 trong 4 bản đồ
            chosenType = 1 + random.nextInt(4);
        }
        this.currentMapType = chosenType;

        // Reset toàn bộ lưới thành EMPTY (0)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = TileType.EMPTY.getCode();
            }
        }

        // Tạo 4 vùng an toàn tại 4 góc xuất phát (1,1), (W-2,1), (1,H-2), (W-2,H-2)
        boolean[][] safeSpawn = new boolean[height][width];
        markSafeZone(safeSpawn, 1, 1);
        markSafeZone(safeSpawn, width - 2, 1);
        markSafeZone(safeSpawn, 1, height - 2);
        markSafeZone(safeSpawn, width - 2, height - 2);

        // Đắp viền ngoài bằng Tường cứng (HARD_WALL = 1)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    grid[y][x] = TileType.HARD_WALL.getCode();
                }
            }
        }

        switch (chosenType) {
            case MAP_OPEN:
                generateOpenMap(safeSpawn, random);
                break;
            case MAP_LABYRINTH:
                generateLabyrinthMap(safeSpawn, random);
                break;
            case MAP_CROSSFIRE:
                generateCrossfireMap(safeSpawn, random);
                break;
            case MAP_CLASSIC:
            default:
                generateClassicMap(safeSpawn, random);
                break;
        }

        // Đảm bảo 100% 4 góc spawn và các ô liền kề hoàn toàn trống (không có gạch/tường)
        clearSpawnCorners();

        // Đếm tổng số gạch mềm
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

    /** Bản đồ 1: Cổ điển (Classic Arena) - Cột xen kẽ tại các ô chẵn */
    private void generateClassicMap(boolean[][] safeSpawn, Random random) {
        for (int y = 2; y < height - 2; y += 2) {
            for (int x = 2; x < width - 2; x += 2) {
                grid[y][x] = TileType.HARD_WALL.getCode();
            }
        }

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (grid[y][x] == TileType.EMPTY.getCode() && !safeSpawn[y][x]) {
                    if (random.nextDouble() < 0.65) {
                        grid[y][x] = TileType.SOFT_BRICK.getCode();
                    }
                }
            }
        }
    }

    /** Bản đồ 2: Đấu trường Mở (Open Arena) - Trung tâm rộng rãi, chỉ có 4 cụm chướng ngại bo góc */
    private void generateOpenMap(boolean[][] safeSpawn, Random random) {
        // 4 cụm tường cứng nhỏ ở 4 góc phòng
        int[][] pillars = {
            {3, 3}, {4, 3}, {3, 4},
            {width - 4, 3}, {width - 5, 3}, {width - 4, 4},
            {3, height - 4}, {4, height - 4}, {3, height - 5},
            {width - 4, height - 4}, {width - 5, height - 4}, {width - 4, height - 5},
            // 2 khối chắn tâm nhỏ
            {width / 2, 3}, {width / 2, height - 4}
        };

        for (int[] p : pillars) {
            int px = p[0], py = p[1];
            if (px > 0 && px < width - 1 && py > 0 && py < height - 1 && !safeSpawn[py][px]) {
                grid[py][px] = TileType.HARD_WALL.getCode();
            }
        }

        // Rải gạch mật độ vừa phải (45%) để không gian thoáng đãng cho bắn súng và rượt đuổi
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (grid[y][x] == TileType.EMPTY.getCode() && !safeSpawn[y][x]) {
                    // Để trống tâm bản đồ (bán kính 2 ô)
                    int distCenter = Math.abs(x - width / 2) + Math.abs(y - height / 2);
                    if (distCenter > 2) {
                        if (random.nextDouble() < 0.45) {
                            grid[y][x] = TileType.SOFT_BRICK.getCode();
                        }
                    }
                }
            }
        }
    }

    /** Bản đồ 3: Mê cung (Labyrinth) - Dãy tường đá ziczac */
    private void generateLabyrinthMap(boolean[][] safeSpawn, Random random) {
        // Hàng tường đá ngang ngắt quãng tạo mê cung
        for (int y = 2; y < height - 2; y += 2) {
            boolean openLeft = (y % 4 == 2);
            for (int x = 2; x < width - 2; x++) {
                if (openLeft && (x == 2 || x == 3)) continue;
                if (!openLeft && (x == width - 3 || x == width - 4)) continue;
                if (x % 3 == 0) continue; // Cửa thông ngẫu nhiên
                if (!safeSpawn[y][x]) {
                    grid[y][x] = TileType.HARD_WALL.getCode();
                }
            }
        }

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (grid[y][x] == TileType.EMPTY.getCode() && !safeSpawn[y][x]) {
                    if (random.nextDouble() < 0.50) {
                        grid[y][x] = TileType.SOFT_BRICK.getCode();
                    }
                }
            }
        }
    }

    /** Bản đồ 4: Ngã tư Crossfire - Chữ thập lớn chia 4 góc */
    private void generateCrossfireMap(boolean[][] safeSpawn, Random random) {
        int midX = width / 2;
        int midY = height / 2;

        // Trục dọc
        for (int y = 2; y < height - 2; y++) {
            if (y != midY && y != midY - 1 && y != midY + 1) {
                if (!safeSpawn[y][midX]) {
                    grid[y][midX] = TileType.HARD_WALL.getCode();
                }
            }
        }

        // Trục ngang
        for (int x = 2; x < width - 2; x++) {
            if (x != midX && x != midX - 1 && x != midX + 1) {
                if (!safeSpawn[midY][x]) {
                    grid[midY][x] = TileType.HARD_WALL.getCode();
                }
            }
        }

        // 4 trụ phụ ở 4 góc phòng
        int[] subX = {3, width - 4};
        int[] subY = {2, height - 3};
        for (int sx : subX) {
            for (int sy : subY) {
                if (!safeSpawn[sy][sx]) {
                    grid[sy][sx] = TileType.HARD_WALL.getCode();
                }
            }
        }

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (grid[y][x] == TileType.EMPTY.getCode() && !safeSpawn[y][x]) {
                    if (random.nextDouble() < 0.60) {
                        grid[y][x] = TileType.SOFT_BRICK.getCode();
                    }
                }
            }
        }
    }

    private void markSafeZone(boolean[][] safe, int cx, int cy) {
        safe[cy][cx] = true;
        if (cy - 1 >= 0) safe[cy - 1][cx] = true;
        if (cy + 1 < height) safe[cy + 1][cx] = true;
        if (cx - 1 >= 0) safe[cy][cx - 1] = true;
        if (cx + 1 < width) safe[cy][cx + 1] = true;
    }

    /**
     * Dọn sạch hoàn toàn 4 góc spawn và các ô thoát góc liền kề thành EMPTY (0).
     * Ngăn chặn tuyệt đối tình trạng nhân vật bị kẹt/dính vào khối khi bắt đầu bất kỳ ván đấu nào.
     */
    public void clearSpawnCorners() {
        int[][] cornerTiles = {
            // Góc trên-trái (Player 1)
            {1, 1}, {2, 1}, {1, 2},
            // Góc trên-phải (Player 2)
            {width - 2, 1}, {width - 3, 1}, {width - 2, 2},
            // Góc dưới-trái (Player 3)
            {1, height - 2}, {2, height - 2}, {1, height - 3},
            // Góc dưới-phải (Player 4)
            {width - 2, height - 2}, {width - 3, height - 2}, {width - 2, height - 3}
        };

        for (int[] tile : cornerTiles) {
            int tx = tile[0];
            int ty = tile[1];
            if (tx >= 1 && tx < width - 1 && ty >= 1 && ty < height - 1) {
                grid[ty][tx] = TileType.EMPTY.getCode();
            }
        }
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

    public boolean isSoftBricksBelowThreshold(double thresholdRatio) {
        if (initialSoftBricks <= 0) return false;
        return ((double) remainingSoftBricks / initialSoftBricks) < thresholdRatio;
    }

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
