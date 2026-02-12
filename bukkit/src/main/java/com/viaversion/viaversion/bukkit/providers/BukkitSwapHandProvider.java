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

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.bukkit.events.SwapHandEvent;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.SwapHandProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public class BukkitSwapHandProvider extends SwapHandProvider {
    @Override
    public void onSwapHand(UserConnection user) {
        UUID uuid = user.getProtocolInfo().getUuid();
        Via.getPlatform().runSync(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Bukkit.getPluginManager().callEvent(new SwapHandEvent(player));
            }
        });
    }
}
