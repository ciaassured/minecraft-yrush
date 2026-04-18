package io.github.ciaassured.yrush.location;

public enum StartCategory {
    SURFACE_DRY,
    SURFACE_WATER,
    UNDERGROUND_DRY,
    UNDERGROUND_WATER;

    public static StartCategory from(StartType type, boolean water) {
        return switch (type) {
            case SURFACE -> water ? SURFACE_WATER : SURFACE_DRY;
            case UNDERGROUND -> water ? UNDERGROUND_WATER : UNDERGROUND_DRY;
        };
    }

    public boolean isWater() {
        return this == SURFACE_WATER || this == UNDERGROUND_WATER;
    }
}
