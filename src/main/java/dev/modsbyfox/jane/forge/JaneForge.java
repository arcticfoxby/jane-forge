package dev.modsbyfox.jane.forge;

import dev.modsbyfox.jane.forge.client.JaneClientBridge;
import dev.modsbyfox.jane.forge.net.JaneNetwork;
import dev.modsbyfox.jane.forge.server.JaneServerState;
import java.io.IOException;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(JaneForge.MOD_ID)
public final class JaneForge {
    public static final String MOD_ID = "jane";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public JaneForge() {
        JaneNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> JaneClientBridge::bootstrap);
    }

    private void onServerStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) return;
        try {
            JaneServerState.start(FMLPaths.GAMEDIR.get());
        } catch (IOException exception) {
            LOGGER.error("Jane server initialization failed", exception);
            throw new IllegalStateException("Jane server initialization failed closed", exception);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        JaneServerState.stop();
    }
}
