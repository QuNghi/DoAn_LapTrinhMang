package com.bomberman.server.model;

/**
 * Đại diện cho một quả bom được đặt trên sân đấu.
 */
public class Bomb {
    private final String id;
    private final int ownerId;
    private final int gridX;
    private final int gridY;
    private long remainingMs;
    private final int radius;
    private boolean exploded;

    public Bomb(String id, int ownerId, int gridX, int gridY, long countdownMs, int radius) {
        this.id = id;
        this.ownerId = ownerId;
        this.gridX = gridX;
        this.gridY = gridY;
        this.remainingMs = countdownMs;
        this.radius = radius;
        this.exploded = false;
    }

    public String getId() {
        return id;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public long getRemainingMs() {
        return remainingMs;
    }

    public int getRadius() {
        return radius;
    }

    public boolean isExploded() {
        return exploded;
    }

    /**
     * Cập nhật bộ đếm thời gian của quả bom.
     * @param deltaMs thời gian trôi qua giữa 2 tick
     * @return true nếu bom vừa đếm hết giờ và cần kích nổ
     */
    public boolean update(long deltaMs) {
        if (exploded) return false;
        remainingMs -= deltaMs;
        if (remainingMs <= 0) {
            exploded = true;
            return true;
        }
        return false;
    }

    public void triggerExplosion() {
        this.exploded = true;
        this.remainingMs = 0;
    }

    /**
     * Định dạng chuỗi tóm tắt bom để gửi trong gói tin STATE.
     * Cú pháp: id,gridX,gridY,countdownMs
     */
    public String toStateString() {
        return String.format("%s,%d,%d,%d", id, gridX, gridY, Math.max(0, remainingMs));
    }
}
