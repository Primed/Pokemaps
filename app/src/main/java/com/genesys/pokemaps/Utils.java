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

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Vibrator;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.util.PokeDictionary;

import java.util.Locale;

/**
 * Hand crafted by Primed with love for the Pokemaps project.
 * Created on 8/12/16 at 3:47 AM
 * https://github.com/Primed/Pokemaps
 */

public class Utils {

    /**
     * Prints a log to the console for debugging purposes.
     *
     * @param priority Priority level of message. (see Log class for more info).
     * @param context Always use 'this' for this parameter.
     * @param message The message to stream to console.
     */
    public static void log(int priority, Object context, String message) {
        Log.println(priority, context.getClass().getSimpleName(), message);
    }

    /**
     * Prints a log to the console for debugging purposes.
     *
     * @param context Always use 'this' for this parameter.
     * @param message The message to stream to console.
     */
    public static void log(Object context, String message) {
        log(Log.INFO, context, message);
    }

    /**
     * Prints a log to the console only if debugging is enabled in com.grid.pokemaps.Constants.
     *
     * @param context Always use 'this' for this parameter.
     * @param message The message to stream to console.
     */
    public static void debug(Object context, String message) {
        if (Constants.DEBUG) {
            log(context, message);
        }
    }

    /**
     * Creates and displays a bare-bones alert dialog based on the parameters.
     *
     * @param context The activity this message is launched from (usually 'this').
     * @param title The String title to use for the alert.
     * @param message The String message to be displayed in the alert.
     */
    public static void alert(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message).setPositiveButton("OK", null);
        if (title != null) {
            builder.setTitle(title);
        }
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Converts a solid color to a color with an alpha value.
     *
     * @param color Color value. (Color, not resource ID for that color)
     * @param ratio Alpha ratio. (0-1)
     * @return Color with specified alpha multiple.
     */
    public static int getColorWithAlpha(int color, float ratio) {
        int alpha = Math.round(Color.alpha(color) * ratio);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    /**
     * Gets the English name of the specified Pokemon object.
     *
     * @param pokemon The Pokemon to identify.
     * @return The name of the Pokemon.
     */
    public static String getPokemonName(Pokemon pokemon) {
        return getPokemonName(pokemon.getPokemonId().getNumber());
    }

    /**
     * Gets the English name of the specified Pokemon from its ID.
     *
     * @param id The Pokedex number of the Pokemon you'd like to identify.
     * @return The name of the Pokemon.
     */
    public static String getPokemonName(int id) {
        return PokeDictionary.getDisplayName(id, Locale.ENGLISH);
    }

    /**
     * Gets the color using the relevant APIs for the current version.
     *
     * @param context Activity context.
     * @param id Color resource ID.
     * @return The generated color.
     */
    public static int getColor(Context context, int id) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            return context.getColor(id);
        }
        return context.getResources().getColor(id, context.getTheme());
    }

    public static void vibrate(long[] pattern, Vibrator vibrator) {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(pattern, -1);
        }
    }
}
