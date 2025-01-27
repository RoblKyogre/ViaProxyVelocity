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

import java.util.Arrays;

public class VelocityConstants {

    public static final String BASE_CHANNEL = "velocity:";
    public static final String INFO_CHANNEL = BASE_CHANNEL + "player_info";

    public static final int MODERN_FORWARDING_DEFAULT = 1;
    public static final int MODERN_FORWARDING_WITH_KEY = 2;
    public static final int MODERN_FORWARDING_WITH_KEY_V2 = 3;
    public static final int MODERN_LAZY_SESSION = 4;

    private static final int[] SUPPORTED_FORWARDING_VERSION = {
        MODERN_FORWARDING_DEFAULT,
        MODERN_LAZY_SESSION,
    };
}
