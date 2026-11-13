package dev.modsbyfox.jane.forge.client;

import dev.modsbyfox.jane.core.LoginStatus;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.Connection;

/** Client-only entry points for Forge's login channel and lifecycle. */
public final class JaneClientBridge {
    private JaneClientBridge() { }

    public static void bootstrap() { JaneClient.bootstrap(); }

    public static CompletableFuture<LoginStatus> handleOffer(byte[] payload, Connection connection) {
        return JaneClient.handleOffer(payload, connection);
    }
}
