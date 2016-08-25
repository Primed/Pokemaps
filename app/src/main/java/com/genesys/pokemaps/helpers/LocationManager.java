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

package com.genesys.pokemaps.helpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;

import com.genesys.pokemaps.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand crafted by Primed with love for the Pokemaps project.
 * Created on 8/19/16 at 12:48 AM
 * https://github.com/Primed/Pokemaps
 */

public class LocationManager implements
        ConnectionCallbacks,
        OnConnectionFailedListener,
        LocationListener {

    /**
     * The interval in which the application requests a location update in milliseconds.
     */
    private static final long LOCATION_REQUEST_INTERVAL = 2000; // In this case, two seconds.

    /**
     * The fastest interval of time between location updates in milliseconds. This is an exact
     * measurement; the app will never update location faster than this rate. It is usually defined
     * as half of the normal LOCATION_REQUEST_INTERVAL, as defined above.
     */
    private static final long FASTEST_LOCATION_REQUEST_INTERVAL = 1000; // In this case, one second.

    /**
     * Basically 'this'
     */
    private static LocationManager instance;

    /**
     * An array of Listeners. When our location is updated, we will notify each of our listeners of
     * the changed value.
     */
    private static List<Listener> listeners;

    /**
     * Our hero that connects us to Google Play Services
     */
    private GoogleApiClient googleApiClient;

    /**
     * The class that handles location requests. But that's self-explanatory, isn't it?
     */
    private LocationRequest locationRequest;

    /**
     * Our Location Manager constructor. This is where the magic happens.
     *
     * @param context Current application context.
     */
    private LocationManager(Context context) {
        createGoogleApiClient(context);
        createLocationRequest();
        listeners = new ArrayList<>();
    }

    /**
     * Only allow access to this class from this getInstance method. This way there's always
     * ony one instance of LocationManager at a time. One instance can be shared between multiple
     * threads at a time.
     *
     * @param context Current application context.
     * @return LocationManager instance.
     */
    public static LocationManager getInstance(Context context) {
        // If an instance hasn't been created yet, create it. Otherwise, we return our current
        // instance.
        if (instance == null) {
            instance = new LocationManager(context);
        }
        return instance;
    }

    /* Instance methods */

    /**
     * If googleApiClient hasn't been initialized, create it and add all our various callbacks.
     *
     * @param context Current application context.
     */
    private void createGoogleApiClient(Context context) {
        // Create an instance of GoogleAPIClient.
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(context)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest()
                .setInterval(LOCATION_REQUEST_INTERVAL)
                .setFastestInterval(FASTEST_LOCATION_REQUEST_INTERVAL)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * I think this method is pretty self-explanatory.
     */
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(googleApiClient.getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    googleApiClient, locationRequest, this);
        }
    }

    /**
     * Adds the specified listener to an array of listeners.
     *
     * @param listener The listener to be added.
     */
    public void register(Listener listener) {
        if (listeners != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes specified listener from the array of listeners.
     *
     * @param listener The listener to be removed.
     */
    public void unregister(Listener listener) {
        if (listeners != null && listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    /**
     * Cycles through each listener and notifies it of the updated location.
     *
     * @param location Updated location.
     */
    private void notifyLocationChanged(Location location) {
        if (listeners != null) {
            for (Listener listener : listeners) {
                listener.onLocationChanged(location);
            }
        }
    }

    /**
     * Run this method from the base Activity's onStart().
     */
    public void onStart() {
        // Check if our Google API Client is valid, then connect.
        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    /**
     * Run this method from the base Activity's onStop().
     */
    public void onStop() {
        // If our Google APIs are initialized, disconnect.
        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
    }

    /* Overridden methods for ConnectionCallbacks */

    /**
     * This method runs once Google API Client connects to Google Play Services.
     * From here, we initialize our location request and start our updater.
     *
     * @param bundle Arguments
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Utils.debug(this, "Connected to Google Play Services.");
        // Add our location request to the builder.
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        // Check to make sure our location settings are satisfactory.
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(googleApiClient,
                        builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can
                        // initialize location requests here.
                        startLocationUpdates();
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        Utils.debug(this, "Unable to satisfy location settings. Automatic updates " +
                                "will not ne enabled. Status message: " + status.getStatusMessage());
                        break;
                }
            }
        });
    }

    /**
     * This method runs when connection to Google Play Services has been suspended.
     *
     * @param i Honestly I don't know that this parameter does. Probably best not to mess
     *          with it. ¯\_(ツ)_/¯
     */
    @Override
    public void onConnectionSuspended(int i) {
        Utils.debug(this, "Connection to Google Play Services suspended.");
    }

    /* Overridden methods for OnConnectionFailedListener */

    /**
     * If, for some reason, Google API Client fails to connect, this method will run.
     *
     * @param connectionResult Connection failure result. It contains various status messages on
     *                         why the failure to connect occurred.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Utils.debug(this, "Google Play connection failed. Status message: "
                + connectionResult.getErrorMessage());
    }

    /* Overridden methods for LocationListener */

    /**
     * This what this whole class was made for. This method is called every time the location is
     * updated. In this case, we'll notify all of our listeners of out updated location.
     *
     * @param location Updated location.
     */
    @Override
    public void onLocationChanged(Location location) {
        notifyLocationChanged(location);
    }

    /**
     * This interface can be implemented by any Activity that wishes to receive location updates.
     * Simply implement this class, then, in onCreate,
     */
    public interface Listener {

        /**
         * This method is called every time the location is updated.
         *
         * @param location Updated location.
         */
        void onLocationChanged(Location location);
    }
}
