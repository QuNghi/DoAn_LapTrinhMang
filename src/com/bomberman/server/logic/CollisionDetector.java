package com.bomberman.server.logic;

import com.bomberman.common.GameConstants;
import com.bomberman.common.TileType;
import com.bomberman.server.model.Bomb;
import com.bomberman.server.model.GameMap;

import java.util.List;

/**
 * Thuật toán phát hiện và xử lý va chạm trên Server (Authoritative Server).
 */
public class CollisionDetector {

    /**
     * Kiểm tra xem nhân vật có thể di chuyển tới tọa độ (newX, newY) hay không.
     * Sử dụng bounding box thu nhỏ để tránh bị kẹt góc khi rẽ.
     *
     * @param map bản đồ game
     * @param bombs danh sách các quả bom đang tồn tại
     * @param oldX tọa độ x hiện tại
     * @param oldY tọa độ y hiện tại
     * @param newX tọa độ x dự kiến
     * @param newY tọa độ y dự kiến
     * @return true nếu vị trí mới hoàn toàn hợp lệ
     */
    public static boolean canMove(GameMap map, List<Bomb> bombs, double oldX, double oldY, double newX, double newY) {
        // Kiểm tra biên bản đồ
        if (newX < 0 || newX >= map.getWidth() || newY < 0 || newY >= map.getHeight()) {
            return false;
        }

        // Kích thước nửa thân nhân vật để kiểm tra va chạm góc (Bounding Box 0.55 ô)
        double halfSize = 0.28;

        double minX = newX - halfSize;
        double maxX = newX + halfSize;
        double minY = newY - halfSize;
        double maxY = newY + halfSize;

        int leftCol = (int) Math.floor(minX);
        int rightCol = (int) Math.floor(maxX);
        int topRow = (int) Math.floor(minY);
        int bottomRow = (int) Math.floor(maxY);

        // Kiểm tra tất cả các ô mà thân nhân vật đang lấn vào
        for (int row = topRow; row <= bottomRow; row++) {
            for (int col = leftCol; col <= rightCol; col++) {
                int tile = map.getTile(col, row);

                // Không thể đi xuyên qua Tường cứng hoặc Gạch mềm
                if (tile == TileType.HARD_WALL.getCode() || tile == TileType.SOFT_BRICK.getCode()) {
                    return false;
                }

                // Kiểm tra va chạm với quả bom
                if (isBlockedByBomb(bombs, oldX, oldY, col, row)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Kiểm tra va chạm với Bom:
     * - Nếu tại ô (col, row) có bom, nhưng vị trí cũ (oldX, oldY) người chơi cũng đang đứng trúng ô đó
     *   (người chơi vừa đặt bom xong và đang bước ra) -> CHO PHÉP ĐI TIẾP.
     * - Nếu người chơi ở ngoài ô bom muốn đi vào -> CHẶN LẠI.
     */
    private static boolean isBlockedByBomb(List<Bomb> bombs, double oldX, double oldY, int col, int row) {
        if (bombs == null) return false;

        for (Bomb bomb : bombs) {
            if (bomb.getGridX() == col && bomb.getGridY() == row && !bomb.isExploded()) {
                // Kiểm tra xem vị trí cũ có đang ở trong ô bom này không
                int oldGridX = (int) Math.round(oldX);
                int oldGridY = (int) Math.round(oldY);

                if (oldGridX == col && oldGridY == row) {
                    // Đang bước ra khỏi ô bom vừa đặt -> Cho phép
                    return false;
                }
                // Đi từ ô khác vào ô có bom -> Chặn lại
                return true;
            }
        }
        return false;
    }
}
