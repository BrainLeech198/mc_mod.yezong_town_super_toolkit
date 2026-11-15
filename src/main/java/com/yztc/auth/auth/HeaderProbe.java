package com.yztc.auth.auth;

import net.minecraft.server.level.ServerPlayer;

/**
 * @deprecated HTTP headers are not part of the Minecraft connection. Use
 * {@link com.yztc.auth.api.ClientIpResolveEvent} for a trusted server-side integration.
 */
@Deprecated(forRemoval = false)
public final class HeaderProbe {
    private HeaderProbe() {}

    /** Legacy no-op retained for source compatibility; no HTTP header is available here. */
    public static String firstValue(ServerPlayer p, String headerName) {
        return null;
    }
}
