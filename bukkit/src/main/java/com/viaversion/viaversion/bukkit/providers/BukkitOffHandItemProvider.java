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
package com.viaversion.viaversion.bukkit.providers;

import com.viaversion.viaversion.api.minecraft.item.DataItem;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.OffHandItemProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BukkitOffHandItemProvider extends OffHandItemProvider {

    // Stocke la main principale avant le swap temporaire
    private static final Map<UUID, ItemStack> savedMainHand = new HashMap<>();

    public static void register(Plugin viaPlugin) {
        Plugin pvpfix = Bukkit.getPluginManager().getPlugin("PvPFix");

        OffHandItemProvider.setCallbacks(
            // 1. itemCallback — retourne l'item off-hand en format ViaVersion
            uuid -> {
                ItemStack bukkitItem = getOffHandBukkit(pvpfix, uuid);
                if (bukkitItem == null) return null;

                @SuppressWarnings("deprecation")
                int id = bukkitItem.getType().getId();
                return new DataItem(id, (byte) bukkitItem.getAmount(), bukkitItem.getDurability(), null);
            },

            // 2. prePlacementCallback — swap temporaire sur le thread Bukkit
            // Appelé depuis le thread réseau via Via.getPlatform().runSync()
            uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) return;

                ItemStack offHandItem = getOffHandBukkit(pvpfix, uuid);
                if (offHandItem == null) return;

                // Sauvegarde la main principale et met l'item off-hand à la place
                savedMainHand.put(uuid, player.getItemInHand());
                player.setItemInHand(offHandItem);
            },

            // 3. postPlacementCallback — restore la main principale
            uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) return;

                ItemStack saved = savedMainHand.remove(uuid);
                if (saved != null) {
                    player.setItemInHand(saved);
                }
            }
        );
    }

    private static ItemStack getOffHandBukkit(Plugin pvpfix, UUID uuid) {
        if (pvpfix == null) return null;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return null;

        try {
            Method getManager = pvpfix.getClass().getMethod("getOffHandManager");
            Object manager = getManager.invoke(pvpfix);
            Method getItem = manager.getClass().getMethod("getOffHandItem", Player.class);
            return (ItemStack) getItem.invoke(manager, player);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
