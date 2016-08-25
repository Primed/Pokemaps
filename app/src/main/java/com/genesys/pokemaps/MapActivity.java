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
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.genesys.pokemaps.helpers.GameManager;
import com.genesys.pokemaps.helpers.LocationManager;
import com.genesys.pokemaps.helpers.LocationManager.Listener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.map.pokemon.NearbyPokemon;
import com.pokegoapi.api.pokemon.PokemonMetaRegistry;
import com.pokegoapi.exceptions.AsyncPokemonGoException;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.NoSuchItemException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.util.PokeDictionary;

import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MapActivity extends AppCompatActivity implements
        OnMapReadyCallback,
        Listener,
        GameManager.LoginListener {

    /**
     * The name of this class for use in debugging purposes.
     */
    private static final String TAG = "MapActivity";
    /**
     * Radius in which our current position can access things in meters
     * This is defined by the Pokemon GO settings; this is simply
     * for the on-screen indicator.
     */
    private static final long SCAN_RADIUS = 70;

    /**
     * The default level of zoom when the map initially launches. This is also the level of zoom
     * the camera snaps to when the locate button is clicked.
     */
    private static final int DEFAULT_ZOOM = 17;

    /**
     * The rate at which game components update in milliseconds.
     */
    private static final long GAME_REFRESH_RATE = 3000;

    /**
     * Our Google Map object. We can use this to manipulate various map options.
     */
    protected GoogleMap mMap;

    /**
     * The level of zoom the map is currently set to.
     */
    private int currentZoom = DEFAULT_ZOOM;

    /**
     * Our Pokemon GO game manager. This object handles all the heavy stuff for us.
     */
    private GameManager gameManager;

    /**
     * This is the container for our MapActivity views. It's really only used for making
     * SnackBars.
     */
    private CoordinatorLayout mapViewGroup;

    /**
     * This is the container for the nearby pokemon.
     */
    private GridLayout nearbyContainer;

    /**
     * The text that is displayed when no pokemon are nearby.
     */
    private TextView nearbyTextView;

    /**
     * Our location manager. It get our location for us and handles automatic location updates.
     */
    private LocationManager locationManager;

    /**
     * Our current location. Pretty self-explanatory.
     */
    private Location location;

    /**
     * The same as the above mentioned location object, only converted to latiude and longitude.
     */
    private LatLng position;

    /**
     * This object contains relevant data for a circle that is displayed on our map at our location.
     * It indicates the radius in which our Pokemon GO character can interact with other elements.
     */
    private Circle scanCircle;

    /**
     * Stays true until the first time it's been accessed, the becomes false. This creates a branch
     * that allows only a single passthrough.
     */
    private boolean firstLocationFlag = true;

    /**
     * Our preferences object. Do I even have to explain this one?
     */
    private SharedPreferences preferences;

    /**
     * Our System vibrator object.
     */
    private Vibrator vibrator;

    /* Overridden parent methods */

    /**
     * The first method that is called when this activity starts.
     *
     * @param savedInstanceState The instances that were saved on onStop().
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Here's where we set up our toolbar. This subsequently calls onCreateOptionsMenu().
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Get our preferences.
        preferences = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        // Set up our vibrator.
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        setupGameManager();

        // @layout/activity_map views.
        mapViewGroup = (CoordinatorLayout) findViewById(R.id.map_viewgroup);
        nearbyContainer = (GridLayout) findViewById(R.id.nearby_container);
        nearbyTextView = (TextView) nearbyContainer.findViewById(R.id.no_nearby_text_view);

        findViewById(R.id.plus_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.zoomIn());
                }
            }
        });

        findViewById(R.id.minus_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.zoomOut());
                }
                gameManager.farmXP();
            }
        });

        setupLocationManager();

        startPokemoonLoop();
    }

    /**
     * Gets our game manager instance and sets the login completed listener.
     */
    private void setupGameManager() {
        gameManager = GameManager.getInstance(this);
        gameManager.setOnLoginCompletedListener(new GameManager.LoginListener() {
            @Override
            public void onLoginCompleted(GameManager.LoginResult loginResult) {
                // TODO: Do something when login is completed.
                Utils.debug(this, "Login completed on MapActivity.");
            }
        });
    }

    /**
     * Gets our location manager instance and registers this object as a listener.
     */
    private void setupLocationManager() {
        locationManager = LocationManager.getInstance(this);
        locationManager.register(this);
    }

    public void startPokemoonLoop() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (gameManager != null && (location != null)) {
                    long startTime = System.currentTimeMillis();
                    try {
                        // Updates our game location to match our real location.
                        gameManager.setPlayerLocation(location);

                        // Update our pokestops and add a marker on our map at its location.
                        // TODO: Add a map marker for pokestops.
                        // gameManager.updatePokestops();

                        // Cycles through our pokestops and loots them if the option is available.
                        for (PokestopLootResult lootResult : gameManager.lootPokestops()) {
                            switch (lootResult.getResult()) {
                                case SUCCESS:
                                    showSnackBar("Pokestop was successfully looted. Gained "
                                            + lootResult.getExperience() + " XP");
                                    Log.i(TAG, "Pokestop was successfully looted. Gained"
                                            + lootResult.getExperience() + " XP and "
                                            + lootResult.getItemsAwarded().size() + " items.");
                                    Utils.vibrate(Constants.POKESTOP_VIBRATION_PATTERN, vibrator);
                                    break;
                                case INVENTORY_FULL:
                                    String invMsg = "Inventory too full to loot Pokestop";
                                    showSnackBar(invMsg);
                                    Log.i(TAG, invMsg);
                                    break;
                                case IN_COOLDOWN_PERIOD:
                                    String coolMsg = "Pokestop is currently in cooldown";
                                    showSnackBar(coolMsg);
                                    Log.i(TAG, coolMsg);
                                    break;
                                default:
                                    String errMsg = "Couldn't loot pokestop due to error " + lootResult.getResult().name();
                                    showSnackBar(errMsg);
                                    Log.i(TAG, errMsg);
                            }
                        }

                        final List<NearbyPokemon> nearbyPokemon = gameManager.getNearbyPokemon();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                for (int i = 0; i < nearbyContainer.getChildCount(); i++) {
                                    View child = nearbyContainer.getChildAt(i);
                                    if (child.getId() != R.id.no_nearby_text_view && child.getParent() == nearbyContainer) {
                                        nearbyContainer.removeViewAt(i--);
                                    }
                                }

                                if (nearbyPokemon.size() > 0) {
                                    // There are nearby Pokemon!
                                    // Set our TextView visibility to gone.
                                    nearbyTextView.setVisibility(View.GONE);
                                    for (NearbyPokemon pokemon : nearbyPokemon) {
                                        Log.i(TAG, pokemon.getPokemonId().name() + " is nearby.");

                                        ViewGroup nearbyPokemonLayout = (ViewGroup) getLayoutInflater()
                                                .inflate(R.layout.nearby_pokemon, null);

                                        ImageView pokemonImage = (ImageView) nearbyPokemonLayout
                                                .getChildAt(1);
                                        ImageView pokemonBackground = (ImageView) nearbyPokemonLayout
                                                .getChildAt(0);

                                        int id = getResources().getIdentifier(pokemon.getPokemonId().name().toLowerCase(),
                                                "drawable",
                                                getPackageName());
                                        pokemonImage.setImageResource(id);

                                        int backgroundColor;
                                        switch (PokemonMetaRegistry.getMeta(pokemon.getPokemonId()).getPokemonClass()) {
                                            case VERY_COMMON:
                                                backgroundColor = Utils.getColor(MapActivity.this, R.color.veryCommonColor);
                                                break;
                                            case COMMON:
                                                backgroundColor = Utils.getColor(MapActivity.this, R.color.commonColor);
                                                break;
                                            case UNCOMMON:
                                                backgroundColor = Utils.getColor(MapActivity.this, R.color.uncommonColor);
                                                break;
                                            case RARE:
                                                backgroundColor = Utils.getColor(MapActivity.this, R.color.rareColor);
                                                break;
                                            case VERY_RARE:
                                                backgroundColor = Utils.getColor(MapActivity.this, R.color.veryRareColor);
                                                break;
                                            case EPIC:
                                                backgroundColor = Utils.getColor(MapActivity.this, R.color.epicColor);
                                                break;
                                            case LEGENDARY:
                                                backgroundColor = Utils.getColor(MapActivity.this, R.color.legendaryColor);
                                                break;
                                            case MYTHIC:
                                                backgroundColor = Utils.getColor(MapActivity.this, R.color.mythicColor);
                                                break;
                                            default:
                                                backgroundColor = Color.WHITE;
                                        }

                                        pokemonBackground.setColorFilter(backgroundColor);

                                        nearbyContainer.addView(nearbyPokemonLayout);
                                    }
                                } else {
                                    nearbyTextView.setVisibility(View.VISIBLE);
                                }
                            }
                        });

                        // Update our pokemon and add a marker on the map at its location.
                        // TODO: Add a map marker for catchable pokemon.
                        //gameManager.updateCatchablePokemon();

                        GameManager.Catch catchResult = gameManager.catchPokemon();
                        if (catchResult != null) {
                            String pokemon = PokeDictionary.getDisplayName(
                                    catchResult.getCatchablePokemon().getPokemonId().getNumber(),
                                    Locale.ENGLISH);
                            String message;
                            switch (catchResult.getCatchResult().getStatus()) {
                                case CATCH_SUCCESS:
                                    message = pokemon + " successfully captured";
                                    Utils.vibrate(Constants.POKEMON_VIBRATION_PATTERN, vibrator);
                                    break;
                                case CATCH_FLEE:
                                    message = pokemon + " fled";
                                    break;
                                case CATCH_MISSED:
                                    message = pokemon + " missed";
                                    break;
                                default:
                                    message = "Unable to catch " + pokemon;
                            }
                            showSnackBar(message);
                            Log.i(TAG, message);
                        }

                        gameManager.updateGyms();

                    } catch (LoginFailedException e) {
                        showSnackBar("Login failed. Credentials changed");
                    } catch (RemoteServerException e) {
                        showSnackBar("Login failed. Servers may be down");
                    } catch (NoSuchItemException e) {
                        showSnackBar("Not enough pokeballs to catch pokemon");
                    } catch (AsyncPokemonGoException e) {
                        String username = preferences.getString(getString(R.string.username_preference_key), null);
                        String password = preferences.getString(getString(R.string.password_preference_key), null);
                        if (username != null || password != null) {
                            gameManager.loginPTC(username, password);
                        }
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    double scanTimeSeconds = (double) (System.currentTimeMillis() - startTime) / 1000;
                    Log.i(TAG, "World scanning completed in " + scanTimeSeconds + " seconds.");
                }
            }
        }, 0, GAME_REFRESH_RATE);
    }

    /**
     * This method inflates our options menu into our toolbar options menu from our menu xml.
     *
     * @param menu The menu to be inflated.
     * @return Whether or not the menu was inflated successfully.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    /**
     * This method handles options menu clicks. Any desired menu action should be defined here.
     *
     * @param item The menu item that was selected
     * @return Whether or not action click was successful.
     */
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

    /**
     * Calls after onCreate(), when the Activity starts.
     */
    @Override
    public void onStart() {
        locationManager.onStart();
        super.onStart();
    }

    /**
     * Calls when the activity
     */
    @Override
    public void onStop() {
        locationManager.onStop();
        super.onStop();
    }

    /* Overridden methods from OnMapReadyCallback */

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
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
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        }

        mMap.getUiSettings().setScrollGesturesEnabled(false);
        mMap.getUiSettings().setZoomGesturesEnabled(false);
        mMap.getUiSettings().setCompassEnabled(false);
    }

    /**
     * Updates our scan circle to match our location
     */
    public void updateScanCircle() {
        scanCircle.setCenter(position);
    }

    /**
     * Updates the camera position to match our location
     */
    public void updateCameraLocation() {
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder(mMap.getCameraPosition())
                        .target(position)
                        .build()));
    }

    /**
     * This method runs when the location is received for the first time.
     */
    public void setupCamera() {
        // We might as well zoom to our location for the first time.
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder(mMap.getCameraPosition())
                        .target(position)
                        .zoom(currentZoom)
                        .tilt(30)
                        .build()), 1, null);

        // Also, let's add a little circle scan thing. I don't know what else to call it.
        // It's a circle that shows how far out the scan goes, so henceforth it will be known
        // as circle scan thing.
        scanCircle = mMap.addCircle(new CircleOptions()
                .center(position)
                .radius(SCAN_RADIUS)
                .strokeWidth(4)
                .strokeColor(Utils.getColor(this, R.color.colorPrimary))
                .fillColor(Utils.getColorWithAlpha(Utils.getColor(this, R.color.colorPrimary), 0.3f)));
    }

    /**
     * Handy method that shows a snack bar.
     *
     * @param msg The message that is displayed in the snack bar.
     */
    public void showSnackBar(String msg) {
        Snackbar.make(mapViewGroup, msg, Snackbar.LENGTH_LONG).show();
    }

    /**
     * This method is called every time we receive a location update from our location manager.
     *
     * @param location Updated location.
     */
    @Override
    public void onLocationChanged(Location location) {
        // We can't do anything if the location is null, therefore we'll return.
        if (location == null) return;

        // Update our location variables.
        this.location = location;
        position = new LatLng(location.getLatitude(), location.getLongitude());

        if (firstLocationFlag) {
            // This is the first time we've gotten a location update. From here, we'll set up our
            // map indicators.
            setupCamera();
            firstLocationFlag = false;
        } else {
            updateScanCircle();
            updateCameraLocation();
        }

        // Utils.debug(this, "Location updated! " + position.latitude + ", " + position.longitude);
    }

    /**
     * This method is run whenever the user logs in from this method.
     *
     * @param loginResult The login result.
     */
    @Override
    public void onLoginCompleted(GameManager.LoginResult loginResult) {
        showSnackBar(loginResult.getMessage());
    }
}