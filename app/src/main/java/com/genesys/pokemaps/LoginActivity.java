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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Hand crafted by Primed with love for the Pokemaps project.
 * Created on 8/11/16 at 10:18 PM
 * https://github.com/Primed/Pokemaps
 */

public class LoginActivity extends AppCompatActivity {

    // @layout/activity_login.xml views.
    private CoordinatorLayout loginViewGroup;
    private LinearLayout loginContainer;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private TextView signUpTextView;
    private ProgressBar loginProgressBar;

    // Pokemon Go components
    private OkHttpClient http;
    private PokemonGo go;

    // Firebase stuff
    private FirebaseAnalytics mFirebaseAnalytics;

    // Other objects
    private LoginTask loginThread;
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

        // Launch MapActivity automatically if username and password are stored.
        final String usernameTemp = preferences.getString(getString(R.string.username_preference_key), null);
        final String passwordTemp = preferences.getString(getString(R.string.password_preference_key), null);
        if (usernameTemp != null && passwordTemp != null) {
            // Username and password are stored in our preferences. It's now safe to launch the
            // map activity.
            launchMapActivity();
        }

        // Get our views from our layout.
        loginViewGroup = (CoordinatorLayout) findViewById(R.id.login_viewgroup);
        loginContainer = (LinearLayout) findViewById(R.id.login_container);
        usernameEditText = (EditText) findViewById(R.id.username_edit_text);
        passwordEditText = (EditText) findViewById(R.id.password_edit_text);
        loginButton = (Button) findViewById(R.id.login_button);
        signUpTextView = (TextView) findViewById(R.id.sign_up_text_view);
        loginProgressBar = (ProgressBar) findViewById(R.id.login_progress_bar);

        // Set our login click listener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get our username and password from our EditTexts.
                final String username = usernameEditText.getText().toString();
                final String password = passwordEditText.getText().toString();

                // Instantiate our LoginTask object.
                loginThread = new LoginTask();
                // Start the login process.
                loginThread.execute(username, password);
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
                        }
                    })
                    .show();
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /* Instance methods */

    private void setLoginProgressBarVisible(boolean visible) {
        if (visible) {
            loginContainer.setVisibility(View.GONE);
            loginProgressBar.setVisibility(View.VISIBLE);
        } else {
            loginContainer.setVisibility(View.VISIBLE);
            loginProgressBar.setVisibility(View.GONE);
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
        Snackbar.make(loginViewGroup, "Login failed. Servers may be busy", Snackbar.LENGTH_LONG)
                .setAction("status", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent upStatusIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://ispokemongodownornot.com/"));
                        startActivity(upStatusIntent);
                    }
                }).show();
    }

    public void launchMapActivity() {
        // TODO: Actually launch the MapActivity lol
    }

    public class LoginTask extends AsyncTask<String, Integer, Boolean> {

        private String username;
        private String password;

        /**
         * Logs in and stores username and password.
         *
         * @param params Username and password, respectively. Should
         *               have a length of two.
         * @return Return state. True if login was successful; False
         * if login failed.
         */
        @Override
        protected Boolean doInBackground(final String... params) {
            // Return if params length isn't two. Params needs to contain
            // only username and password, respectively.
            if (params.length != 2) {
                return false;
            }

            username = params[0];
            password = params[1];

            // Next, we'll initiate the OkHttpClient.
            http = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).build();

            // This is where things get real.
            try {
                go = new PokemonGo(new PtcCredentialProvider(http, username, password), http);
                if (go.getAuthInfo().isInitialized()) {
                    // Success!
                    Snackbar.make(loginViewGroup, "Login successful!", Snackbar.LENGTH_LONG).show();
                    launchMapActivity(); // Self explanatory
                }
            } catch (LoginFailedException e) {
                // If the login failed because of invalid credentials, display a Snackbar.
                Snackbar.make(loginViewGroup, "Incorrect username or password", Snackbar.LENGTH_LONG).show();
                return false;
            } catch (RemoteServerException e) {
                // Also show a snackbar if the server is busy. This time with a nifty status checker.
                showRemoteServerError("Login failed. Servers may be busy");
                return false;
            }
            return true;
        }

        @Override
        protected void onPreExecute() {
            // At the start of the login task, let's show the progress bar so users know something's
            // going on.
            setLoginProgressBarVisible(true);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            setLoginProgressBarVisible(false);

            // If login is successful, we will
            if (result) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(getString(R.string.username_preference_key), username);
                editor.putString(getString(R.string.password_preference_key), password);
                editor.apply();
            }
        }
    }
}
