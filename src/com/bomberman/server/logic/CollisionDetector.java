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
        // Tọa độ tâm nhân vật nằm ở giữa ô: (x + 0.5, y + 0.5)
        double centerX = newX + 0.5;
        double centerY = newY + 0.5;

        // Bán kính va chạm 0.27 (đã giảm 10% để dễ luồn lách qua các góc cua và khe gạch)
        double halfSize = 0.27;

        double minX = centerX - halfSize;
        double maxX = centerX + halfSize;
        double minY = centerY - halfSize;
        double maxY = centerY + halfSize;

        // Kiểm tra biên bản đồ
        if (minX < 0 || maxX >= map.getWidth() || minY < 0 || maxY >= map.getHeight()) {
            return false;
        }

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
                if (isBlockedByBomb(bombs, oldX, oldY, col, row, halfSize)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Kiểm tra va chạm với Bom:
     * - Nếu tại vị trí cũ (oldX, oldY) nhân vật đã đang đứng chạm vào ô bom này rồi
     *   (người chơi vừa đặt bom xong và đang bước ra) -> CHO PHÉP ĐI TIẾP.
     * - Nếu người chơi ở ngoài ô bom muốn đi vào -> CHẶN LẠI.
     */
    private static boolean isBlockedByBomb(List<Bomb> bombs, double oldX, double oldY, int col, int row, double halfSize) {
        if (bombs == null) return false;

        for (Bomb bomb : bombs) {
            if (bomb.getGridX() == col && bomb.getGridY() == row && !bomb.isExploded()) {
                // Kiểm tra xem vị trí cũ (oldX, oldY) có đang đè/lấn vào ô bom này không
                double oldCenterX = oldX + 0.5;
                double oldCenterY = oldY + 0.5;
                double oldMinX = oldCenterX - halfSize;
                double oldMaxX = oldCenterX + halfSize;
                double oldMinY = oldCenterY - halfSize;
                double oldMaxY = oldCenterY + halfSize;

                boolean oldOverlapped = (oldMinX < (col + 1.0) && oldMaxX > col &&
                                         oldMinY < (row + 1.0) && oldMaxY > row);

                if (oldOverlapped) {
                    // Đang bước ra khỏi ô bom vừa đặt -> Cho phép tiếp tục đi ra
                    return false;
                }
                // Đi từ ô khác vào ô có bom -> Chặn lại
                return true;
            }
        }
        return false;
    }
}
