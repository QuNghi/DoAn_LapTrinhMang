package com.bomberman.server.model;

import com.bomberman.common.Direction;

/**
 * Đại diện cho viên đạn súng bắn ra trong trận đấu (Authoritative Server).
 * Có tọa độ riêng, vận tốc riêng và phương thức cập nhật theo tick.
 * Chỉ gây sát thương khi viên đạn thực sự chạm vào mục tiêu.
 */
public class Bullet {
    private final String id;
    private final int shooterId;
    private double x;
    private double y;
    private final Direction direction;
    private final double speed; // Tốc độ di chuyển mỗi tick (khoảng 0.40 ô/tick ~ 12 ô/giây)
    private final double maxDistance;
    private double traveledDistance;
    private boolean active;

    public Bullet(String id, int shooterId, double startX, double startY, Direction direction, double speed, double maxDistance) {
        this.id = id;
        this.shooterId = shooterId;
        this.x = startX;
        this.y = startY;
        this.direction = direction;
        this.speed = speed;
        this.maxDistance = maxDistance;
        this.traveledDistance = 0;
        this.active = true;
    }

    /**
     * Cập nhật vị trí viên đạn mỗi tick.
     */
    public void update() {
        if (!active) return;
        x += direction.getDx() * speed;
        y += direction.getDy() * speed;
        traveledDistance += speed;
        if (traveledDistance >= maxDistance) {
            active = false;
        }
    }

    public String getId() {
        return id;
    }

    public int getShooterId() {
        return shooterId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getGridX() {
        return (int) Math.round(x);
    }

    public int getGridY() {
        return (int) Math.round(y);
    }

    public Direction getDirection() {
        return direction;
    }

    public double getSpeed() {
        return speed;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
