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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import okhttp3.OkHttpClient;

public class MapActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener {

    // MapActivity views
    private GoogleMap mMap;
    private CoordinatorLayout mapViewGroup;
    private FloatingActionButton scanFab;

    // Google Play Service components
    GoogleApiClient googleApiClient;
    Location lastLocation;

    // Pokemon Go components
    OkHttpClient http;
    PokemonGo go;

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
        scanFab = (FloatingActionButton) findViewById(R.id.scan_fab);

        // Get our preferences.
        preferences = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        // Create an instance of GoogleAPIClient.
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        // Set our scan floating action bar click action.
        // In this case, scan for Pokemon. Duh.
        scanFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Implement Pokemon scanning
            }
        });

        // Login. Nothing much here.
        new Thread(new LoginThread()).start();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
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
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

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
     * This method is run when the app is connected to Google Play Services.
     *
     * @param bundle Google arguments.
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    googleApiClient);
            if (lastLocation != null) {
                showSnackBar("Location found");
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(),
                        lastLocation.getLongitude()), 13));
                // Sets the center of the map to location user
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()))
                        .zoom(15)                   // Sets the zoom
                        .build();                   // Creates a CameraPosition from the builder
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                mMap.addCircle(new CircleOptions()
                        .strokeColor(getResources().getColor(R.color.colorPrimary))
                        .fillColor(Utils.getColorWithAlpha(getResources().getColor(R.color.colorPrimary), 0.3f))
                        .strokeWidth(3)
                        .visible(true)
                        .radius(70)
                        .center(new LatLng(lastLocation.getLatitude(),
                                lastLocation.getLongitude())));
                // If we're logged into Pokemon Go, update our player's location.
                if (go != null) {
                    go.setLocation(lastLocation.getLatitude(),
                            lastLocation.getLongitude(),
                            lastLocation.getAltitude());
                }
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    /* Instance methods */

    protected void onStart() {
        googleApiClient.connect();
        super.onStart();
        showSnackBar("Google Services connected");
    }

    protected void onStop() {
        googleApiClient.disconnect();
        super.onStop();
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
                    Snackbar.make(mapViewGroup, "Login successful!", Snackbar.LENGTH_LONG).show();
                }
            } catch (LoginFailedException e) {
                // If the login failed because of invalid credentials, display a Snackbar.
                Snackbar.make(mapViewGroup, "Incorrect username or password", Snackbar.LENGTH_LONG).show();
                return;
            } catch (RemoteServerException e) {
                // Also show a snackbar if the server is busy. Indicate retrying status.
                Snackbar.make(mapViewGroup, "Failed to connect. Retrying...", Snackbar.LENGTH_LONG).show();
                return;
            }

            // If our location is available, update our Pokemon Go player's location.
            if (lastLocation != null) {
                go.setLocation(lastLocation.getLatitude(),
                        lastLocation.getLongitude(),
                        lastLocation.getAltitude());
            }
        }
    }
}
