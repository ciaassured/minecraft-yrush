package io.github.ciaassured.yrush;

public enum RoundDirection {
    UP("CLIMB TO", "Climb"),
    DOWN("DIG DOWN TO", "Dig down");

    private final String titlePrefix;
    private final String label;

    RoundDirection(String titlePrefix, String label) {
        this.titlePrefix = titlePrefix;
        this.label = label;
    }

    public String titlePrefix() {
        return titlePrefix;
    }

    public String label() {
        return label;
    }
}

