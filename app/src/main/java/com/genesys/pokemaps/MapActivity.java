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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.gym.Gym;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.map.pokemon.CatchResult;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.map.pokemon.NearbyPokemon;
import com.pokegoapi.api.map.pokemon.encounter.EncounterResult;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.NoSuchItemException;
import com.pokegoapi.exceptions.RemoteServerException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import POGOProtos.Inventory.Item.ItemIdOuterClass;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass;
import okhttp3.OkHttpClient;

public class MapActivity extends AppCompatActivity implements
        ConnectionCallbacks,
        OnMapReadyCallback,
        LocationListener,
        OnConnectionFailedListener {

    // Time in milliseconds between location updates.
    private static final long LOCATION_INTERVAL = 2000;

    // Minimum time in milliseconds between location updates.
    private static final long MIN_LOCATION_INTERVAL = 1000;

    // Radius in which our current position can access things in meters
    // This is defined by the Pokemon GO settings; this is simply
    // for the on-screen indicator.
    private static final long SCAN_RADIUS = 70;
    private static final int DEFAULT_ZOOM = 17;

    // The rate at which game components update in milliseconds.
    private static final long GAME_REFRESH_RATE = 3000;

    // MapActivity views
    protected GoogleMap mMap;
    private CoordinatorLayout mapViewGroup;
    private View overlay;

    // Google Play Service components
    private GoogleApiClient googleApiClient;

    // Location components
    protected LocationRequest locationRequest;
    private Location location;
    private LatLng position;
    private Circle scanCircle;
    private boolean followUserLocation = true;

    // Pokemon Go components
    protected OkHttpClient http;
    protected PokemonGo go;

    // Preference objects
    private SharedPreferences preferences;

    /* Overridden parent methods */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Here's where we set up our toolbar and open it up to handle click responses.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // @layout/activity_map views.
        mapViewGroup = (CoordinatorLayout) findViewById(R.id.map_viewgroup);
        overlay = findViewById(R.id.overlay);

        overlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                followUserLocation = false;
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                return false;
            }
        });

        // Get our preferences.
        preferences = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Create an instance of GoogleAPIClient.
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        // Login. Nothing much here.
        new Thread(new LoginThread()).start();
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable map location features such as the 'show location' button.
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
                @Override
                public boolean onMyLocationButtonClick() {
                    followUserLocation = true;
                    mMap.getUiSettings().setMyLocationButtonEnabled(false);
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                            new CameraPosition.Builder(mMap.getCameraPosition())
                                    .target(position)
                                    .zoom(DEFAULT_ZOOM)
                                    .build()));
                    return true;
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_sign_out) {
            // Clear our login data and all preferences.
            preferences.edit().clear().commit();
            // Start the main Login activity.
            Intent loginIntent = new Intent(this, LoginActivity.class);
            startActivity(loginIntent);
            // Finish this activity.
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        googleApiClient.connect();
        Utils.debug(this, "Google Play Services connected");
        super.onStart();
    }

    @Override
    public void onStop() {
        googleApiClient.disconnect();
        Utils.debug(this, "Google Play Services disconnected");
        super.onStop();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Utils.debug(this, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Utils.debug(this, "Connection failed");
    }

    @Override
    public void onConnected(Bundle bundle) {
        // Create a new LocationRequest to update our location based on our specified intervals.
        // TODO: Create a battery saver option that changes accuracy level and refresh rate.
        locationRequest = LocationRequest.create()
                .setInterval(LOCATION_INTERVAL)
                .setFastestInterval(MIN_LOCATION_INTERVAL)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // This statement starts our location updater. Nice.
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);

            // Get our first location.
            location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            // Convert it to a latitude/longitude coordinate
            position = new LatLng(location.getLatitude(), location.getLongitude());
            // Now that Google Play Services are connected, we might as well zoom to our location
            // for the first time.
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                    new CameraPosition.Builder()
                            .target(position)
                            .zoom(DEFAULT_ZOOM)
                            .build()));
            // Also, let's add a little circle scan thing. I don't know what else to call it.
            // It's a circle that shows how far out the scan goes, so henceforth it will be known
            // as circle scan thing.
            scanCircle = mMap.addCircle(new CircleOptions()
                    .center(position)
                    .radius(SCAN_RADIUS)
                    .strokeWidth(4)
                    .strokeColor(getResources().getColor(R.color.colorPrimary))
                    .fillColor(Utils.getColorWithAlpha(getResources().getColor(R.color.colorPrimary), 0.3f)));
            // Lastly, since we follow the user by default, we can get rid of the
            // 'center location' button.
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Utils.debug(this, "Location updated.");

        this.location = location;
        position = new LatLng(location.getLatitude(), location.getLongitude());

        updateScanCircle();
        if (followUserLocation) {
            updateCameraLocation();
        }
    }

    public void updateScanCircle() {
        scanCircle.setCenter(position);
    }

    public void updateCameraLocation() {
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder(mMap.getCameraPosition())
                        .target(position)
                        .zoom(DEFAULT_ZOOM)
                        .build()));
    }

    public void updateWorld() {
        if (go != null) {
            go.setLocation(position.latitude, position.longitude, location.getAltitude());

            try {
                List<NearbyPokemon> nearbyPokemon = go.getMap().getNearbyPokemon();
                Utils.debug(this, "There are " + nearbyPokemon.size() + " pokemon nearby.");
                for (NearbyPokemon pokemon : nearbyPokemon) {
                    Utils.debug(this, "There is a " + pokemon.getPokemonId().name() + " nearby.");
                }

                List<CatchablePokemon> catchablePokemon = go.getMap().getCatchablePokemon();
                Utils.debug(this, "There are " + catchablePokemon.size() + " catchable pokemon nearby.");
                for (CatchablePokemon pokemon : catchablePokemon) {
                    Utils.debug(this, "There is a " + pokemon.getPokemonId().name() + " at "
                            + pokemon.getLatitude() + ", " + pokemon.getLongitude() + ". It expires" +
                            " in " + pokemon.getExpirationTimestampMs() / 1000 + " seconds.");
                    EncounterResult encResult = pokemon.encounterPokemon();
                    // If the encounter was successful, catch it!
                    if (encResult.wasSuccessful()) {
                        double captureProbability = encResult.getCaptureProbability().getCaptureProbability(0);
                        Utils.debug(this, "Catching " + pokemon.getPokemonId() +
                                ". Capture probability: " + captureProbability);

                        // TODO: Create a setting that allows for more pokeballs and razz berries to be used with rarer pokemon
                        CatchResult result = pokemon.catchPokemonBestBallToUse(encResult, new ArrayList<ItemIdOuterClass.ItemId>(), -1, 3);
                        if (result.getStatus() == CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus.CATCH_SUCCESS) {
                            // Success!
                            showSnackBar(pokemon.getPokemonId() + " captured");
                            Utils.debug(this, "Capture success! Throw accuracy: " + (1 - result.getMissPercent()));
                        } else if (result.getStatus() == CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus.CATCH_FLEE) {
                            showSnackBar("Pokemon fled");
                        }
                    }
                }

                List<Gym> gyms = go.getMap().getGyms();
                Utils.debug(this, "There are " + gyms.size() + " gyms nearby.");

                Collection<Pokestop> pokestops = go.getMap().getMapObjects().getPokestops();
                for (Pokestop pokestop : pokestops) {
                    if (pokestop.canLoot() && pokestop.inRange()) {
                        Utils.debug(this, "Attempting to loot pokestop...");
                        PokestopLootResult lootResult = pokestop.loot();
                        if (lootResult.wasSuccessful()) {
                            showSnackBar("Loot successful! Earned " + lootResult.getExperience() + " XP");
                            Utils.debug(this, "Loot successful! Earned " + lootResult.getExperience()
                                    + " XP, and " + lootResult.getItemsAwarded().size() + " items.");
                        } else {
                            Utils.debug(this, "Loot was unsuccessful.");
                        }
                    } else {
                        Utils.debug(this, "Pokestop found and is " + pokestop.getDistance() + " meters away.");
                    }
                }
            } catch (LoginFailedException | RemoteServerException | NoSuchItemException e) {
                e.printStackTrace();
            }
        }
    }

    public void showSnackBar(String msg) {
        Snackbar.make(mapViewGroup, msg, Snackbar.LENGTH_LONG).show();
    }

    private class LoginThread implements Runnable {

        @Override
        public void run() {
            // Here we're basically doing the same thing we did in LoginActivity.
            // I don't feel like rewriting all those witty comments so if you want
            // more of my comedic genius just look back at LoginActivity.

            String username = preferences.getString(getString(R.string.username_preference_key), null);
            String password = preferences.getString(getString(R.string.password_preference_key), null);

            http = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();

            try {
                go = new PokemonGo(new PtcCredentialProvider(http, username, password), http);
                if (go.getAuthInfo().isInitialized()) {
                    // Success!
                    showSnackBar("Login successful");

                    // Start a new thread that loops every 3 seconds.
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            updateWorld();
                        }
                    }, 0, GAME_REFRESH_RATE);
                }
            } catch (LoginFailedException e) {
                // If the login failed because of invalid credentials, display a Snackbar.
                showSnackBar("Incorrect username or password");
                return;
            } catch (RemoteServerException e) {
                // Also show a snackbar if the server is busy. Indicate retrying status.
                showSnackBar("Failed to connect. Retrying...");
                return;
            }

            // If our location is available, update our Pokemon Go player's location.
            if (location != null) {
                go.setLocation(location.getLatitude(),
                        location.getLongitude(),
                        location.getAltitude());
            }
        }
    }
}