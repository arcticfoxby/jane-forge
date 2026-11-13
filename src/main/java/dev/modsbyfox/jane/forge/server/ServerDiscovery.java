package dev.modsbyfox.jane.forge.server;

import dev.modsbyfox.jane.core.PathSafety;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;

/** Finds loaded Forge mod files that are physical JARs directly inside this instance's mods directory. */
final class ServerDiscovery {
    record Candidate(String modId, String displayName, String version, List<String> bundledIds,
                     boolean serverOnly) { }
    record Discovered(Candidate candidate, Path jar) { }
    record Skipped(Path jar, String reason) { }
    record Discovery(List<Discovered> mods, List<Skipped> skipped) { }

    private ServerDiscovery() { }

    static Discovery discover(Path gameDir) throws IOException {
        Path root = gameDir.toRealPath();
        Path mods = root.resolve("mods");
        if (!Files.exists(mods, LinkOption.NOFOLLOW_LINKS)) return new Discovery(List.of(), List.of());
        if (Files.isSymbolicLink(mods) || !Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)
                || !mods.toRealPath().getParent().equals(root)) {
            throw new IOException("mods directory escaped current instance");
        }

        ModList modList = ModList.get();
        if (modList == null) throw new IOException("Forge mod list is not ready");
        Map<Path, Candidate> selected = new HashMap<>();
        for (IModFileInfo info : modList.getModFiles()) {
            if (!(info instanceof ModFileInfo fileInfo))
                throw new IOException("Unsupported Forge mod file metadata");
            Path origin = fileInfo.getFile().getFilePath().toAbsolutePath().normalize();
            if (!mods.equals(origin.getParent()) || info.getMods().isEmpty()) continue;
            Path safe = PathSafety.existingJarInMods(root, origin);
            List<IModInfo> bundled = info.getMods();
            IModInfo primary = bundled.stream().min(Comparator.comparing(IModInfo::getModId)).orElseThrow();
            boolean serverOnly = bundled.stream().allMatch(mod -> "IGNORE_SERVER_VERSION".equals(
                    mod.getConfig().<String>getConfigElement("displayTest").orElse("MATCH_VERSION")));
            Candidate candidate = new Candidate(primary.getModId(), primary.getDisplayName(),
                    primary.getVersion().toString(), bundled.stream().map(IModInfo::getModId).sorted().toList(),
                    serverOnly);
            if (selected.putIfAbsent(safe, candidate) != null)
                throw new IOException("Duplicate Forge metadata for physical JAR: " + safe.getFileName());
        }

        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(mods)) {
            for (Path entry : entries) {
                if (entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) jars.add(entry);
            }
        }
        jars.sort(Comparator.comparing(path -> path.getFileName().toString()));
        List<Discovered> discovered = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (Path jar : jars) {
            Path safe = PathSafety.existingJarInMods(root, jar);
            Candidate candidate = selected.get(safe);
            if (candidate == null) skipped.add(new Skipped(safe, "not_loaded_forge_mod"));
            else discovered.add(new Discovered(candidate, safe));
        }
        discovered.sort(Comparator.comparing((Discovered item) -> item.candidate().modId())
                .thenComparing(item -> item.jar().getFileName().toString()));
        return new Discovery(List.copyOf(discovered), List.copyOf(skipped));
    }
}
