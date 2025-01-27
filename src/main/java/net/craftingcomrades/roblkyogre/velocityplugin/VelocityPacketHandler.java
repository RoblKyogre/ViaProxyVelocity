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

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import net.lenni0451.mcstructs.text.components.StringComponent;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.netminecraft.packet.impl.common.C2SCustomPayloadPacket;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginCustomQueryAnswerPacket;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginHelloPacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginCustomQueryPacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginDisconnectPacket;
import net.raphimc.netminecraft.packet.impl.play.S2CPlayCustomPayloadPacket;
import net.raphimc.viaproxy.proxy.packethandler.PacketHandler;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.util.logging.Logger;

public class VelocityPacketHandler extends PacketHandler {

    private final AtomicInteger id = new AtomicInteger(0);
    private final Map<
        Integer,
        CompletableFuture<ByteBuf>
    > customPayloadListener = new ConcurrentHashMap<>();

    public VelocityPacketHandler(ProxyConnection proxyConnection) {
        super(proxyConnection);
    }

    private void sendVelocityPacket() {
        try {
            Logger.LOGGER.error("Login detected.");
            final ByteBuf response = sendCustomPayload(
                VelocityConstants.INFO_CHANNEL,
                PacketTypes.writeVarInt(
                    Unpooled.buffer(),
                    VelocityConstants.MODERN_LAZY_SESSION
                )
            ).get(1, TimeUnit.SECONDS);
            if (response == null) throw new TimeoutException();
            if (
                response.isReadable() && !response.readBoolean()
            ) throw new TimeoutException();
        } catch (Exception e) {
            //event.getProxyConnection().kickClient(VelocityConfig.kickMessage);
            Logger.LOGGER.error("Exception hit!");
        }
    }

    @Override
    public boolean handleC2P(
        Packet packet,
        List<ChannelFutureListener> listeners
    ) {
        if (packet instanceof C2SLoginHelloPacket loginHello) {
            sendCustomPayload(
                VelocityConstants.INFO_CHANNEL,
                PacketTypes.writeVarInt(
                    Unpooled.buffer(),
                    VelocityConstants.MODERN_LAZY_SESSION
                )
            );
        } else if (
            packet instanceof
            C2SLoginCustomQueryAnswerPacket loginCustomQueryAnswer
        ) {
            Logger.LOGGER.info("Custom packet detected!");
            if (
                loginCustomQueryAnswer.response != null &&
                this.handleCustomPayload(
                        loginCustomQueryAnswer.queryId,
                        Unpooled.wrappedBuffer(loginCustomQueryAnswer.response)
                    )
            ) {
                return false;
            }
        }

        return true;
    }

    public CompletableFuture<ByteBuf> sendCustomPayload(
        final String channel,
        final ByteBuf data
    ) {
        if (channel.length() > 20) throw new IllegalStateException(
            "Channel name can't be longer than 20 characters"
        );
        final CompletableFuture<ByteBuf> future = new CompletableFuture<>();
        final int id = this.id.getAndIncrement();

        switch (this.proxyConnection.getC2pConnectionState()) {
            case LOGIN:
                Logger.LOGGER.info("Sending custom payload...");
                this.proxyConnection.getC2P()
                    .writeAndFlush(
                        new S2CLoginCustomQueryPacket(
                            id,
                            channel,
                            PacketTypes.readReadableBytes(data)
                        )
                    )
                    .addListener(
                        ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE
                    );
                break;
            default:
                throw new IllegalStateException(
                    "Can't send a custom payload packet during " +
                    this.proxyConnection.getC2pConnectionState()
                );
        }

        this.customPayloadListener.put(id, future);
        return future;
    }

    private boolean handleCustomPayload(final int id, final ByteBuf data) {
        if (this.customPayloadListener.containsKey(id)) {
            this.customPayloadListener.remove(id).complete(data);
            Logger.LOGGER.info(
                "Received a custom payload packet with id: " + id
            );
            return true;
        }
        Logger.LOGGER.info(
            "Received a custom payload packet with an unknown id: " + id
        );
        return false;
    }
}
