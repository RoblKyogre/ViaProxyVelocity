/*
 * This file is part of ViaProxyOpenAuthMod - https://github.com/ViaVersionAddons/ViaProxyOpenAuthMod
 * Copyright (C) 2024-2025 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.craftingcomrades.roblkyogre.velocityplugin;

import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import net.lenni0451.lambdaevents.EventHandler;
import net.lenni0451.reflect.Enums;
import net.lenni0451.reflect.stream.RStream;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.ClientLoggedInEvent;
import net.raphimc.viaproxy.plugins.events.ConnectEvent;
import net.raphimc.viaproxy.plugins.events.ViaProxyLoadedEvent;
import net.raphimc.viaproxy.protocoltranslator.viaproxy.ViaProxyConfig;
import net.raphimc.viaproxy.ui.I18n;
import net.raphimc.viaproxy.util.logging.Logger;

public class VelocityPlugin extends ViaProxyPlugin {

    private static ViaProxyConfig.AuthMethod VELOCITY;

    @Override
    public void onEnable() {
        VelocityConfig.load();
        ViaProxy.EVENT_MANAGER.register(this);

        VELOCITY = Enums.newInstance(
            ViaProxyConfig.AuthMethod.class,
            "VELOCITY",
            ViaProxyConfig.AuthMethod.values().length,
            new Class[] { String.class },
            new Object[] { "velocity.auth_method.name" }
        );
        Enums.addEnumInstance(ViaProxyConfig.AuthMethod.class, VELOCITY);
    }

    @EventHandler
    private void onViaProxyLoaded(ViaProxyLoadedEvent event) {
        if (VelocityConfig.forwardingSecret.isEmpty()) {
            Logger.LOGGER.error(
                "The forwarding secret is not set! Please set it in the velocity.yml file."
            );
            System.exit(-1);
        }
        final Map<String, Properties> locales = RStream.of(I18n.class)
            .fields()
            .by("LOCALES")
            .get();
        locales
            .get("en_US")
            .setProperty(VELOCITY.getGuiTranslationKey(), "Use Velocity");
    }

    @EventHandler
    private void onConnect(ConnectEvent event) {
        event
            .getProxyConnection()
            .getPacketHandlers()
            .add(0, new VelocityPacketHandler(event.getProxyConnection()));
    }

    @EventHandler
    private void onClientLoggedIn(ClientLoggedInEvent event)
        throws ExecutionException, InterruptedException {
        if (ViaProxy.getConfig().getAuthMethod() == VELOCITY) {
            event
                .getProxyConnection()
                .getPacketHandler(VelocityPacketHandler.class)
                .sendCustomPayload(
                    VelocityConstants.INFO_CHANNEL,
                    PacketTypes.writeVarInt(
                        Unpooled.buffer(),
                        VelocityConstants.MODERN_LAZY_SESSION
                    )
                );
        }
    }
}
