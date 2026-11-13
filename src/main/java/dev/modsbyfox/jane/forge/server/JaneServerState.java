package dev.modsbyfox.jane.forge.server;

import dev.modsbyfox.jane.core.RequiredManifest;
import dev.modsbyfox.jane.core.ServerProviderCore;
import dev.modsbyfox.jane.core.ServerProviderOffer;
import dev.modsbyfox.jane.core.ServerProviderService;
import io.netty.channel.Channel;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dedicated-server manifest and download provider, created only after Forge has loaded mods. */
public final class JaneServerState {
    private static final Logger LOGGER = LoggerFactory.getLogger("jane");
    private static volatile State current;

    private record State(RequiredManifest manifest, ServerProviderService separateProvider,
                         MinecraftProviderTransport minecraftProvider, int advertisedPort) {
        void close() {
            if (separateProvider != null) separateProvider.close();
            if (minecraftProvider != null) minecraftProvider.close();
        }
    }

    private JaneServerState() { }

    public static synchronized void start(Path gameDir) throws IOException {
        stop();
        Path root = gameDir.toRealPath();
        ServerConfig.Config config = ServerConfig.read(root, root.resolve("config"));
        ServerManifest.Prepared prepared = ServerManifest.prepare(root, config);
        RequiredManifest manifest = prepared.manifest();
        ServerProviderService separate = null;
        MinecraftProviderTransport minecraft = null;
        int advertisedPort = 0;
        try {
            if (config.provider().mode() == ServerConfig.ProviderMode.SEPARATE_PORT) {
                Map<String, ServerProviderService.File> files = new HashMap<>();
                for (var entry : manifest.entries()) {
                    files.put(entry.sha512(), new ServerProviderService.File(entry,
                            prepared.files().get(entry.sha512())));
                }
                separate = new ServerProviderService(root, files, config.provider().bindPort().orElseThrow(),
                        Clock.systemUTC());
                advertisedPort = config.provider().advertisedPort().orElseThrow();
                LOGGER.info("[server] Jane provider on separate TCP port {} (advertised {})",
                        separate.port(), advertisedPort);
            } else if (config.provider().mode() == ServerConfig.ProviderMode.AUTO
                    || config.provider().mode() == ServerConfig.ProviderMode.MINECRAFT) {
                Map<String, ServerProviderCore.File> files = new HashMap<>();
                for (var entry : manifest.entries()) {
                    files.put(entry.sha512(), new ServerProviderCore.File(entry,
                            prepared.files().get(entry.sha512())));
                }
                minecraft = new MinecraftProviderTransport(new ServerProviderCore(root, files, Clock.systemUTC()));
                LOGGER.info("[server] Jane provider uses the Minecraft TCP port");
            } else {
                LOGGER.info("[server] Jane provider disabled");
            }
            current = new State(manifest, separate, minecraft, advertisedPort);
            LOGGER.info("[server] Jane required manifest ready: {} entries", manifest.entries().size());
        } catch (IOException | RuntimeException exception) {
            if (separate != null) separate.close();
            if (minecraft != null) minecraft.close();
            throw exception;
        }
    }

    public static synchronized void stop() {
        State old = current;
        current = null;
        if (old != null) old.close();
    }

    public static boolean active() {
        return current != null;
    }

    public static RequiredManifest manifest() {
        State state = current;
        if (state == null) throw new IllegalStateException("Jane server manifest is not ready");
        return state.manifest();
    }

    /** Issues a new per-login capability for the current manifest, or null when disabled. */
    public static ServerProviderOffer providerOffer() {
        State state = current;
        if (state == null) throw new IllegalStateException("Jane server manifest is not ready");
        if (state.minecraftProvider() != null)
            return ServerProviderOffer.minecraft(state.minecraftProvider().issue(state.manifest()));
        if (state.separateProvider() != null)
            return ServerProviderOffer.separatePort(state.advertisedPort(),
                    state.separateProvider().issue(state.manifest()));
        return null;
    }

    /** Called only for Jane-marked login connections by the login packet hook. */
    public static void acceptMarkedTransfer(MinecraftServer server, Connection connection, Channel channel) {
        State state = current;
        if (!server.isDedicatedServer() || state == null || state.minecraftProvider() == null || channel == null) {
            if (channel != null) channel.close();
            return;
        }
        state.minecraftProvider().takeover(server, connection, channel);
    }
}
