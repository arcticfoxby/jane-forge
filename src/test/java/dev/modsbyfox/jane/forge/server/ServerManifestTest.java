package dev.modsbyfox.jane.forge.server;

import dev.modsbyfox.jane.core.PathSafety;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerManifestTest {
    @TempDir Path gameDir;

    @Test
    void blacklistRequiresAnExactCopy() throws Exception {
        Path mods = Files.createDirectory(gameDir.resolve("mods"));
        Path jar = Files.write(mods.resolve("sample.jar"), new byte[] {1, 2, 3, 4});
        var discovered = List.of(new ServerDiscovery.Discovered(
                new ServerDiscovery.Candidate("sample", "Sample", "1.0", List.of("sample"), false), jar));

        assertEquals(1, ServerManifest.build(discovered, gameDir).manifest().entries().size());

        Path blacklist = PathSafety.janeDirectory(gameDir, "blacklist");
        Files.copy(jar, blacklist.resolve(jar.getFileName()));
        assertEquals(0, ServerManifest.build(discovered, gameDir).manifest().entries().size());

        Files.write(blacklist.resolve(jar.getFileName()), new byte[] {1, 2, 3, 5});
        assertEquals(1, ServerManifest.build(discovered, gameDir).manifest().entries().size());
    }

    @Test
    void bundledJaneJarIsExcluded() throws Exception {
        Path mods = Files.createDirectory(gameDir.resolve("mods"));
        Path jar = Files.write(mods.resolve("jane.jar"), new byte[] {1});
        var discovered = List.of(new ServerDiscovery.Discovered(
                new ServerDiscovery.Candidate("other", "Other", "1.0", List.of("other", "jane"), false), jar));

        assertEquals(0, ServerManifest.build(discovered, gameDir).manifest().entries().size());
    }

    @Test
    void explicitlyServerOnlyJarIsExcluded() throws Exception {
        Path mods = Files.createDirectory(gameDir.resolve("mods"));
        Path jar = Files.write(mods.resolve("server.jar"), new byte[] {1});
        var discovered = List.of(new ServerDiscovery.Discovered(
                new ServerDiscovery.Candidate("servermod", "Server Mod", "1.0",
                        List.of("servermod"), true), jar));

        assertEquals(0, ServerManifest.build(discovered, gameDir).manifest().entries().size());
    }
}
