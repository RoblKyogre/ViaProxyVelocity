/*
 * This file is part of ViaProxyOpenAuthMod - https://github.com/ViaVersionAddons/ViaProxyOpenAuthMod
 * Copyright (C) 2024-2024 RK_01/RaphiMC and contributors
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
package net.raphimc.openauthmodplugin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import net.lenni0451.mcstructs.text.components.StringComponent;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.netminecraft.packet.impl.common.C2SCustomPayloadPacket;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginCustomQueryAnswerPacket;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginKeyPacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginCustomQueryPacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginDisconnectPacket;
import net.raphimc.netminecraft.packet.impl.play.S2CPlayCustomPayloadPacket;
import net.raphimc.viaproxy.proxy.external_interface.OpenAuthModConstants;
import net.raphimc.viaproxy.proxy.packethandler.PacketHandler;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class OpenAuthModPacketHandler extends PacketHandler {

    private final AtomicInteger id = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<ByteBuf>> customPayloadListener = new ConcurrentHashMap<>();

    public OpenAuthModPacketHandler(ProxyConnection proxyConnection) {
        super(proxyConnection);
    }

    @Override
    public boolean handleC2P(Packet packet, List<ChannelFutureListener> listeners) {
        if (packet instanceof C2SCustomPayloadPacket customPayloadPacket) {
            final ByteBuf data = Unpooled.wrappedBuffer(customPayloadPacket.data);
            if (customPayloadPacket.channel.equals(OpenAuthModConstants.DATA_CHANNEL) && this.handleCustomPayload(PacketTypes.readVarInt(data), data)) {
                return false;
            }
        } else if (packet instanceof C2SLoginCustomQueryAnswerPacket loginCustomQueryAnswer) {
            if (loginCustomQueryAnswer.response != null && this.handleCustomPayload(loginCustomQueryAnswer.queryId, Unpooled.wrappedBuffer(loginCustomQueryAnswer.response))) {
                return false;
            }
        } else if (packet instanceof C2SLoginKeyPacket loginKeyPacket) {
            if (this.proxyConnection.getClientVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) && new String(loginKeyPacket.encryptedNonce, StandardCharsets.UTF_8).equals(OpenAuthModConstants.DATA_CHANNEL)) { // 1.8-1.12.2 OpenAuthMod response handling
                final ByteBuf byteBuf = Unpooled.wrappedBuffer(loginKeyPacket.encryptedSecretKey);
                this.handleCustomPayload(PacketTypes.readVarInt(byteBuf), byteBuf);
                return false;
            }
        }

        return true;
    }

    public CompletableFuture<ByteBuf> sendCustomPayload(final String channel, final ByteBuf data) {
        if (channel.length() > 20) throw new IllegalStateException("Channel name can't be longer than 20 characters");
        final CompletableFuture<ByteBuf> future = new CompletableFuture<>();
        final int id = this.id.getAndIncrement();

        switch (this.proxyConnection.getC2pConnectionState()) {
            case LOGIN:
                if (this.proxyConnection.getClientVersion().newerThanOrEqualTo(ProtocolVersion.v1_13)) {
                    this.proxyConnection.getC2P().writeAndFlush(new S2CLoginCustomQueryPacket(id, channel, PacketTypes.readReadableBytes(data))).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                } else {
                    final ByteBuf disconnectPacketData = Unpooled.buffer();
                    PacketTypes.writeString(disconnectPacketData, channel);
                    PacketTypes.writeVarInt(disconnectPacketData, id);
                    disconnectPacketData.writeBytes(data);
                    this.proxyConnection.getC2P().writeAndFlush(new S2CLoginDisconnectPacket(new StringComponent("§cYou need to install OpenAuthMod in order to join this server.§k\n" + Base64.getEncoder().encodeToString(ByteBufUtil.getBytes(disconnectPacketData)) + "\n" + OpenAuthModConstants.LEGACY_MAGIC_STRING))).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                }
                break;
            case PLAY:
                final ByteBuf responseData = Unpooled.buffer();
                PacketTypes.writeVarInt(responseData, id);
                responseData.writeBytes(data);
                this.proxyConnection.getC2P().writeAndFlush(new S2CPlayCustomPayloadPacket(channel, ByteBufUtil.getBytes(responseData))).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                break;
            default:
                throw new IllegalStateException("Can't send a custom payload packet during " + this.proxyConnection.getC2pConnectionState());
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
