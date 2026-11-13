package dev.modsbyfox.jane.forge.net;

import io.netty.util.AttributeKey;

/** Connection-local result used to resume Forge's login packets after Jane's check. */
public final class JaneLoginGate {
    public static final AttributeKey<Boolean> ACCEPTED = AttributeKey.valueOf("jane:login-accepted");

    private JaneLoginGate() { }
}
