package io.github.ciaassured.yrush.game;

import io.github.ciaassured.yrush.config.YRushConfig;

public record RoundOptions(
    RunMode runMode,
    int preTeleportDelayTicks,
    int lockedCountdownTicks,
    boolean showGetReadyMessage,
    boolean showCountdownMessages,
    boolean showRoundStartMessages,
    boolean showActionBar
) {
    private static final int NORMAL_PRE_TELEPORT_DELAY_TICKS = 100;
    private static final int TRAINING_LOCKED_COUNTDOWN_TICKS = 1;

    public static RoundOptions from(YRushConfig config, RunMode runMode) {
        if (runMode.isTraining()) {
            return new RoundOptions(runMode, 0, TRAINING_LOCKED_COUNTDOWN_TICKS, false, false, false, false);
        }
        return new RoundOptions(runMode, NORMAL_PRE_TELEPORT_DELAY_TICKS, config.countdownSeconds() * 20, true, true, true, true);
    }

    public int lockedCountdownEffectSeconds() {
        return Math.max(1, (int) Math.ceil(lockedCountdownTicks / 20.0));
    }
}
