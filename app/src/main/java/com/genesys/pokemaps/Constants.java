/*
 * Copyright (C) 2016 Primed
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

package com.genesys.pokemaps;

/**
 * Hand crafted by Primed with love for the Pokemaps project.
 * Created on 8/12/16 at 5:34 AM
 * https://github.com/Primed/Pokemaps
 */

public class Constants {

    // Enables debug actions through the app. Mostly things like debug logging.
    static final boolean DEBUG = true;

    // Location settings
    static final int REQUEST_FINE_LOCATION_KEY = 0;
    static boolean locationEnabled = false;

    static final long[] POKESTOP_VIBRATION_PATTERN = {0, 85, 100, 85};
    static final long[] POKEMON_VIBRATION_PATTERN = {0, 100};
}
