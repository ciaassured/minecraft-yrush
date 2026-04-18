package io.github.ciaassured.yrush;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

public final class TargetYSelector {
    private final Random random;

    public TargetYSelector(Random random) {
        this.random = random;
    }

    public OptionalInt select(World world, int startY, int minimumDistance, int maximumDistance) {
        return select(world, startY, minimumDistance, maximumDistance, TargetDirectionPreference.ANY);
    }

    public OptionalInt select(
        World world,
        int startY,
        int minimumDistance,
        int maximumDistance,
        TargetDirectionPreference preference
    ) {
        int minWorldY = world.getMinHeight();
        int maxWorldY = world.getMaxHeight() - 1;

        List<Range> ranges = new ArrayList<>();

        int downMin = Math.max(minWorldY, startY - maximumDistance);
        int downMax = startY - minimumDistance;
        if (downMin <= downMax) {
            ranges.add(new Range(downMin, downMax));
        }

        if (preference != TargetDirectionPreference.DOWN_ONLY) {
            int upMin = startY + minimumDistance;
            int upMax = Math.min(maxWorldY, startY + maximumDistance);
            if (upMin <= upMax) {
                ranges.add(new Range(upMin, upMax));
            }
        }

        if (ranges.isEmpty()) {
            return OptionalInt.empty();
        }

        int totalValues = ranges.stream().mapToInt(Range::size).sum();
        int selected = random.nextInt(totalValues);
        for (Range range : ranges) {
            if (selected < range.size()) {
                return OptionalInt.of(range.min + selected);
            }
            selected -= range.size();
        }

        return OptionalInt.empty();
    }

    private record Range(int min, int max) {
        int size() {
            return max - min + 1;
        }
    }
}
