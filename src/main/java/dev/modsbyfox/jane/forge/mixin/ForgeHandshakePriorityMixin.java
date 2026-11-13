package dev.modsbyfox.jane.forge.mixin;

import dev.modsbyfox.jane.forge.net.JaneLoginGate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Let Jane compare required files before Forge rejects missing mod channels or registries. */
@Mixin(value = HandshakeHandler.class, remap = false)
public abstract class ForgeHandshakePriorityMixin {
    @Shadow private List<NetworkRegistry.LoginPayload> messageList;
    @Shadow private int packetPosition;
    @Shadow @Final private Connection manager;

    @Unique private boolean jane$waitingForResult;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void jane$prioritize(Connection connection, NetworkDirection direction, CallbackInfo callback) {
        ResourceLocation janeChannel = new ResourceLocation("jane", "manifest");
        int found = -1;
        for (int index = 0; index < messageList.size(); index++) {
            if (janeChannel.equals(messageList.get(index).getChannelName())) {
                found = index;
                break;
            }
        }
        if (found < 0) return;
        jane$waitingForResult = true;
        if (found == 0) return;
        List<NetworkRegistry.LoginPayload> ordered = new ArrayList<>(messageList);
        NetworkRegistry.LoginPayload janeOffer = ordered.remove(found);
        ordered.add(0, janeOffer);
        messageList = ordered;
    }

    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void jane$awaitResult(CallbackInfoReturnable<Boolean> callback) {
        if (!jane$waitingForResult || packetPosition == 0) return;
        Boolean accepted = ((ConnectionChannelAccessor) manager).jane$getChannel()
                .attr(JaneLoginGate.ACCEPTED).get();
        if (!Boolean.TRUE.equals(accepted)) callback.setReturnValue(false);
    }
}
