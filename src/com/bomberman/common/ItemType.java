package com.bomberman.common;

/**
 * Các loại vật phẩm bổ trợ (Buff Items) xuất hiện khi phá hủy gạch mềm.
 * Lấy cảm hứng từ Bomberman cổ điển và Bomb IT.
 */
public enum ItemType {
    BOMB_RANGE(1, "Thuốc tăng tầm nổ"),         // +1 ô tầm xa tia lửa nổ
    SPEED_BOOST(2, "Thuốc tăng tốc chạy"),       // +10% tốc độ di chuyển
    BOMB_COUNT(3, "Thuốc tăng số lượng bom"),    // +1 quả bom tối đa đặt cùng lúc
    SHIELD(4, "Khiên chắn bảo vệ"),             // Đỡ 1 lần sát thương bất kỳ (không cộng dồn)
    WEAPON_GUN(5, "Súng bắn Laser/Rocket");     // Vũ khí có số lần sử dụng giới hạn

    private final int id;
    private final String displayName;

    ItemType(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ItemType fromId(int id) {
        for (ItemType t : values()) {
            if (t.id == id) return t;
        }
        return null;
    }
}
