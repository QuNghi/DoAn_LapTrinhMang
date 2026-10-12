package com.bomberman.common;

/**
 * Định nghĩa loại ô trên ma trận bản đồ.
 */
public enum TileType {
    EMPTY(0),      // Ô trống, có thể đi xuyên qua
    HARD_WALL(1),  // Tường cứng bê tông, không thể phá hủy, không thể đi qua
    SOFT_BRICK(2), // Gạch mềm, bị phá hủy bởi bom nổ, không thể đi qua
    BOMB(3);       // Ô đang có bom đặt

    private final int code;

    TileType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TileType fromCode(int code) {
        for (TileType t : values()) {
            if (t.code == code) return t;
        }
        return EMPTY;
    }
}
