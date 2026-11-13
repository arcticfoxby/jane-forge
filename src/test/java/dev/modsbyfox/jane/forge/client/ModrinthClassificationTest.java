package dev.modsbyfox.jane.forge.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.modsbyfox.jane.core.ClientInstallCategory;
import dev.modsbyfox.jane.core.ManifestEntry;
import dev.modsbyfox.jane.core.ResolutionPlan;
import dev.modsbyfox.jane.core.UpdatePlan;
import dev.modsbyfox.jane.core.WindowsBatch;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModrinthClassificationTest {
    private static final String HASH = "a".repeat(128);
    private static final ManifestEntry TARGET = new ManifestEntry("example", "Example", "1", 123, HASH);

    private static JsonObject version(String hash) {
        return version(hash, "forge");
    }

    private static JsonObject version(String hash, String loader) {
        return JsonParser.parseString("""
                {"project_id":"A1b2C3d4", "game_versions":["1.20.1"], "loaders":["%s"],
                 "files":[{"hashes":{"sha512":"%s"}, "size":123,
                           "filename":"example.jar", "url":"https://cdn.modrinth.com/data/example.jar"}]}
                """.formatted(loader, hash)).getAsJsonObject();
    }

    private static JsonObject project(String id, String clientSide) {
        return JsonParser.parseString("{" + "\"id\":\"" + id + "\",\"client_side\":\"" + clientSide + "\"}").getAsJsonObject();
    }

    @Test void exactHashAndMatchingProjectOptionalAdviceCanClassifyOptional() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ModrinthService service = new ModrinthService(uri -> {
            calls.incrementAndGet();
            return Optional.of(uri.getPath().startsWith("/v2/version_file/")
                    ? version(HASH) : project("A1b2C3d4", "optional"));
        });
        ModrinthService.ClassifiedSource result = service.findClassified(TARGET);
        assertEquals(ClientInstallCategory.CLIENT_OPTIONAL, result.category());
        assertTrue(result.source().isPresent());
        assertEquals(2, calls.get());
    }

    @Test void unverifiedSameNameProjectCannotClassifyOptional() {
        AtomicInteger calls = new AtomicInteger();
        ModrinthService service = new ModrinthService(uri -> {
            calls.incrementAndGet();
            return Optional.of(version("b".repeat(128)));
        });
        assertThrows(IOException.class, () -> service.findClassified(TARGET));
        assertEquals(1, calls.get());
    }

    @Test void exactFabricFileIsNotAcceptedForForge() {
        ModrinthService service = new ModrinthService(uri -> Optional.of(version(HASH, "fabric")));
        assertThrows(IOException.class, () -> service.findClassified(TARGET));
    }

    @Test void projectLookupFailureAndConflictingMetadataFallBackToRequired() throws Exception {
        ModrinthService networkFailure = new ModrinthService(uri -> {
            if (uri.getPath().startsWith("/v2/project/")) throw new IOException("offline");
            return Optional.of(version(HASH));
        });
        var fallback = networkFailure.findClassified(TARGET);
        assertTrue(fallback.source().isPresent());
        assertEquals(ClientInstallCategory.SERVER_REQUIRED, fallback.category());

        ModrinthService conflicting = new ModrinthService(uri -> Optional.of(
                uri.getPath().startsWith("/v2/project/") ? project("Other123", "optional") : version(HASH)));
        assertEquals(ClientInstallCategory.SERVER_REQUIRED, conflicting.findClassified(TARGET).category());
    }

    @Test void noExactVersionIsRequiredWithoutGuessingByName() throws Exception {
        ModrinthService service = new ModrinthService(uri -> Optional.empty());
        var result = service.findClassified(TARGET);
        assertTrue(result.source().isEmpty());
        assertEquals(ClientInstallCategory.SERVER_REQUIRED, result.category());
    }

    @Test void safeUnicodeModrinthFilenameIsPreservedThroughUpdaterPlan() throws Exception {
        String filename = "[安全工艺]SecurityCraft (1.20.1).jar";
        JsonObject response = version(HASH);
        response.getAsJsonArray("files").get(0).getAsJsonObject().addProperty("filename", filename);
        ResolutionPlan.Source source = new ModrinthService(uri -> Optional.of(response)).find(TARGET).orElseThrow();
        assertEquals(filename, source.name());
        UpdatePlan plan = new UpdatePlan("12345678-1234-1234-1234-123456789012", "b".repeat(64),
                "2026-11-13_12-00-00", List.of(new UpdatePlan.Operation("example", UpdatePlan.Kind.ADD,
                null, null, source.name(), HASH, TARGET.fileSize())));
        assertTrue(WindowsBatch.generate(plan, 1234).contains("mods\\" + filename + "\""));
    }

    @Test void unsafeModrinthFilenameUsesHashBoundLocalAlias() throws Exception {
        JsonObject response = version(HASH);
        response.getAsJsonArray("files").get(0).getAsJsonObject().addProperty("filename", "../evil%name.jar");
        ResolutionPlan.Source source = new ModrinthService(uri -> Optional.of(response)).find(TARGET).orElseThrow();
        assertEquals(ResolutionPlan.serverSource(TARGET).name(), source.name());

        response.getAsJsonArray("files").get(0).getAsJsonObject().addProperty("url", "https://example.com/evil.jar");
        assertThrows(IOException.class, () -> new ModrinthService(uri -> Optional.of(response)).find(TARGET));
    }

    @Test void missingOrMalformedModrinthFilenameIsNotAnAliasCandidate() {
        JsonObject response = version(HASH);
        response.getAsJsonArray("files").get(0).getAsJsonObject().remove("filename");
        assertThrows(IOException.class, () -> new ModrinthService(uri -> Optional.of(response)).find(TARGET));
        response.getAsJsonArray("files").get(0).getAsJsonObject().addProperty("filename", 12);
        assertThrows(IOException.class, () -> new ModrinthService(uri -> Optional.of(response)).find(TARGET));
    }
}

