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
package com.viaversion.viaversion.protocols.v1_8to1_9.provider;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.platform.providers.Provider;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class OffHandItemProvider implements Provider {

    private static Function<UUID, Item> itemCallback;
    private static Consumer<UUID> prePlacementCallback;
    private static Consumer<UUID> postPlacementCallback;

    public static void setCallbacks(
        Function<UUID, Item> itemCb,
        Consumer<UUID> preCb,
        Consumer<UUID> postCb
    ) {
        itemCallback = itemCb;
        prePlacementCallback = preCb;
        postPlacementCallback = postCb;
    }

    public Item getOffHandItem(UserConnection user) {
        if (itemCallback == null) return null;
        return itemCallback.apply(user.getProtocolInfo().getUuid());
    }

    public void prePlacement(UserConnection user) {
        if (prePlacementCallback != null)
            prePlacementCallback.accept(user.getProtocolInfo().getUuid());
    }

    public void postPlacement(UserConnection user) {
        if (postPlacementCallback != null)
            postPlacementCallback.accept(user.getProtocolInfo().getUuid());
    }
}
