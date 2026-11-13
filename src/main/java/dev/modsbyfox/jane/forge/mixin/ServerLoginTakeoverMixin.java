package dev.modsbyfox.jane.forge.mixin;

import dev.modsbyfox.jane.core.MinecraftTransferMarker;
import dev.modsbyfox.jane.forge.server.JaneServerState;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Route only Jane-marked login connections to the raw JAR transport. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginTakeoverMixin {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Connection connection;

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void jane$handleMarkedTransfer(ServerboundHelloPacket packet, CallbackInfo callback) {
        if (!MinecraftTransferMarker.matches(packet.name(), packet.profileId())) return;
        callback.cancel();
        JaneServerState.acceptMarkedTransfer(server, connection,
                ((ConnectionChannelAccessor) connection).jane$getChannel());
    }
}
