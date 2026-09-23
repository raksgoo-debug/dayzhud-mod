package com.dayzhud.mod.inventory.grid;

/** A footprint in grid cells. Width is the horizontal dimension. */
public record Footprint(int width, int height) {

    public static final Footprint SINGLE = new Footprint(1, 1);

    public Footprint rotated() {
        return new Footprint(height, width);
    }

    public boolean isMultiCell() {
        return width > 1 || height > 1;
    }
}
