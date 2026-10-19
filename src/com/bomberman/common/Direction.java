package com.bomberman.common;

/**
 * Hướng di chuyển của người chơi.
 */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    STOP(0, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public static Direction fromString(String str) {
        if (str == null) return STOP;
        try {
            return Direction.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STOP;
        }
    }
}
