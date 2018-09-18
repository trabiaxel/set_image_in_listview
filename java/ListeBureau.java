package com.tech.ab.testapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.tech.ab.testapp.network.ApiService;
import com.tech.ab.testapp.network.ConnexionInternet;
import com.tech.ab.testapp.network.RetrofitBuilder;
import com.tech.ab.testapp.network.TokenManager;
import com.tech.ab.testapp.network.entities.AccessToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import butterknife.ButterKnife;
import retrofit2.Call;

public class ListeBureau extends AppCompatActivity {

    private String TAG = ListeBureau.class.getSimpleName();

    private ProgressDialog pDialog;
    private ListView listViewBureau;
    SearchView svBureau;
    URL url;

    private int recupIdVille, communeId, fenetreEtat, bureau_recup_id, nbreBureau;
    private String recupLibVille, recupLibCommune, logo_url;

    ArrayList<HashMap<String, String>> bureauList = new ArrayList<>();
    SimpleAdapter adapter;

    // Pour ce connecter on aura donc besoin de nos methodes
    ApiService service;
    TokenManager tokenManager;
    Call<AccessToken> call;

    static final int DIALOG_ERROR_CONNECTION = 1;
    private Handler myHandler;

    private Runnable myRunnable = new Runnable() {
        @Override
        public void run() {
            // Code à éxécuter de façon périodique
            // Permet de verifier l'etat de la connectivité du telephone à internet
            if(ConnexionInternet.isConnectedInternet(ListeBureau.this)) {
                //Toast.makeText(getApplicationContext(), "Il y a connexion à internet ", Toast.LENGTH_SHORT).show();
            } else {
                showDialog(DIALOG_ERROR_CONNECTION);
            }
            myHandler.postDelayed(this,500);
        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;

        switch (id) {
            case DIALOG_ERROR_CONNECTION :
                AlertDialog.Builder errorDialog = new AlertDialog.Builder(this);
                errorDialog.setTitle("Error");
                errorDialog.setMessage("No internet connection");
                //  errorDialog.setIcon(R.drawable.presence_busy);
                errorDialog.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            dialog.dismiss();
                            android.os.Process.killProcess(android.os.Process.myPid());
                        } catch (Exception e){
                            Toast.makeText(ListeBureau.this,"It seems something went wrong", Toast.LENGTH_SHORT).show();
                            Toast.makeText(ListeBureau.this,"Be sure to try again later", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                errorDialog.setPositiveButton("SETTING", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent myIntent = new Intent( Settings.ACTION_WIFI_SETTINGS);
                            startActivity(myIntent);
                        } catch (Exception e){
                            Toast.makeText(ListeBureau.this,"It seems something went wrong", Toast.LENGTH_SHORT).show();
                            Toast.makeText(ListeBureau.this,"Be sure to try again later", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                AlertDialog errorAlert = errorDialog.create();
                return errorAlert;

            default:
                break;
        }
        return dialog;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // On arrete le callback
        if(myHandler != null){
            myHandler.removeCallbacks(myRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // On arrete le callback
        if(myHandler != null){
            myHandler.removeCallbacks(myRunnable);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.liste_bureau);

        // Pour faire apparaitre les bouton retour en haut de l'activié
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            try {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("List of bureaux");
            } catch (Exception ignored){}
        }

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // on redemande toute les 500ms
        myHandler = new Handler();
        myHandler.postDelayed(myRunnable,500);
        ButterKnife.bind(this);

        StrictMode.ThreadPolicy threadPolicy = new
                StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(threadPolicy);

        Intent intent = getIntent();

        recupIdVille = intent.getIntExtra("recupIdVille", 1);
        recupLibVille = intent.getStringExtra("recupLibVille");

        recupLibCommune = intent.getStringExtra("recupLibCommune");
        communeId = intent.getIntExtra("communeId", 1);

        fenetreEtat = intent.getIntExtra("fenetreEtat", 0);

        nbreBureau = intent.getIntExtra("nbreBureau", 20);

        final String url = "https://www.dev.com/api/bureau/search?commune_id=" + communeId + "&per_page="+ nbreBureau;

        // Ce code permet a l'utilisateur de faire une recherche sur les bureaux
        svBureau = findViewById(R.id.searchBureau);
        svBureau.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {

                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                try {
                    adapter.getFilter().filter(newText);
                } catch (Exception ignored){}
                return false;
            }
        });

        listViewBureau = findViewById(R.id.Liste_Bureau);
        TextView emptyText = (TextView)findViewById(R.id.empty_listburo);
        listViewBureau.setEmptyView(emptyText);

        listViewBureau.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int i, long l) {
                try {
                    //Toast.makeText(ListeBureau.this, bureauList.get(i).get("id").toString(), Toast.LENGTH_SHORT).show();
                    bureau_recup_id = Integer.parseInt(bureauList.get(i).get("id"));

                    Intent intent;
                    intent = new Intent(ListeBureau.this, DetailBureau.class);
                    intent.putExtra("logo_url", logo_url);

                    intent.putExtra("bureau_id", bureau_recup_id);

                    intent.putExtra("recupIdVille", recupIdVille);
                    intent.putExtra("recupLibVille", recupLibVille);

                    intent.putExtra("communeId",communeId);
                    intent.putExtra("recupLibCommune", recupLibCommune);

                    intent.putExtra("fenetreEtat",0);
                    startActivity(intent);
                    finish();
                } catch (Exception e){
                    Toast.makeText(ListeBureau.this,"It seems something went wrong", Toast.LENGTH_SHORT).show();
                    Toast.makeText(ListeBureau.this,"Be sure to try again later", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Initialisation des methode de connexions
        service = RetrofitBuilder.createService(ApiService.class);
        tokenManager = TokenManager.getInstance(getSharedPreferences("prefs", MODE_PRIVATE));

        new GetBureau().execute(url);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        try {
            Intent intent;
            intent = new Intent(ListeBureau.this, RechercheBureau.class);

            intent.putExtra("recupIdVille", recupIdVille);
            intent.putExtra("recupLibVille", recupLibVille);

            intent.putExtra("communeId",communeId);
            intent.putExtra("recupLibCommune", recupLibCommune);

            intent.putExtra("fenetreEtat",0);
            startActivity(intent);
            finish();
        } catch (Exception e){
            Toast.makeText(ListeBureau.this,"It seems something went wrong", Toast.LENGTH_SHORT).show();
            Toast.makeText(ListeBureau.this,"Be sure to try again later", Toast.LENGTH_SHORT).show();
        }
    }

    // Permet de mettre le code de back pressed sur le bouton de retour en haut de l'activité
    @Override
    public boolean onSupportNavigateUp(){
        try {
            Intent intent;
            intent = new Intent(ListeBureau.this, RechercheBureau.class);

            intent.putExtra("recupIdVille", recupIdVille);
            intent.putExtra("recupLibVille", recupLibVille);

            intent.putExtra("communeId",communeId);
            intent.putExtra("recupLibCommune", recupLibCommune);

            intent.putExtra("fenetreEtat",0);
            startActivity(intent);
            finish();
        } catch (Exception e){
            Toast.makeText(ListeBureau.this,"It seems something went wrong", Toast.LENGTH_SHORT).show();
            Toast.makeText(ListeBureau.this,"Be sure to try again later", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private class GetBureau extends AsyncTask<String, Void, String> {

        Bitmap image;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Showing progress dialog
            pDialog = new ProgressDialog(ListeBureau.this);
            pDialog.setMessage("Please wait...");
            pDialog.setCancelable(false);
            pDialog.setIndeterminate(false);
            pDialog.show();
        }

        @Override
        protected String doInBackground(String... strings) {

            StringBuilder result = new StringBuilder();

            try {

                HttpURLConnection conn;

                URL url = new URL(strings[0]);
                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setDoInput(true);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");

                conn.connect();

                InputStream in = new BufferedInputStream(conn.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                conn.disconnect();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            return result.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            // Dismiss the progress dialog
            if (pDialog.isShowing())
                pDialog.dismiss();

            /**
             * Updating parsed JSON data into ListView
             * */
            JSONObject jsonArrayBureau = null;
            JSONArray data = null;

            try {
                jsonArrayBureau = new JSONObject(result.toString());
                data = jsonArrayBureau.getJSONArray("data");

                // looping through All bureau
                for (int i = 0; i < data.length(); i++) {
                    JSONObject bureaux = data.getJSONObject(i);

                    String logo_url = bureaux.getString("logo_url");

                    String id = bureaux.getString("id");
                    String denomination = bureaux.getString("denomination");
                    String adresse = bureaux.getString("adresse");
                    String mobile = bureaux.getString("mobile");
                    if (mobile == "null") {
                        mobile = "";
                    }
                    String daily_visit = bureaux.getString("daily_visit");

                    // tmp hash map for single bureau
                    HashMap<String, String> bureau = new HashMap<>();

                    // adding each child node to HashMap key => value
                    bureau.put("logo_url", logo_url);
             
                    bureau.put("id", id);
                    bureau.put("denomination", denomination);
                    bureau.put("adresse", adresse);
                    bureau.put("mobile", mobile);
                    bureau.put("daily_visit", daily_visit);

                    // adding contact to abonnement list
                    bureauList.add(bureau);
                }
            }catch (final JSONException e) {
                Log.e(TAG, "Json parsing error: " + e.getMessage());
            }catch (NullPointerException e){
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String[] from = {"denomination", "mobile", "adresse", "daily_visit", "logo_url"};
            int[] to = {R.id.bureau_denomination, R.id.bureau_mobile, R.id.bureau_adresse, R.id.bureau_visit, R.id.image_bureau_list};

            adapter = new SimpleAdapter(ListeBureau.this, bureauList, R.layout.adapter_list_bureau, from, to);

            listViewBureau.setAdapter(adapter);
        }
    }
}