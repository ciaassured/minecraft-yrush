package io.github.ciaassured.yrush.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

final class StartLocationServiceTest {
    @Test
    void fullServerReceivesUniqueStartsInsideTheLoadedChunk() {
        World world = mock(World.class);
        SafeLocationValidator validator = mock(SafeLocationValidator.class);
        when(validator.isSafe(any(Location.class))).thenReturn(true);
        StartLocationService service = new StartLocationService(new Random(1L), validator, null);
        Location center = new Location(world, 8.5, 80.0, 8.5);

        List<Location> positions = service.findPlayerPositions(center, 128);

        assertEquals(128, positions.size());
        assertEquals(128, new HashSet<>(positions).size());
        assertTrue(positions.stream().allMatch(location -> location.getBlockX() >> 4 == 0));
        assertTrue(positions.stream().allMatch(location -> location.getBlockZ() >> 4 == 0));
    }
}
