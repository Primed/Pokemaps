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

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import com.genesys.pokemaps.R;
import com.genesys.pokemaps.Utils;
import com.google.android.gms.maps.model.LatLng;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.api.gym.Gym;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.fort.PokestopLootResult;
import com.pokegoapi.api.map.pokemon.CatchResult;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.pokegoapi.api.map.pokemon.EvolutionResult;
import com.pokegoapi.api.map.pokemon.NearbyPokemon;
import com.pokegoapi.api.map.pokemon.encounter.EncounterResult;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.api.pokemon.PokemonMetaRegistry;
import com.pokegoapi.api.settings.CatchOptions;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.NoSuchItemException;
import com.pokegoapi.exceptions.RemoteServerException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import POGOProtos.Enums.PokemonIdOuterClass;
import POGOProtos.Inventory.Item.ItemIdOuterClass;
import POGOProtos.Networking.Responses.CatchPokemonResponseOuterClass;
import POGOProtos.Networking.Responses.ReleasePokemonResponseOuterClass;
import okhttp3.OkHttpClient;

/**
 * Hand crafted by Primed with love for the Pokemaps project.
 * Created on 8/19/16 at 3:12 AM
 * https://github.com/Primed/Pokemaps
 */

public class GameManager {

    private static final String TAG = "GameManager";
    /**
     * The current instance.
     */
    private static GameManager instance;
    /**
     * The login handler.
     */
    private OkHttpClient client;
    /**
     * Our Pokemon GO object. This is what will be doing most of the work.
     */
    private PokemonGo go;
    /**
     * Our preferences. Self-explanatory.
     */
    private SharedPreferences preferences;
    /**
     * Our LoginListener instance. If this field is not null, loginListener.onLoginCompleted() will
     * be called upon login completion.
     */
    private LoginListener loginListener;
    /**
     * List of discovered pokestops.
     */
    private List<Pokestop> pokestops;
    /**
     * List of discovered catchable pokemon.
     */
    private List<CatchablePokemon> catchablePokemon;
    /**
     * Our options for catching pokemon intelligently.
     */
    private CatchOptions options;
    /**
     * List of discovered gyms.
     */
    private List<Gym> gyms;

    /**
     * Creates a new GameManager object and sets up the various Pokemon GO components.
     */
    private GameManager(Context context) {
        // Build our login manager and set a login timeout of 10 seconds.
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();

        // Get our preferences from the Activity context.
        preferences = context.getSharedPreferences(context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE);

        // Initialize our arrays.
        pokestops = new ArrayList<>();
        catchablePokemon = new ArrayList<>();
        gyms = new ArrayList<>();
    }

    /**
     * Only allow access to this class from this getInstance method. This way there's always
     * ony one instance of GameManager at a time. One instance can be shared between multiple
     * threads at a time.
     *
     * @return GameManager instance.
     */
    public static GameManager getInstance(Context context) {
        if (instance == null) {
            instance = new GameManager(context);
        }
        return instance;
    }

