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
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.util.Log;

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
     * Prints a log to the console only if debugging is enabled in com.grid.abacus.Constants.
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
        builder.setMessage(message).setTitle(title).setPositiveButton("OK", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
