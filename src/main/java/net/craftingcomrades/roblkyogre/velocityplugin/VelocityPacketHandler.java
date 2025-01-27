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

import static java.util.Arrays.binarySearch;

import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginCustomQueryAnswerPacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginCustomQueryPacket;
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

    @Override
    public boolean handleC2P(
        Packet packet,
        List<ChannelFutureListener> listeners
    ) {
        if (
            packet instanceof
            C2SLoginCustomQueryAnswerPacket loginCustomQueryAnswer
        ) {
            final ByteBuf responseBuf = Unpooled.wrappedBuffer(
                loginCustomQueryAnswer.response
            );
            if (
                loginCustomQueryAnswer.response != null &&
                this.handleCustomPayload(
                        loginCustomQueryAnswer.queryId,
                        responseBuf
                    )
            ) {
                if (!verifySignature(responseBuf)) {
                    this.proxyConnection.kickClient(
                            "§cCould not verify player details!"
                        );
                    return false;
                }

                InetAddress clientAddress = InetAddresses.forString(
                    PacketTypes.readString(responseBuf, Short.MAX_VALUE)
                );

                final GameProfile profile = new GameProfile(
                    PacketTypes.readUuid(responseBuf),
                    PacketTypes.readString(responseBuf, 16)
                );
                final int properties = PacketTypes.readVarInt(responseBuf);
                for (int i1 = 0; i1 < properties; i1++) {
                    final String name = PacketTypes.readString(
                        responseBuf,
                        Short.MAX_VALUE
                    );
                    final String value = PacketTypes.readString(
                        responseBuf,
                        Short.MAX_VALUE
                    );
                    final String signature = responseBuf.readBoolean()
                        ? PacketTypes.readString(responseBuf, Short.MAX_VALUE)
                        : null;
                    profile
                        .getProperties()
                        .put(name, new Property(name, value, signature));
                }
                this.proxyConnection.setGameProfile(profile);

                return false;
            }
        }

        return true;
    }

    private boolean verifySignature(final ByteBuf responseBuf) {
        try {
            final byte[] responseSig = new byte[32];
            responseBuf.readBytes(responseSig);

            final byte[] responseData = new byte[responseBuf.readableBytes()];
            responseBuf.getBytes(responseBuf.readerIndex(), responseData);

            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                new SecretKeySpec(
                    VelocityConfig.forwardingSecret.getBytes(),
                    "HmacSHA256"
                )
            );
            final byte[] dataSig = mac.doFinal(responseData);
            if (!MessageDigest.isEqual(responseSig, dataSig)) {
                return false;
            }

            int version = PacketTypes.readVarInt(responseBuf);
            if (
                binarySearch(
                    VelocityConstants.SUPPORTED_FORWARDING_VERSIONS,
                    version
                ) <
                0
            ) {
                throw new IllegalStateException(
                    "Unsupported forwarding version " +
                    version +
                    ", supported " +
                    Arrays.toString(
                        VelocityConstants.SUPPORTED_FORWARDING_VERSIONS
                    )
                );
            }
            return true;
        } catch (
            final InvalidKeyException
            | NoSuchAlgorithmException
            | IllegalStateException e
        ) {
            Logger.LOGGER.error("Forwarding secret check failed!", e);
            return false;
        }
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
            return true;
        }
        return false;
    }
}
