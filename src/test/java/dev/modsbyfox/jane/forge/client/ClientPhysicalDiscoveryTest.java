package dev.modsbyfox.jane.forge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientPhysicalDiscoveryTest {
    @Test
    void onlyUniformClientDisplayTestsAllowClientClassification() {
        assertEquals(ModEnvironment.CLIENT, ClientPhysicalDiscovery.classifyDisplayTests(
                List.of("IGNORE_ALL_VERSION", "IGNORE_ALL_VERSION")));
        assertEquals(ModEnvironment.UNIVERSAL, ClientPhysicalDiscovery.classifyDisplayTests(
                List.of("IGNORE_ALL_VERSION", "MATCH_VERSION")));
    }

    @Test
    void onlyUniformServerDisplayTestsSuppressExtraModPrompt() {
        assertEquals(ModEnvironment.SERVER, ClientPhysicalDiscovery.classifyDisplayTests(
                List.of("IGNORE_SERVER_VERSION", "IGNORE_SERVER_VERSION")));
        assertEquals(ModEnvironment.UNIVERSAL, ClientPhysicalDiscovery.classifyDisplayTests(
                List.of("IGNORE_SERVER_VERSION", "IGNORE_ALL_VERSION")));
    }

    @Test
    void unknownOrMissingDisplayTestsRemainUniversal() {
        assertEquals(ModEnvironment.UNIVERSAL, ClientPhysicalDiscovery.classifyDisplayTests(List.of()));
        assertEquals(ModEnvironment.UNIVERSAL, ClientPhysicalDiscovery.classifyDisplayTests(
                Arrays.asList("IGNORE_ALL_VERSION", null)));
    }
}
