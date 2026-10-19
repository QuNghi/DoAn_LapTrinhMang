package com.bomberman.server.model;

import com.bomberman.common.ItemType;

/**
 * Đại diện cho một vật phẩm đang nằm trên sàn đấu chờ người chơi nhặt.
 */
public class Item {
    private final String id;
    private final ItemType type;
    private final int gridX;
    private final int gridY;

    public Item(String id, ItemType type, int gridX, int gridY) {
        this.id = id;
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
    }

    public String getId() {
        return id;
    }

    public ItemType getType() {
        return type;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    /**
     * Chuỗi đại diện gửi cho Client: id,typeCode,gridX,gridY
     */
    public String toStateString() {
        return String.format("%s,%d,%d,%d", id, type.getId(), gridX, gridY);
    }
}
