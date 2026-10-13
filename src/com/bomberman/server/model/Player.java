package com.bomberman.server.model;

import com.bomberman.common.Direction;
import com.bomberman.common.ItemType;

/**
 * Quản lý trạng thái và thông tin của từng người chơi trên Server.
 */
public class Player {
    private final int id;
    private final String name;
    private double x;
    private double y;
    private Direction direction;
    private Direction lastFacingDirection;
    private boolean alive;
    private int score;
    private int maxBombs;
    private int currentBombs;
    private int bombRadius;
    private double speed;
    private boolean hasShield;
    private ItemType weapon;
    private int weaponAmmo;

    public Player(int id, String name, double startX, double startY) {
        this.id = id;
        this.name = name;
        this.x = startX;
        this.y = startY;
        this.direction = Direction.STOP;
        this.lastFacingDirection = Direction.DOWN;
        this.alive = true;
        this.score = 0;
        this.maxBombs = 1;
        this.currentBombs = 0;
        this.bombRadius = 2;
        this.speed = com.bomberman.common.GameConstants.PLAYER_SPEED;
        this.hasShield = false;
        this.weapon = null;
        this.weaponAmmo = 0;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
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

    public void setDirection(Direction direction) {
        this.direction = direction;
        if (direction != Direction.STOP) {
            this.lastFacingDirection = direction;
        }
    }

    public Direction getLastFacingDirection() {
        return lastFacingDirection != null ? lastFacingDirection : Direction.DOWN;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
        if (!alive) {
            this.direction = Direction.STOP;
        }
    }

    public int getScore() {
        return score;
    }

    public void addScore(int points) {
        this.score += points;
    }

    public boolean canPlaceBomb() {
        return alive && currentBombs < maxBombs;
    }

    public void onBombPlaced() {
        currentBombs++;
    }

    public void onBombExploded() {
        if (currentBombs > 0) {
            currentBombs--;
        }
    }

    public int getBombRadius() {
        return bombRadius;
    }

    public void setBombRadius(int bombRadius) {
        this.bombRadius = bombRadius;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public int getMaxBombs() {
        return maxBombs;
    }

    public boolean hasShield() {
        return hasShield;
    }

    public void setShield(boolean hasShield) {
        this.hasShield = hasShield;
    }

    public ItemType getWeapon() {
        return weapon;
    }

    public int getWeaponAmmo() {
        return weaponAmmo;
    }

    /**
     * Nhặt và áp dụng hiệu ứng vật phẩm buff / vũ khí.
     * @param type loại vật phẩm
     * @return true nếu hiệu ứng được kích hoạt thành công (khiên không cộng dồn trả về false nếu đã có)
     */
    public boolean applyItem(ItemType type) {
        if (type == null) return false;
        switch (type) {
            case BOMB_RANGE:
                this.bombRadius++;
                return true;

            case SPEED_BOOST:
                this.speed = Math.min(com.bomberman.common.GameConstants.MAX_PLAYER_SPEED,
                        this.speed * (1.0 + com.bomberman.common.GameConstants.SPEED_BOOST_PERCENT));
                return true;

            case BOMB_COUNT:
                this.maxBombs++;
                return true;

            case SHIELD:
                // Người chơi chỉ có thể sở hữu cùng lúc 1 khiên, nhặt thêm không tính
                if (this.hasShield) {
                    return false;
                }
                this.hasShield = true;
                return true;

            case WEAPON_GUN:
                this.weapon = ItemType.WEAPON_GUN;
                this.weaponAmmo = com.bomberman.common.GameConstants.WEAPON_DEFAULT_AMMO;
                return true;

            default:
                return false;
        }
    }

    /**
     * Nhận sát thương (do bom hoặc súng vũ khí):
     * - Nếu có khiên: Khiên vỡ, người chơi không chết.
     * - Nếu không có khiên: Người chơi bị tiêu diệt.
     * @return true nếu người chơi chết; false nếu khiên đã đỡ đòn thành công
     */
    public boolean takeDamage() {
        if (hasShield) {
            hasShield = false; // Phá hủy khiên
            return false; // Thoát chết
        }
        setAlive(false);
        return true; // Bị hạ gục
    }

    /**
     * Sử dụng vũ khí (bắn súng).
     * @return true nếu bắn thành công (còn đạn)
     */
    public boolean useWeapon() {
        if (weapon != null && weaponAmmo > 0) {
            weaponAmmo--;
            if (weaponAmmo <= 0) {
                weapon = null;
            }
            return true;
        }
        return false;
    }

    /**
     * Định dạng chuỗi tóm tắt người chơi để gửi trong gói tin STATE.
     * Cú pháp: id,x,y,alive,score,direction,hasShield,weaponAmmo
     */
    public String toStateString() {
        return String.format(java.util.Locale.US, "%d,%.2f,%.2f,%d,%d,%s,%d,%d",
                id, x, y, alive ? 1 : 0, score, direction.name(), hasShield ? 1 : 0, weaponAmmo);
    }
}
