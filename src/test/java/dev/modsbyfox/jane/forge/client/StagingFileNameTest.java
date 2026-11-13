package dev.modsbyfox.jane.forge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.modsbyfox.jane.core.Comparison;
import dev.modsbyfox.jane.core.Hashing;
import dev.modsbyfox.jane.core.ManifestEntry;
import dev.modsbyfox.jane.core.ResolutionPlan;
import dev.modsbyfox.jane.core.UpdatePlan;
import dev.modsbyfox.jane.core.WindowsBatch;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StagingFileNameTest {
    @TempDir Path game;

    private ResolutionPlan.Item trusted(String id, char hashDigit, String name) {
        ManifestEntry entry = new ManifestEntry(id, id, "1", 1, String.valueOf(hashDigit).repeat(128));
        Comparison.Result comparison = new Comparison.Result(entry, null, Comparison.Status.MISSING, null);
        return new ResolutionPlan.Item(comparison, ResolutionPlan.Availability.TRUSTED_AVAILABLE,
                new ResolutionPlan.Source(name, URI.create("https://cdn.modrinth.com/data/file.jar"), 1));
    }

    @Test void preservesUniqueOriginalNameAndAliasesBothDuplicateSources() throws Exception {
        Files.createDirectory(game.resolve("mods"));
        String original = "【安全工艺】SecurityCraft (1.20.1).jar";
        ResolutionPlan.Item unique = trusted("unique", 'a', original);
        ResolutionPlan.Item first = trusted("first", 'b', "Shared.jar");
        ResolutionPlan.Item second = trusted("second", 'c', "shared.jar");
        ResolutionPlan plan = new ResolutionPlan(List.of(unique, first, second));
        Map<String, String> names = StagingService.planFileNames(game, plan,
                Set.of("unique", "first", "second"), Set.of(), List.of());
        assertEquals(original, names.get("unique"));
        assertEquals(ResolutionPlan.serverSource(first.comparison().required()).name(), names.get("first"));
        assertEquals(ResolutionPlan.serverSource(second.comparison().required()).name(), names.get("second"));
        assertEquals(names, StagingService.planFileNames(game, plan,
                Set.of("unique", "first", "second"), Set.of(), List.of()));
        UpdatePlan.Operation install = new UpdatePlan.Operation("unique", UpdatePlan.Kind.ADD,
                null, null, original, unique.comparison().required().sha512(), 1);
        UpdatePlan update = new UpdatePlan("00000000-0000-0000-0000-000000000001", "a".repeat(64),
                "2026-11-13_17-00-00", List.of(install));
        assertTrue(WindowsBatch.generate(update, 123).contains("\"mods\\" + original + "\""));
    }

    @Test void aliasesUnrelatedLocalCollisionButKeepsOwnReplacementName() throws Exception {
        Path mods = Files.createDirectory(game.resolve("mods"));
        Files.writeString(mods.resolve("occupied.jar"), "other");
        ResolutionPlan.Item missing = trusted("missing", 'a', "occupied.jar");
        assertEquals(ResolutionPlan.serverSource(missing.comparison().required()).name(),
                StagingService.planFileNames(game, new ResolutionPlan(List.of(missing)),
                        Set.of("missing"), Set.of(), List.of()).get("missing"));

        Path own = Files.writeString(mods.resolve("Own Mod.jar"), "old");
        ManifestEntry entry = new ManifestEntry("replace", "replace", "2", 1, "b".repeat(128));
        Comparison.LocalMod local = new Comparison.LocalMod("replace", "1", own);
        Comparison.Result comparison = new Comparison.Result(entry, local,
                Comparison.Status.VERSION_MISMATCH, Hashing.sha512(own));
        ResolutionPlan.Item replacement = new ResolutionPlan.Item(comparison,
                ResolutionPlan.Availability.TRUSTED_AVAILABLE,
                new ResolutionPlan.Source("Own Mod.jar", URI.create("https://cdn.modrinth.com/data/file.jar"), 1));
        assertEquals("Own Mod.jar", StagingService.planFileNames(game,
                new ResolutionPlan(List.of(replacement)), Set.of("replace"),
                Set.of("own mod.jar"), List.of()).get("replace"));
    }

    @Test void keepsAlreadyStagedNameAndRejectsConflictingAlias() throws Exception {
        Path mods = Files.createDirectory(game.resolve("mods"));
        ResolutionPlan.Item item = trusted("example", 'a', "Original.jar");
        String alias = ResolutionPlan.serverSource(item.comparison().required()).name();
        UpdatePlan.Operation staged = new UpdatePlan.Operation("example", UpdatePlan.Kind.ADD,
                null, null, alias, item.comparison().required().sha512(), 1);
        assertEquals(alias, StagingService.planFileNames(game, new ResolutionPlan(List.of(item)),
                Set.of("example"), Set.of(), List.of(staged)).get("example"));

        Files.writeString(mods.resolve("Original.jar"), "other");
        Files.writeString(mods.resolve(alias), "alias occupied");
        String chosen = StagingService.planFileNames(game, new ResolutionPlan(List.of(item)),
                Set.of("example"), Set.of(), List.of()).get("example");
        assertEquals(alias, chosen);
        assertThrows(IOException.class, () -> StagingService.prepareOperation(game, item, chosen,
                Map.of(chosen.toLowerCase(java.util.Locale.ROOT), 1), Set.of()));
    }
}
