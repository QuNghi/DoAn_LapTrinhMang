package com.bomberman.server.model;

import com.bomberman.common.Direction;

/**
 * Quản lý trạng thái và thông tin của từng người chơi trên Server.
 */
public class Player {
    private final int id;
    private final String name;
    private double x;
    private double y;
    private Direction direction;
    private boolean alive;
    private int score;
    private int maxBombs;
    private int currentBombs;
    private int bombRadius;

    public Player(int id, String name, double startX, double startY) {
        this.id = id;
        this.name = name;
        this.x = startX;
        this.y = startY;
        this.direction = Direction.STOP;
        this.alive = true;
        this.score = 0;
        this.maxBombs = 1;
        this.currentBombs = 0;
        this.bombRadius = 2;
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

    /**
     * Định dạng chuỗi tóm tắt người chơi để gửi trong gói tin STATE.
     * Cú pháp: id,x,y,alive,score,direction
     */
    public String toStateString() {
        return String.format("%d,%.2f,%.2f,%d,%d,%s",
                id, x, y, alive ? 1 : 0, score, direction.name());
    }
}
