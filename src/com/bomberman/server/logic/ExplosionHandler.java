package com.bomberman.server.logic;

import com.bomberman.common.Direction;
import com.bomberman.common.TileType;
import com.bomberman.common.GameConstants;
import com.bomberman.common.ItemType;
import com.bomberman.server.model.Bomb;
import com.bomberman.server.model.GameMap;
import com.bomberman.server.model.Item;
import com.bomberman.server.model.Player;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Xử lý thuật toán lan truyền tia lửa bom nổ, phá hủy tường mềm, rơi vật phẩm buff và tiêu diệt người chơi.
 */
public class ExplosionHandler {

    private static final Random random = new Random();

    public static class ExplosionResult {
        public final List<Point> flameCells = new ArrayList<>();
        public final List<Point> destroyedBricks = new ArrayList<>();
        public final List<Player> killedPlayers = new ArrayList<>();
        public final List<Player> shieldSavedPlayers = new ArrayList<>();
        public final List<Bomb> triggeredBombs = new ArrayList<>();
        public final List<Item> spawnedItems = new ArrayList<>();
        public final List<Item> incineratedItems = new ArrayList<>();
    }

    /**
     * Ngẫu nhiên chọn loại vật phẩm rơi ra theo tỉ lệ cân bằng.
     */
    public static ItemType randomItemType() {
        int roll = random.nextInt(100);
        if (roll < 25) return ItemType.BOMB_RANGE;   // 25% Thuốc tăng tầm nổ
        if (roll < 50) return ItemType.BOMB_COUNT;   // 25% Thuốc tăng số lượng bom
        if (roll < 70) return ItemType.SPEED_BOOST;  // 20% Thuốc tăng tốc độ di chuyển (+10%)
        if (roll < 85) return ItemType.SHIELD;       // 15% Khiên đỡ 1 lần sát thương
        return ItemType.WEAPON_GUN;                  // 15% Vũ khí có số lần sử dụng (Bomb IT style)
    }

    /**
     * Tính toán toàn bộ hiệu ứng và va chạm của một vụ nổ bom.
     *
     * @param map bản đồ game
     * @param bombs danh sách các quả bom trên sân (để kích nổ dây chuyền)
     * @param players danh sách tất cả người chơi
     * @param existingItems danh sách vật phẩm đang nằm trên sàn
     * @param bomb quả bom phát nổ
     * @param itemCounter bộ đếm ID sinh vật phẩm
     * @return ExplosionResult chứa danh sách ô lửa, gạch vỡ, vật phẩm sinh ra, vật phẩm cháy, người chơi bị hạ gục/được khiên cứu
     */
    public static ExplosionResult processExplosion(GameMap map, List<Bomb> bombs, List<Player> players,
                                                  List<Item> existingItems, Bomb bomb, AtomicInteger itemCounter) {
        ExplosionResult result = new ExplosionResult();
        Set<Point> flameSet = new HashSet<>();

        int cx = bomb.getGridX();
        int cy = bomb.getGridY();
        int radius = bomb.getRadius();

        // 1. Tâm nổ
        Point center = new Point(cx, cy);
        flameSet.add(center);

        // 2. Lan tỏa tia lửa theo 4 hướng: Lên, Xuống, Trái, Phải
        Direction[] directions = {Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT};

        for (Direction dir : directions) {
            for (int step = 1; step <= radius; step++) {
                int nx = cx + dir.getDx() * step;
                int ny = cy + dir.getDy() * step;

                // Ra ngoài bản đồ thì dừng
                if (nx < 0 || nx >= map.getWidth() || ny < 0 || ny >= map.getHeight()) {
                    break;
                }

                int tile = map.getTile(nx, ny);

                // Gặp Tường cứng -> Dừng ngay lập tức, không đi xuyên qua
                if (tile == TileType.HARD_WALL.getCode()) {
                    break;
                }

                // Gặp Gạch mềm -> Thêm vào vùng nổ, phá hủy gạch thành ô trống, và dừng tia lửa hướng này
                if (tile == TileType.SOFT_BRICK.getCode()) {
                    Point p = new Point(nx, ny);
                    flameSet.add(p);
                    result.destroyedBricks.add(p);
                    map.destroyBrick(nx, ny);

                    // Tỉ lệ rơi vật phẩm khi gạch vỡ
                    if (random.nextDouble() < GameConstants.ITEM_DROP_RATE && itemCounter != null) {
                        ItemType droppedType = randomItemType();
                        String itemId = "IT" + itemCounter.getAndIncrement();
                        Item newItem = new Item(itemId, droppedType, nx, ny);
                        result.spawnedItems.add(newItem);
                    }
                    break;
                }

                // Kiểm tra nếu trúng quả bom khác -> Kích nổ dây chuyền (Chain Reaction)
                for (Bomb otherBomb : bombs) {
                    if (!otherBomb.isExploded() && otherBomb.getGridX() == nx && otherBomb.getGridY() == ny) {
                        otherBomb.triggerExplosion();
                        result.triggeredBombs.add(otherBomb);
                    }
                }

                // Ô trống bình thường -> Tia lửa đi qua tiếp tục
                flameSet.add(new Point(nx, ny));
            }
        }

        result.flameCells.addAll(flameSet);

        // 3. Thiêu hủy các vật phẩm đang nằm trên sàn nếu bị tia lửa quét trúng
        if (existingItems != null) {
            for (Item item : existingItems) {
                Point itemPos = new Point(item.getGridX(), item.getGridY());
                if (flameSet.contains(itemPos)) {
                    result.incineratedItems.add(item);
                }
            }
        }

        // 4. Kiểm tra va chạm với các Player
        for (Player player : players) {
            if (!player.isAlive()) continue;

            int px = player.getGridX();
            int py = player.getGridY();

            for (Point flame : result.flameCells) {
                if (flame.x == px && flame.y == py) {
                    boolean killed = player.takeDamage();
                    if (killed) {
                        result.killedPlayers.add(player);

                        // Cộng điểm nếu tiêu diệt được đối thủ khác mình
                        if (player.getId() != bomb.getOwnerId()) {
                            for (Player p : players) {
                                if (p.getId() == bomb.getOwnerId()) {
                                    p.addScore(100);
                                }
                            }
                        }
                    } else {
                        // Người chơi có khiên đỡ 1 lần sát thương!
                        result.shieldSavedPlayers.add(player);
                    }
                    break;
                }
            }
        }

        return result;
    }
}