    /**
     * Logs into the Pokemon GO servers using Pokemon Trainer Club credentials.
     *
     * @param username PTC username.
     * @param password PTC password.
     */
    public void loginPTC(final String username, final String password) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LoginResult result = new LoginResult();
                try {
                    go = new PokemonGo(new PtcCredentialProvider(client, username, password), client);
                    if (go.getAuthInfo().isInitialized()) {
                        // Success!
                        result.message("Login successful")
                                .result(Result.SUCCESS);

                        // If username and password haven't already been recorded into the
                        // preferences, record them now.
                        if (!preferences.contains("4A1MtZE8p1")
                                && !preferences.contains("8M5fzdR27u")) {
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("4A1MtZE8p1", username);
                            editor.putString("8M5fzdR27u", password);
                            editor.apply();
                        }

                        // Notify our login listener of completion if it exists.
                        if (loginListener != null) {
                            loginListener.onLoginCompleted(result);
                        }
                    }
                } catch (LoginFailedException e) {
                    // Invalid credentials
                    result.message("Invalid username or password")
                            .result(Result.INVALID_CREDENTIALS);
                } catch (RemoteServerException e) {
                    // Server busy... probably
                    result.message("Servers are busy. Please try again later")
                            .result(Result.SERVER_BUSY);
                }
            }
        }).start();
    }

    /* Instance methods */

    /**
     * Sets our location in Pokemon GO.
     *
     * @param location The location to update to.
     */
    public void setPlayerLocation(final Location location) {
        if (go != null) {
            go.setLocation(location.getLatitude(),
                    location.getLongitude(),
                    location.getAltitude());
        }
    }

    /**
     * Sets our location in Pokemon GO.
     *
     * @param location The location to update to.
     */
    public void setPlayerLocation(final LatLng location) {
        if (go != null) {
            go.setLocation(location.latitude,
                    location.longitude,
                    0.336792f);
        }
    }

    /**
     * Adds nearby discovered pokestops to our array if they aren't already contained in it.
     *
     * @return Our list of updated pokestops.
     * @throws LoginFailedException  If login username and password are incorrect.
     * @throws RemoteServerException If Pokemon GO's servers are down.
     */
    public List<Pokestop> updatePokestops() throws
            LoginFailedException,
            RemoteServerException {
        if (go != null) {
            for (Pokestop pokestop : go.getMap().getMapObjects().getPokestops()) {
                if (pokestops != null && !pokestops.contains(pokestop)) {
                    Log.i(TAG, "New Pokestop found at " + pokestop.getLatitude()
                            + ", " + pokestop.getLongitude());
                    pokestops.add(pokestop);
                }
            }
        }
        return pokestops;
    }

    /**
     * Loots the nearby pokestops.
     *
     * @return The result of the Pokestop loot.
     * @throws LoginFailedException  If login username and password are incorrect.
     * @throws RemoteServerException If Pokemon GO's servers are down.
     */
    public List<PokestopLootResult> lootPokestops() throws
            LoginFailedException,
            RemoteServerException {
        List<PokestopLootResult> lootResults = new ArrayList<>();
        if (go != null) {
            for (Pokestop pokestop : go.getMap().getMapObjects().getPokestops()) {
                if (pokestop.inRange() && pokestop.canLoot()) {
                    lootResults.add(pokestop.loot());
                }
            }
        }
        return lootResults;
    }

    /**
     * Gets all the nearby pokemon. (Catchable and un-catchable).
     *
     * @return List of all nearby pokemon.
     * @throws LoginFailedException  If login username and password are incorrect.
     * @throws RemoteServerException If Pokemon GO's servers are down.
     */
    public List<NearbyPokemon> getNearbyPokemon() throws
            LoginFailedException,
            RemoteServerException {
        List<NearbyPokemon> nearbyPokemon = new ArrayList<>();
        if (go != null) {
            nearbyPokemon = go.getMap().getNearbyPokemon();
        }
        return nearbyPokemon;
    }

    /**
     * Adds nearby discovered pokemon to our array if they aren't already contained in it.
     *
     * @return Our list of updated pokemon.
     * @throws LoginFailedException  If login username and password are incorrect.
     * @throws RemoteServerException If Pokemon GO's servers are down.
     */
    public List<CatchablePokemon> updateCatchablePokemon() throws
            LoginFailedException,
            RemoteServerException {
        if (go != null) {
            for (CatchablePokemon pokemon : go.getMap().getCatchablePokemon()) {
                if (catchablePokemon != null && !catchablePokemon.contains(pokemon)) {
                    Log.i(TAG, pokemon.getPokemonId().name() + " found at " + pokemon.getLatitude()
                            + ", " + pokemon.getLongitude());
                    catchablePokemon.add(pokemon);
                }
            }
        }
        return catchablePokemon;
    }

    /**
     * Catches a single pokemon at our location.
     *
     * @return The result of the pokemon capture.
     * @throws LoginFailedException  If login username and password are incorrect.
     * @throws RemoteServerException If Pokemon GO's servers are down.
     * @throws NoSuchItemException   If we have no pokeballs and/or razz berries.
     */
    public Catch catchPokemon() throws
            LoginFailedException,
            RemoteServerException,
            NoSuchItemException, InterruptedException {
        if (go != null) {
            // Set up our options for smart pokemon capture.
            options = new CatchOptions(go)
                    .noMasterBall(true)
                    .useSmartSelect(true)
                    .useBestBall(true)
                    .useRazzberries(go.getInventories().getItemBag()
                            .getItem(ItemIdOuterClass.ItemId.ITEM_RAZZ_BERRY).getCount() > 0)
                    .maxPokeballs(-1);

            for (CatchablePokemon pokemon : go.getMap().getCatchablePokemon()) {
                EncounterResult encounterResult = pokemon.encounterPokemon();
                if (encounterResult.wasSuccessful()) {
                    Log.i(TAG, Utils.getPokemonName(pokemon.getPokemonIdValue()) + " encountered.");
                    // Catch the pokemon. The first parameter is the encounter result. The second
                    // is a list of pokeballs you wish to exclude from the capture process. In our
                    // case, we don't want to exclude any, so we pass null. The third parameter is
                    // the max amount of tries to catch the pokemon. We use -1 for infinite tries.
                    // TODO: Create a setting that changes max try count and max razz berry count.
                    // And finally, the fourth parameter is the max amount of razz berries to use
                    // when capturing the pokemon.
                    Thread.sleep(2000);

                    int maxRazzBerries;

                    // Set number of razz berries independent of pokemon rarity.
                    switch (PokemonMetaRegistry.getMeta(pokemon.getPokemonId()).getPokemonClass()) {
                        case VERY_COMMON:
                            maxRazzBerries = 2;
                            break;
                        case COMMON:
                            maxRazzBerries = 5;
                            break;
                        case UNCOMMON:
                            maxRazzBerries = 7;
                            break;
                        case RARE:
                            maxRazzBerries = 9;
                            break;
                        default:
                            maxRazzBerries = -1;
                            break;
                    }

                    // If pokemon has not yet been caught, we'll go all out.
                    if (go.getInventories().getPokedex().getPokedexEntry(pokemon.getPokemonId()) == null) {
                        maxRazzBerries = -1;
                    }

                    options = options.maxPokeballs(maxRazzBerries);

                    CatchResult catchResult = pokemon.catchPokemon(options);
                    if (catchResult.getStatus()
                            == CatchPokemonResponseOuterClass.CatchPokemonResponse.CatchStatus.CATCH_SUCCESS) {
                        catchablePokemon.remove(pokemon);
                    }
                    return new Catch(pokemon, catchResult);
                }
            }
        }
        return null;
    }

    /**
     * Adds nearby discovered pokemon to our array if they aren't already contained in it.
     *
     * @return Our list of updated pokemon.
     * @throws LoginFailedException  If login username and password are incorrect.
     * @throws RemoteServerException If Pokemon GO's servers are down.
     */
    public List<Gym> updateGyms() throws
            LoginFailedException,
            RemoteServerException {
        if (go != null) {
            for (Gym gym : gyms) {
                if (gyms != null && !gyms.contains(gym)) {
                    Log.i(TAG, "New gym found at " + gym.getLatitude() + ", " + gym.getLongitude());
                    gyms.add(gym);
                }
            }
        }
        return gyms;
    }

    /**
     * A useful little script that
     */
    public void farmXP() {
        if (go != null) {
            try {
                for (PokemonIdOuterClass.PokemonId pokemonId : new PokemonIdOuterClass.PokemonId[]{
                        PokemonIdOuterClass.PokemonId.PIDGEY,
                        PokemonIdOuterClass.PokemonId.WEEDLE,
                        PokemonIdOuterClass.PokemonId.CATERPIE,
                        PokemonIdOuterClass.PokemonId.RATTATA,
                        PokemonIdOuterClass.PokemonId.SPEAROW,
                        PokemonIdOuterClass.PokemonId.ZUBAT
                }) {
                    for (Pokemon pokemon : go.getInventories().getPokebank()
                            .getPokemonByPokemonId(pokemonId)) {
                        evolveOrTransfer(pokemon);
                    }
                }
            } catch (LoginFailedException | RemoteServerException | InterruptedException e) {
                e.printStackTrace();
            }
            Log.i(TAG, "Finished farming XP.");
        }
    }

    private void evolveOrTransfer(Pokemon pokemon) throws
            LoginFailedException,
            RemoteServerException,
            InterruptedException {
        if (go != null) {
            if (pokemon.canEvolve()) {
                EvolutionResult result = pokemon.evolve();
                if (result.isSuccessful()) {
                    Log.i(TAG, Utils.getPokemonName(pokemon) + " evolved.");
                    ReleasePokemonResponseOuterClass.ReleasePokemonResponse.Result releaseResult
                            = result.getEvolvedPokemon().transferPokemon();
                    if (releaseResult
                            == ReleasePokemonResponseOuterClass.ReleasePokemonResponse.Result.SUCCESS) {
                        Log.i(TAG, "Evolved " + Utils.getPokemonName(result.getEvolvedPokemon())
                                + " successfully transferred.");
                    } else {
                        Log.i(TAG, "An error occurred while transferring evolved pokemon.");
                    }
                } else {
                    Log.i(TAG, Utils.getPokemonName(pokemon) + " faced an error while evolving.");
                }
            } else {
                ReleasePokemonResponseOuterClass.ReleasePokemonResponse.Result releaseResult
                        = pokemon.transferPokemon();
                if (releaseResult
                        == ReleasePokemonResponseOuterClass.ReleasePokemonResponse.Result.SUCCESS) {
                    Log.i(TAG, Utils.getPokemonName(pokemon) + " can't evolve. Pokemon transferred " +
                            "instead.");
                } else {
                    Log.i(TAG, "An error occurred while transferring " + Utils.getPokemonName(pokemon));
                }
            }
        }
        Thread.sleep(1000);
    }

    /**
     * Sets the login completed listener.
     *
     * @param loginListener The desired listener to set.
     */
    public void setOnLoginCompletedListener(LoginListener loginListener) {
        this.loginListener = loginListener;
    }

    /**
     * Possible login status results.
     */
    public enum Result {
        SUCCESS,
        INVALID_CREDENTIALS,
        SERVER_BUSY
    }

    /**
     * This class provides various login state indicators for implementation.
     */
    public interface LoginListener {

        /**
         * As the method name implies, this method runs when Pokemon GO login is completed.
         */
        void onLoginCompleted(LoginResult loginResult);
    }

    /**
     * Provides information on the result of the login.
     */
    public class LoginResult {

        private Result result;

        private String message;

        LoginResult result(Result result) {
            this.result = result;
            return this;
        }

        LoginResult message(String message) {
            this.message = message;
            return this;
        }

        public Result getResult() {
            return result;
        }

        public String getMessage() {
            return message;
        }
    }

    public class Catch {

        private CatchResult catchResult;

        private CatchablePokemon catchablePokemon;

        Catch(CatchablePokemon catchablePokemon, CatchResult catchResult) {
            this.catchablePokemon = catchablePokemon;
            this.catchResult = catchResult;
        }

        public CatchablePokemon getCatchablePokemon() {
            return catchablePokemon;
        }

        public CatchResult getCatchResult() {
            return catchResult;
        }
    }
}
