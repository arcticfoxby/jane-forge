package dev.modsbyfox.jane.forge.client;

import dev.modsbyfox.jane.core.ClientCompatibilityReport;
import dev.modsbyfox.jane.core.Comparison;
import dev.modsbyfox.jane.core.PathSafety;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

/** Uses Forge's resolved Mod metadata, limited to direct JARs in this instance's mods directory. */
public final class ClientPhysicalDiscovery {
    public record PhysicalClientMod(String modId, String displayName, String version,
                                    ModEnvironment environment, Path jar,
                                    ClientCompatibilityReport.FileIdentity fileIdentity) { }

    public record Discovery(Map<String, List<Comparison.LocalMod>> candidates, List<PhysicalClientMod> all) {
        public Discovery {
            candidates = Map.copyOf(candidates);
            all = List.copyOf(all);
        }
    }

    private ClientPhysicalDiscovery() { }

    public static Discovery scan(Path gameDir) throws IOException {
        Path root = gameDir.toRealPath();
        Path mods = root.resolve("mods");
        if (!Files.exists(mods, LinkOption.NOFOLLOW_LINKS)) return new Discovery(Map.of(), List.of());
        if (Files.isSymbolicLink(mods) || !Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)
                || !mods.toRealPath().getParent().equals(root))
            throw new IOException("mods directory escaped current instance");

        Map<Path, List<IModInfo>> byJar = new TreeMap<>();
        for (IModInfo info : ModList.get().getMods()) {
            Path path = info.getOwningFile().getFile().getFilePath().toAbsolutePath().normalize();
            if (!mods.equals(path.getParent())
                    || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            Path safe = PathSafety.existingJarInMods(root, path);
            byJar.computeIfAbsent(safe, ignored -> new ArrayList<>()).add(info);
        }

        Map<String, List<Comparison.LocalMod>> candidates = new TreeMap<>();
        List<PhysicalClientMod> all = new ArrayList<>();
        for (var group : byJar.entrySet()) {
            // One physical JAR is one install unit even when its mods.toml declares several IDs.
            IModInfo representative = group.getValue().stream()
                    .min(Comparator.comparing(IModInfo::getModId)).orElseThrow();
            Path jar = group.getKey();
            ClientCompatibilityReport.FileIdentity identity = identityOrNull(jar);
            ModEnvironment environment = classifyDisplayTests(group.getValue().stream()
                    .map(info -> info.getConfig().getConfigElement("displayTest")
                            .map(String::valueOf).orElse(""))
                    .toList());
            PhysicalClientMod physical = new PhysicalClientMod(representative.getModId(),
                    representative.getDisplayName(), representative.getVersion().toString(),
                    environment, jar, identity);
            all.add(physical);
            candidates.computeIfAbsent(physical.modId(), ignored -> new ArrayList<>())
                    .add(new Comparison.LocalMod(physical.modId(), physical.version(), jar));
        }
        return new Discovery(candidates, all);
    }

    static ModEnvironment classifyDisplayTests(List<String> displayTests) {
        if (displayTests == null || displayTests.isEmpty()) return ModEnvironment.UNIVERSAL;
        if (displayTests.stream().allMatch("IGNORE_ALL_VERSION"::equals)) return ModEnvironment.CLIENT;
        if (displayTests.stream().allMatch("IGNORE_SERVER_VERSION"::equals)) return ModEnvironment.SERVER;
        return ModEnvironment.UNIVERSAL;
    }

    private static ClientCompatibilityReport.FileIdentity identityOrNull(Path jar) {
        try {
            return ClientCompatibilityReport.FileIdentity.capture(jar);
        } catch (IOException | SecurityException exception) {
            return null;
        }
    }
}
