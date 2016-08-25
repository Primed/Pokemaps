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

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.genesys.pokemaps.helpers.GameManager;
import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Hand crafted by Primed with love for the Pokemaps project.
 * Created on 8/11/16 at 10:18 PM
 * https://github.com/Primed/Pokemaps
 */

public class LoginActivity extends AppCompatActivity implements GameManager.LoginListener {

    // @layout/activity_login.xml views.
    private CoordinatorLayout loginViewGroup;
    private LinearLayout loginContainer;
    private EditText usernameEditText;
    private EditText passwordEditText;
    protected Button loginButton;
    protected TextView signUpTextView;
    private ProgressBar loginProgressBar;

    // Pokemon Go components
    private GameManager gameManager;

    // Firebase stuff
    protected FirebaseAnalytics mFirebaseAnalytics;

    // Other objects
    private SharedPreferences preferences;

    /* Overridden parent methods */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Start Firebase analytics.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // Get our preferences
        preferences = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        // Get our views from our layout.
        loginViewGroup = (CoordinatorLayout) findViewById(R.id.login_viewgroup);
        loginContainer = (LinearLayout) findViewById(R.id.login_container);
        usernameEditText = (EditText) findViewById(R.id.username_edit_text);
        passwordEditText = (EditText) findViewById(R.id.password_edit_text);
        loginButton = (Button) findViewById(R.id.login_button);
        signUpTextView = (TextView) findViewById(R.id.sign_up_text_view);
        loginProgressBar = (ProgressBar) findViewById(R.id.login_progress_bar);

        if (!preferences.getBoolean(getString(R.string.warning_preference_key), false)) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage(getString(R.string.login_warning))
                    .setPositiveButton("Got it", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean(getString(R.string.warning_preference_key), true);
                            editor.apply();
                            checkForLocationPermission();
                        }
                    })
                    .show();
        } else {
            checkForLocationPermission();
        }

        setupGameManager();

        // Launch MapActivity automatically if username and password are stored.
        final String usernameTemp = preferences.getString(getString(R.string.username_preference_key), null);
        final String passwordTemp = preferences.getString(getString(R.string.password_preference_key), null);
        if (usernameTemp != null && passwordTemp != null) {
            // Show the users a progress bar so they know something's up.
            setLoginProgressBarVisible(true);
            // Username and password are stored in our preferences. It's now safe to login and
            // launch the map activity.
            if (gameManager != null) {
                gameManager.loginPTC(usernameTemp, passwordTemp);
            }
        }

        // Set our login click listener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (gameManager != null) {
                    // Show our progress bar so users know something's up.
                    setLoginProgressBarVisible(true);

                    // Get our username and password from our EditTexts.
                    final String username = usernameEditText.getText().toString();
                    final String password = passwordEditText.getText().toString();

                    gameManager.loginPTC(username, password);
                }
            }
        });

        // When sign up text is clicked, open up sign up page in default
        // browser window.
        signUpTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent signUpIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://club.pokemon.com/us/pokemon-trainer-club/sign-up/"));
                startActivity(signUpIntent);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case Constants.REQUEST_FINE_LOCATION_KEY: {
                // If request is cancelled, the result arrays are empty.
                // Permission was granted, yay!
                Constants.locationEnabled = grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            }
        }
    }

    /* Instance methods */

    private void setupGameManager() {
        gameManager = GameManager.getInstance(this);
        gameManager.setOnLoginCompletedListener(this);
    }

    private void setLoginProgressBarVisible(boolean visible) {
        if (visible) {
            if (loginContainer != null) loginContainer.setVisibility(View.GONE);
            if (loginProgressBar != null) loginProgressBar.setVisibility(View.VISIBLE);
        } else {
            if (loginContainer != null) loginContainer.setVisibility(View.VISIBLE);
            if (loginProgressBar != null) loginProgressBar.setVisibility(View.GONE);
        }
    }

    /**
     * This method simply shows a customizable snackbar error message
     * mainly for RemoteServerExceptions. It also has a neat
     * server status checker button. You're welcome.
     *
     * @param msg The message to be shown on the snackbar itself.
     */
    private void showRemoteServerError(String msg) {
        Snackbar.make(loginViewGroup, msg, Snackbar.LENGTH_LONG)
                .setAction("status", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent upStatusIntent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("http://ispokemongodownornot.com/"));
                        startActivity(upStatusIntent);
                    }
                }).show();
    }

    public void launchMapActivity() {
        // Start the new activity
        Intent mapActivityIntent = new Intent(this, MapActivity.class);
        startActivity(mapActivityIntent);
        // Finish this activity so it's removed from the back stack
        finish();
    }

    public void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                Constants.REQUEST_FINE_LOCATION_KEY);
    }

    public void checkForLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            // Here, thisActivity is the current activity
            // This is where we check if GPS location access has been granted.
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // Explanations are shown if the user declines the request once,
                // and doesn't select "Don't ask again", and supports the permission.
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Location")
                            .setMessage(getString(R.string.app_name) + " needs permission to access GPS location. You" +
                                    " can still use this app without it, but you won't have access " +
                                    "to any location features.")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    requestLocationPermission();
                                }
                            }).show();
                } else {
                    // No explanation needed, we can request the permission.
                    requestLocationPermission();
                }
            }
        }
    }

    public void showSnackBar(String msg) {
        Snackbar.make(loginViewGroup, msg, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onLoginCompleted(final GameManager.LoginResult loginResult) {
        Utils.debug(this, "Login completed in LoginActivity.");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (loginResult.getResult() == GameManager.Result.SUCCESS) {
                    launchMapActivity();
                } else {
                    setLoginProgressBarVisible(false);
                    showSnackBar(loginResult.getMessage());
                }
            }
        });
    }
}
