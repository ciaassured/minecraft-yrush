package io.github.ciaassured.yrush.game;

public enum RunMode {
    SINGLE(false),
    AUTO(true),
    TRAINING(true);

    private final boolean repeating;

    RunMode(boolean repeating) {
        this.repeating = repeating;
    }

    public boolean isRepeating() {
        return repeating;
    }

    public boolean isTraining() {
        return this == TRAINING;
    }

    public String label() {
        return name().toLowerCase();
    }
}
