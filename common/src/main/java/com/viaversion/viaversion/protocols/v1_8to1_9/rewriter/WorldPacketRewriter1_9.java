/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2016-2026 ViaVersion and contributors
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
package com.viaversion.viaversion.protocols.v1_8to1_9.rewriter;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.ChunkPosition;
import com.viaversion.viaversion.api.minecraft.chunks.BaseChunk;
import com.viaversion.viaversion.api.minecraft.chunks.Chunk;
import com.viaversion.viaversion.api.minecraft.chunks.ChunkSection;
import com.viaversion.viaversion.api.minecraft.item.DataItem;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandlers;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.chunk.BulkChunkType1_8;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_8;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_9_1;
import com.viaversion.viaversion.protocols.v1_8to1_9.Protocol1_8To1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.data.EffectIdMappings1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.data.PotionIdMappings1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.data.SoundEffectMappings1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_8;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_8;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.CommandBlockProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.HandItemProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.OffHandItemProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.SwapHandProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.storage.ClientWorld1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.storage.EntityTracker1_9;
import com.viaversion.viaversion.util.ComponentUtil;
import com.viaversion.viaversion.util.Key;
import java.util.ArrayList;
import java.util.Optional;

public class WorldPacketRewriter1_9 {
    public static void register(Protocol1_8To1_9 protocol) {
        protocol.registerClientbound(ClientboundPackets1_8.UPDATE_SIGN, new PacketHandlers() {
            @Override
            public void register() {
                map(Types.BLOCK_POSITION1_8); // 0 - Sign Position
                handler(wrapper -> {
                    for (int i = 0; i < 4; i++) {
                        final String line = wrapper.read(Types.STRING);
                        Protocol1_8To1_9.STRING_TO_JSON.write(wrapper, line);
                    }
                });
            }
        });

        protocol.registerClientbound(ClientboundPackets1_8.LEVEL_EVENT, new PacketHandlers() {
            @Override
            public void register() {
                map(Types.INT); // 0 - Effect ID
                map(Types.BLOCK_POSITION1_8); // 1 - Position
                map(Types.INT); // 2 - Data
                map(Types.BOOLEAN); // 3 - Disable relative volume

                handler(wrapper -> {
                    int id = wrapper.get(Types.INT, 0);
                    id = EffectIdMappings1_9.getNewId(id);
                    wrapper.set(Types.INT, 0, id);
                });
                handler(wrapper -> {
                    int id = wrapper.get(Types.INT, 0);
                    if (id == 2002) {
                        int data = wrapper.get(Types.INT, 1);
                        int newData = PotionIdMappings1_9.getNewPotionID(data);
                        wrapper.set(Types.INT, 1, newData);
                    }
                });
            }
        });

        protocol.registerClientbound(ClientboundPackets1_8.CUSTOM_SOUND, new PacketHandlers() {
            @Override
            public void register() {
                map(Types.STRING); // 0 - Sound Name

                handler(wrapper -> {
                    String name = Key.stripMinecraftNamespace(wrapper.get(Types.STRING, 0));

                    SoundEffectMappings1_9 effect = SoundEffectMappings1_9.getByName(name);
                    int catid = 0;
                    String newname = name;
                    if (effect != null) {
                        catid = effect.getCategory().getId();
                        newname = effect.getNewName();
                    }
                    wrapper.set(Types.STRING, 0, newname);
                    wrapper.write(Types.VAR_INT, catid);

                    if (!Via.getConfig().cancelBlockSounds()) {
                        return;
                    }
                    if (effect != null && effect.isBreakSound()) {
                        EntityTracker1_9 tracker = wrapper.user().getEntityTracker(Protocol1_8To1_9.class);
                        int x = wrapper.passthrough(Types.INT);
                        int y = wrapper.passthrough(Types.INT);
                        int z = wrapper.passthrough(Types.INT);
                        if (tracker.interactedBlockRecently((int) Math.floor(x / 8.0), (int) Math.floor(y / 8.0), (int) Math.floor(z / 8.0))) {
                            wrapper.cancel();
                        }
                    }
                });
            }
        });

        protocol.registerClientbound(ClientboundPackets1_8.LEVEL_CHUNK, wrapper -> {
            ClientWorld1_9 clientWorld = wrapper.user().getClientWorld(Protocol1_8To1_9.class);
            Chunk chunk = wrapper.read(ChunkType1_8.forEnvironment(clientWorld.getEnvironment()));
            long chunkHash = ChunkPosition.chunkKey(chunk.getX(), chunk.getZ());

            if (chunk.isFullChunk() && chunk.getBitmask() == 0) {
                wrapper.setPacketType(ClientboundPackets1_9.FORGET_LEVEL_CHUNK);
                wrapper.write(Types.INT, chunk.getX());
                wrapper.write(Types.INT, chunk.getZ());

                CommandBlockProvider provider = Via.getManager().getProviders().get(CommandBlockProvider.class);
                provider.unloadChunk(wrapper.user(), chunk.getX(), chunk.getZ());
                clientWorld.getLoadedChunks().remove(chunkHash);

                if (Via.getConfig().isChunkBorderFix()) {
                    for (int modX = -1; modX <= 1; modX++) {
                        for (int modZ = -1; modZ <= 1; modZ++) {
                            if (modX == 0 && modZ == 0) continue;
                            int chunkX = chunk.getX() + modX;
                            int chunkZ = chunk.getZ() + modZ;
                            if (!clientWorld.getLoadedChunks().contains(ChunkPosition.chunkKey(chunkX, chunkZ))) {
                                PacketWrapper unloadChunk = wrapper.create(ClientboundPackets1_9.FORGET_LEVEL_CHUNK);
                                unloadChunk.write(Types.INT, chunkX);
                                unloadChunk.write(Types.INT, chunkZ);
                                unloadChunk.send(Protocol1_8To1_9.class);
                            }
                        }
                    }
                }
            } else {
                Type<Chunk> chunkType = ChunkType1_9_1.forEnvironment(clientWorld.getEnvironment());
                wrapper.write(chunkType, chunk);
                clientWorld.getLoadedChunks().add(chunkHash);

                if (Via.getConfig().isChunkBorderFix()) {
                    for (int modX = -1; modX <= 1; modX++) {
                        for (int modZ = -1; modZ <= 1; modZ++) {
                            if (modX == 0 && modZ == 0) continue;
                            int chunkX = chunk.getX() + modX;
                            int chunkZ = chunk.getZ() + modZ;
                            if (!clientWorld.getLoadedChunks().contains(ChunkPosition.chunkKey(chunkX, chunkZ))) {
                                PacketWrapper emptyChunk = wrapper.create(ClientboundPackets1_9.LEVEL_CHUNK);
                                Chunk c = new BaseChunk(chunkX, chunkZ, true, false, 0, new ChunkSection[16], new int[256], new ArrayList<>());
                                emptyChunk.write(chunkType, c);
                                emptyChunk.send(Protocol1_8To1_9.class);
                            }
                        }
                    }
                }
            }
        });

        protocol.registerClientbound(ClientboundPackets1_8.MAP_BULK_CHUNK, null, wrapper -> {
            wrapper.cancel();
            ClientWorld1_9 clientWorld = wrapper.user().getClientWorld(Protocol1_8To1_9.class);
            Chunk[] chunks = wrapper.read(BulkChunkType1_8.TYPE);
            Type<Chunk> chunkType = ChunkType1_9_1.forEnvironment(clientWorld.getEnvironment());

            for (Chunk chunk : chunks) {
                PacketWrapper chunkData = wrapper.create(ClientboundPackets1_9.LEVEL_CHUNK);
                chunkData.write(chunkType, chunk);
                chunkData.send(Protocol1_8To1_9.class);
                clientWorld.getLoadedChunks().add(ChunkPosition.chunkKey(chunk.getX(), chunk.getZ()));
            }

            if (!Via.getConfig().isChunkBorderFix()) return;

            for (Chunk chunk : chunks) {
                for (int modX = -1; modX <= 1; modX++) {
                    for (int modZ = -1; modZ <= 1; modZ++) {
                        if (modX == 0 && modZ == 0) continue;
                        int chunkX = chunk.getX() + modX;
                        int chunkZ = chunk.getZ() + modZ;
                        if (!clientWorld.getLoadedChunks().contains(ChunkPosition.chunkKey(chunkX, chunkZ))) {
                            PacketWrapper emptyChunk = wrapper.create(ClientboundPackets1_9.LEVEL_CHUNK);
                            Chunk c = new BaseChunk(chunkX, chunkZ, true, false, 0, new ChunkSection[16], new int[256], new ArrayList<>());
                            emptyChunk.write(chunkType, c);
                            emptyChunk.send(Protocol1_8To1_9.class);
                        }
                    }
                }
            }
        });

        protocol.registerClientbound(ClientboundPackets1_8.BLOCK_ENTITY_DATA, new PacketHandlers() {
            @Override
            public void register() {
                map(Types.BLOCK_POSITION1_8); // 0 - Block Position
                map(Types.UNSIGNED_BYTE); // 1 - Action
                map(Types.NAMED_COMPOUND_TAG); // 2 - NBT

                handler(wrapper -> {
                    int action = wrapper.get(Types.UNSIGNED_BYTE, 0);
                    if (action == 1) {
                        CompoundTag tag = wrapper.get(Types.NAMED_COMPOUND_TAG, 0);
                        if (tag != null) {
                            StringTag entityId = tag.getStringTag("EntityId");
                            if (entityId != null) {
                                CompoundTag spawn = new CompoundTag();
                                spawn.putString("id", entityId.getValue());
                                tag.put("SpawnData", spawn);
                            } else {
                                CompoundTag spawn = new CompoundTag();
                                spawn.putString("id", "AreaEffectCloud");
                                tag.put("SpawnData", spawn);
                            }
                        }
                    }
                    if (action == 2) {
                        CommandBlockProvider provider = Via.getManager().getProviders().get(CommandBlockProvider.class);
                        provider.addOrUpdateBlock(wrapper.user(), wrapper.get(Types.BLOCK_POSITION1_8, 0), wrapper.get(Types.NAMED_COMPOUND_TAG, 0));
                        wrapper.cancel();
                    }
                });
            }
        });

        /* Incoming Packets */
        protocol.registerServerbound(ServerboundPackets1_9.SIGN_UPDATE, new PacketHandlers() {
            @Override
            public void register() {
                map(Types.BLOCK_POSITION1_8);
                handler(wrapper -> {
                    for (int i = 0; i < 4; i++) {
                        final String line = wrapper.read(Types.STRING);
                        wrapper.write(Types.COMPONENT, ComponentUtil.plainToJson(line));
                    }
                });
            }
        });

        protocol.registerServerbound(ServerboundPackets1_9.PLAYER_ACTION, new PacketHandlers() {
            @Override
            public void register() {
                map(Types.VAR_INT); // Action
                map(Types.BLOCK_POSITION1_8); // Position
                handler(wrapper -> {
                    int status = wrapper.get(Types.VAR_INT, 0);
                    if (status == 6) {
                        wrapper.cancel();
                        Via.getManager().getProviders()
                            .get(SwapHandProvider.class)
                            .onSwapHand(wrapper.user());
                    }
                });
                handler(wrapper -> {
                    int status = wrapper.get(Types.VAR_INT, 0);
                    if (status == 5 || status == 4 || status == 3) {
                        EntityTracker1_9 entityTracker = wrapper.user().getEntityTracker(Protocol1_8To1_9.class);
                        if (entityTracker.isBlocking()) {
                            entityTracker.setBlocking(false);
                            if (!Via.getConfig().isShowShieldWhenSwordInHand()) {
                                entityTracker.setSecondHand(null);
                            }
                        }
                    }
                });
            }
        });

        protocol.registerServerbound(ServerboundPackets1_9.USE_ITEM, null, wrapper -> {
            int hand = wrapper.read(Types.VAR_INT);
            wrapper.clearInputBuffer();
            wrapper.setPacketType(ServerboundPackets1_8.USE_ITEM_ON);
            wrapper.write(Types.BLOCK_POSITION1_8, new BlockPosition(-1, -1, -1));
            wrapper.write(Types.UNSIGNED_BYTE, (short) 255);

            Item item;
            if (hand == 1) {
                // Off-hand : swap temporaire + item off-hand
                OffHandItemProvider offHandProvider = Via.getManager().getProviders().get(OffHandItemProvider.class);
                item = offHandProvider.getOffHandItem(wrapper.user());
                if (item == null) {
                    wrapper.cancel();
                    return;
                }
                Via.getPlatform().runSync(() -> offHandProvider.prePlacement(wrapper.user()));
                Via.getPlatform().runSync(() -> offHandProvider.postPlacement(wrapper.user()));
            } else {
                item = Via.getManager().getProviders().get(HandItemProvider.class).getHandItem(wrapper.user());

                if (Via.getConfig().isShieldBlocking() &&
                    wrapper.user().getProtocolInfo().protocolVersion().olderThan(ProtocolVersion.v1_21_4)) {
                    EntityTracker1_9 tracker = wrapper.user().getEntityTracker(Protocol1_8To1_9.class);
                    boolean showShieldWhenSwordInHand = Via.getConfig().isShowShieldWhenSwordInHand();
                    boolean isSword = showShieldWhenSwordInHand ? tracker.hasSwordInHand()
                        : item != null && Protocol1_8To1_9.isSword(item.identifier());

                    if (isSword) {
                        if (!tracker.isBlocking()) {
                            tracker.setBlocking(true);
                            if (!showShieldWhenSwordInHand && tracker.getItemInSecondHand() == null) {
                                tracker.setSecondHand(new DataItem(442, (byte) 1, (short) 0, null));
                            }
                        }
                        boolean blockUsingMainHand = Via.getConfig().isNoDelayShieldBlocking()
                            && !showShieldWhenSwordInHand;
                        if (blockUsingMainHand && hand == 1 || !blockUsingMainHand && hand == 0) {
                            wrapper.cancel();
                            return;
                        }
                    } else {
                        if (!showShieldWhenSwordInHand) tracker.setSecondHand(null);
                        tracker.setBlocking(false);
                    }
                }
            }

            wrapper.write(Types.ITEM1_8, item);
            wrapper.write(Types.UNSIGNED_BYTE, (short) 0);
            wrapper.write(Types.UNSIGNED_BYTE, (short) 0);
            wrapper.write(Types.UNSIGNED_BYTE, (short) 0);
        });

        protocol.registerServerbound(ServerboundPackets1_9.USE_ITEM_ON, new PacketHandlers() {
            @Override
            public void register() {
                map(Types.BLOCK_POSITION1_8); // 0 - Position
                map(Types.VAR_INT, Types.UNSIGNED_BYTE); // 1 - Block Face
                handler(wrapper -> {
                    final int hand = wrapper.read(Types.VAR_INT); // 2 - Hand

                    final Item item;
                    if (hand == 0) {
                        item = Via.getManager().getProviders()
                            .get(HandItemProvider.class).getHandItem(wrapper.user());
                    } else {
                        // Off-hand
                        OffHandItemProvider offHandProvider = Via.getManager().getProviders().get(OffHandItemProvider.class);
                        item = offHandProvider.getOffHandItem(wrapper.user());
                        if (item == null) {
                            wrapper.cancel();
                            return;
                        }
                        // Pre : met l'item off-hand en main principale sur le thread Bukkit
                        // Le paquet part ensuite, le serveur voit le bon item en main
                        Via.getPlatform().runSync(() -> offHandProvider.prePlacement(wrapper.user()));
                        // Post : restore la main principale au tick suivant
                        Via.getPlatform().runSync(() -> offHandProvider.postPlacement(wrapper.user()));
                    }

                    wrapper.write(Types.ITEM1_8, item); // 3 - Item
                });
                map(Types.UNSIGNED_BYTE); // 4 - X
                map(Types.UNSIGNED_BYTE); // 5 - Y
                map(Types.UNSIGNED_BYTE); // 6 - Z

                handler(wrapper -> {
                    CommandBlockProvider provider = Via.getManager().getProviders().get(CommandBlockProvider.class);
                    BlockPosition pos = wrapper.get(Types.BLOCK_POSITION1_8, 0);
                    Optional<CompoundTag> tag = provider.get(wrapper.user(), pos);
                    if (tag.isPresent()) {
                        PacketWrapper updateBlockEntity = PacketWrapper.create(ClientboundPackets1_9.BLOCK_ENTITY_DATA, null, wrapper.user());
                        updateBlockEntity.write(Types.BLOCK_POSITION1_8, pos);
                        updateBlockEntity.write(Types.UNSIGNED_BYTE, (short) 2);
                        updateBlockEntity.write(Types.NAMED_COMPOUND_TAG, tag.get());
                        updateBlockEntity.scheduleSend(Protocol1_8To1_9.class);
                    }
                });

                if (!Via.getConfig().cancelBlockSounds()) {
                    return;
                }
                handler(wrapper -> {
                    int face = wrapper.get(Types.UNSIGNED_BYTE, 0);
                    if (face == 255) return;
                    BlockPosition p = wrapper.get(Types.BLOCK_POSITION1_8, 0);
                    int x = p.x();
                    int y = p.y();
                    int z = p.z();
                    switch (face) {
                        case 0 -> y--;
                        case 1 -> y++;
                        case 2 -> z--;
                        case 3 -> z++;
                        case 4 -> x--;
                        case 5 -> x++;
                    }
                    EntityTracker1_9 tracker = wrapper.user().getEntityTracker(Protocol1_8To1_9.class);
                    tracker.addBlockInteraction(new BlockPosition(x, y, z));
                });
            }
        });
    }
}
