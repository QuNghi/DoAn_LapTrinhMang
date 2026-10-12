package com.bomberman.server.logic;

import com.bomberman.common.Direction;
import com.bomberman.common.TileType;
import com.bomberman.server.model.Bomb;
import com.bomberman.server.model.GameMap;
import com.bomberman.server.model.Player;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Xử lý thuật toán lan truyền tia lửa bom nổ, phá hủy tường mềm và tiêu diệt người chơi.
 */
public class ExplosionHandler {

    public static class ExplosionResult {
        public final List<Point> flameCells = new ArrayList<>();
        public final List<Point> destroyedBricks = new ArrayList<>();
        public final List<Player> killedPlayers = new ArrayList<>();
        public final List<Bomb> triggeredBombs = new ArrayList<>();
    }

    /**
     * Tính toán toàn bộ hiệu ứng và va chạm của một vụ nổ bom.
     *
     * @param map bản đồ game
     * @param bombs danh sách các quả bom trên sân (để kích nổ dây chuyền)
     * @param players danh sách tất cả người chơi
     * @param bomb quả bom phát nổ
     * @return ExplosionResult chứa danh sách ô lửa, gạch vỡ, người chơi bị hạ gục
     */
    public static ExplosionResult processExplosion(GameMap map, List<Bomb> bombs, List<Player> players, Bomb bomb) {
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

        // 3. Kiểm tra va chạm với các Player
        for (Player player : players) {
            if (!player.isAlive()) continue;

            int px = player.getGridX();
            int py = player.getGridY();

            for (Point flame : result.flameCells) {
                if (flame.x == px && flame.y == py) {
                    player.setAlive(false);
                    result.killedPlayers.add(player);

                    // Cộng điểm nếu tiêu diệt được đối thủ khác mình
                    if (player.getId() != bomb.getOwnerId()) {
                        for (Player p : players) {
                            if (p.getId() == bomb.getOwnerId()) {
                                p.addScore(100);
                            }
                        }
                    }
                    break;
                }
            }
        }

        return result;
    }
}
