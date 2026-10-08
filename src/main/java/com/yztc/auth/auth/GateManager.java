package com.yztc.auth.auth;

import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.Gate;
import com.yztc.auth.security.PasswordHasher;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Base64;

public final class GateManager {
    private static final Base64.Encoder ENC = Base64.getEncoder();
    private static final Base64.Decoder DEC = Base64.getDecoder();

    private final AccountStore store;

    public GateManager(AccountStore store) {
        this.store = store;
    }

    public enum GateResult { OK, BAD, NOT_SET }

    public boolean isGateSet() {
        return store.getGate() != null;
    }

    public GateResult verify(ServerPlayer player, String password) {
        Gate g = store.getGate();
        if (g == null) return GateResult.NOT_SET;
        boolean ok;
        if (PasswordHasher.ALGO_PBKDF2.equals(g.algo)) {
            ok = PasswordHasher.verifyPbkdf2(
                    password.toCharArray(), g.iterations, DEC.decode(g.salt), DEC.decode(g.hash));
        } else {
            ok = PasswordHasher.verifyLegacy(PasswordHasher.legacySha256Hex(password.toCharArray()), g.hash);
        }
        if (ok && PendingState.ALL.containsKey(player.getUUID())) {
            PendingState.ALL.get(player.getUUID()).gatePassed = true;
        }
        return ok ? GateResult.OK : GateResult.BAD;
    }

    public void set(String newPassword) throws IOException {
        PasswordHasher.Hashed h = PasswordHasher.pbkdf2(newPassword.toCharArray(), AuthConfig.pbkdf2Iterations(), 16);
        Gate g = new Gate();
        g.algo = PasswordHasher.ALGO_PBKDF2;
        g.iterations = h.iterations();
        g.salt = ENC.encodeToString(h.salt());
        g.hash = ENC.encodeToString(h.hash());
        g.updatedAt = System.currentTimeMillis();
        store.setGate(g);
        store.save();
    }

    public void clear() throws IOException {
        store.clearGate();
        store.save();
    }
}
