package dev.modsbyfox.jane.forge.net;

import dev.modsbyfox.jane.core.LoginStatus;
import dev.modsbyfox.jane.core.ManifestCodec;
import dev.modsbyfox.jane.core.RequiredManifest;
import dev.modsbyfox.jane.forge.JaneForge;
import dev.modsbyfox.jane.forge.client.JaneClientBridge;
import dev.modsbyfox.jane.forge.server.JaneServerState;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Jane's required-file gate runs within Forge's login negotiation. */
public final class JaneNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger(JaneForge.MOD_ID);
    private static final String NETWORK_VERSION = Integer.toString(RequiredManifest.PROTOCOL);
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(JaneForge.MOD_ID, "manifest"),
            () -> NETWORK_VERSION, NETWORK_VERSION::equals, NETWORK_VERSION::equals);
    private static boolean registered;

    private JaneNetwork() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(LoginOfferMessage.class, 0, NetworkDirection.LOGIN_TO_CLIENT)
                .loginIndex(LoginOfferMessage::getAsInt, LoginOfferMessage::setLoginIndex)
                .encoder(LoginOfferMessage::encode)
                .decoder(LoginOfferMessage::decode)
                .buildLoginPacketList(JaneNetwork::loginOffers)
                .consumerNetworkThread(JaneNetwork::handleOffer)
                .add();
        CHANNEL.messageBuilder(LoginResultMessage.class, 1, NetworkDirection.LOGIN_TO_SERVER)
                .loginIndex(LoginResultMessage::getAsInt, LoginResultMessage::setLoginIndex)
                .encoder(LoginResultMessage::encode)
                .decoder(LoginResultMessage::decode)
                .consumerNetworkThread(HandshakeHandler.indexFirst(JaneNetwork::handleResult))
                .add();
    }

    private static List<Pair<String, LoginOfferMessage>> loginOffers(boolean local) {
        if (local || !JaneServerState.active()) return List.of();
        RequiredManifest manifest = JaneServerState.manifest();
        try {
            byte[] bytes = ManifestCodec.encode(new ManifestCodec.LoginOffer(
                    manifest, JaneServerState.providerOffer()));
            return List.of(Pair.of("jane:manifest", new LoginOfferMessage(bytes)));
        } catch (IOException exception) {
            throw new IllegalStateException("Jane login offer could not be created", exception);
        }
    }

    private static void handleOffer(LoginOfferMessage offer, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // The reply may follow an asynchronous physical-file scan. Mark the received packet now;
        // Forge keeps the login pending until the indexed response reaches the server.
        context.setPacketHandled(true);
        CompletableFuture<LoginStatus> result;
        try {
            result = DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                    () -> () -> JaneClientBridge.handleOffer(offer.payload, context.getNetworkManager()));
        } catch (RuntimeException exception) {
            LOGGER.error("Jane login assessment could not start", exception);
            result = CompletableFuture.completedFuture(LoginStatus.PROTOCOL_ERROR);
        }
        if (result == null) result = CompletableFuture.completedFuture(LoginStatus.PROTOCOL_ERROR);
        result.whenComplete((status, error) -> {
            if (error != null) LOGGER.error("Jane login assessment failed", error);
            try {
                CHANNEL.reply(new LoginResultMessage(error == null && status != null
                        ? status : LoginStatus.PROTOCOL_ERROR), context);
            } catch (RuntimeException exception) {
                LOGGER.error("Jane login result could not be sent", exception);
                context.getNetworkManager().disconnect(Component.translatable("jane.protocol.incompatible"));
            }
        });
    }

    private static void handleResult(HandshakeHandler handshake, LoginResultMessage result,
                                     Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.setPacketHandled(true);
        switch (result.status) {
            case EXACT_PASS -> context.attr(JaneLoginGate.ACCEPTED).set(Boolean.TRUE);
            case USER_OVERRIDE -> {
                LOGGER.warn("Jane client requested one-time join with an unmet required baseline");
                context.attr(JaneLoginGate.ACCEPTED).set(Boolean.TRUE);
            }
            case ACTION_REQUIRED -> context.getNetworkManager()
                    .disconnect(Component.translatable("jane.client.action_required"));
            case PROTOCOL_ERROR -> context.getNetworkManager()
                    .disconnect(Component.translatable("jane.protocol.incompatible"));
        }
    }

    private static final class LoginOfferMessage implements IntSupplier {
        private final byte[] payload;
        private int loginIndex;

        private LoginOfferMessage(byte[] payload) { this.payload = payload; }

        private static void encode(LoginOfferMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.payload.length);
            buffer.writeBytes(message.payload);
        }

        private static LoginOfferMessage decode(FriendlyByteBuf buffer) {
            int length = buffer.readVarInt();
            if (length < 8 || length > RequiredManifest.MAX_PAYLOAD || buffer.readableBytes() != length)
                throw new IllegalArgumentException("Invalid Jane login offer size");
            byte[] bytes = new byte[length];
            buffer.readBytes(bytes);
            return new LoginOfferMessage(bytes);
        }

        private void setLoginIndex(int index) { loginIndex = index; }
        @Override public int getAsInt() { return loginIndex; }
    }

    private static final class LoginResultMessage implements IntSupplier {
        private final LoginStatus status;
        private int loginIndex;

        private LoginResultMessage(LoginStatus status) { this.status = status; }

        private static void encode(LoginResultMessage message, FriendlyByteBuf buffer) {
            buffer.writeByte(message.status.code());
        }

        private static LoginResultMessage decode(FriendlyByteBuf buffer) {
            if (buffer.readableBytes() != 1) return new LoginResultMessage(LoginStatus.PROTOCOL_ERROR);
            try {
                return new LoginResultMessage(LoginStatus.fromCode(buffer.readUnsignedByte()));
            } catch (IllegalArgumentException exception) {
                return new LoginResultMessage(LoginStatus.PROTOCOL_ERROR);
            }
        }

        private void setLoginIndex(int index) { loginIndex = index; }
        @Override public int getAsInt() { return loginIndex; }
    }
}
