package com.ippokratis.mapmaker2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import com.ippokratis.mapmaker2.library.ConnectionDetector;
import com.ippokratis.mapmaker2.library.JSONParser;


import android.app.ListActivity;
import android.app.ProgressDialog;
//import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListAdapter;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/******************************************************************
 * Η Activity για την οθόνη που δείχνει την κατάταξη των παικτών  *
 * ****************************************************************/
public class RankListOfPlayersActivity extends ListActivity {

    //Το url από το οποίο παίρνουμε την κατάταξη των παικτών
    private static String url_all_players_in_rank = "http://corfu.pathsonmap.eu/getRankOfPlayers.php";
    //private static String url_all_players_in_rank = "http://192.168.1.65/mapmaker_local/getRankOfPlayers.php";

    //Τα Id για τα googleAnalytics events
    private static String categoryRankingMenuID = "Ranking Activity Menu";
    private static String actionMenuChoiseID = "Menu choise";
    private static String actionShowMapsID = "Show Maps";
    //private static String labelChaniaID ="Chania";
    //private static String labelCorfuID ="Corfu";
    private static String actionMakeReviewID="Make a review";
    //private static String actionAllPathsID = "All Paths";
    private static String actionBackToRecord = "Back To Record";

    // Διάλογος προόδου
    private ProgressDialog pDialog;

    // Δημιουργία του JSON Parser αντικειμένου
    JSONParser jParser = new JSONParser();

    ArrayList<HashMap<String, String>> playersRankList;

    // Τα ονόματα των κόμβων (nodes) JSON
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_NAME = "name";
    private static final String TAG_POINTS = "total_points";
    private static final String TAG_RANK_OF_PLAYERS = "getRankOfPlayers";
    private static final String TAG_RANKING_OF_PLAYER = "rankingOfPlayer";

    // JSONArray των παικτών
    JSONArray players = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.submenu_rank__list_of_players);
        setContentView(R.layout.activity_rank_list_of_players);

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        t.setScreenName("Ranking screen");
        t.send(new HitBuilders.AppViewBuilder().build());

        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        // Hashmap για την ListView
        playersRankList = new ArrayList<HashMap<String, String>>();

        //Αν υπάρχει σύνδεδη internet θα γίνει προσπάθεια εμφάνισης των παικτών - αλλιώς θα βγάλει ένα μήνυμα στον χρήστη
        ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());

        if(mConnectionDetector.isNetworkConnected() == true &&  mConnectionDetector.isInternetAvailable()==true){

            // Φορτώνει τους παίκτες σε Background Thread
            new LoadAllPlayersRank().execute();

        }
        else{
            //Μήνυμα στον χρήστη ότι δεν υπάρχει σύνδεση internet
            Toast.makeText(getApplicationContext(), R.string.internet_connection_required, Toast.LENGTH_LONG).show();
            finish();
        }

        // Παίρνει την listview
        getListView();
    }

    /**
     * Background Async Task για να φορώσει όλους τους παίκτες κάνοντας ένα αίτημα HTTP
     * */
    class LoadAllPlayersRank extends AsyncTask<String, String, String> {

        /**
         * Πριν αρχίσει το background thread δείξε ένα διάλογο προόδου
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(RankListOfPlayersActivity.this);
            pDialog.setMessage("Loading players. Please wait...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
        }

        /**
         * παίρνει όλους τους παίκτες από το url
         * */
        protected String doInBackground(String... args) {
            // Χτίζει τις παραμέτρους
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("tag", TAG_RANK_OF_PLAYERS));

            // παίρνει το JSON string απ΄π το URL
            JSONObject json = jParser.getJSONFromUrl(url_all_players_in_rank, params);

            // Γράφει στο log cat την JSON απόκριση
            Log.d("All Players: ", json.toString());

            try {
                // Ελέγχει το SUCCESS TAG
                int success = json.getInt(TAG_SUCCESS);

                if (success == 1) {
                    // βρέθηκαν παίκτες
                    // Παίρνει τον πίνακα (Array) όλων των παικτών
                    players = json.getJSONArray(TAG_PLAYERS);

                    // looping μέσω όλων των παικτών
                    for (int i = 0; i < players.length(); i++) {
                        JSONObject c = players.getJSONObject(i);

                        // Αποθηκεύει κάθε στοιχείο json σε μεταβλητή
                        String name = c.getString(TAG_NAME);
                        String points = c.getString(TAG_POINTS);
                        points = getResources().getString(R.string.total_strings_message) + " " + points;

                        //Δημιουργεί ένα καινούργιο HashMap
                        HashMap<String, String> map = new HashMap<String, String>();

                        // προσθέτει κάθε παιδί του κόμβου (child node) στο HashMap κλειδή => τιμή (key => value)
                        map.put(TAG_NAME, name);
                        map.put(TAG_POINTS, points);
                        map.put(TAG_RANKING_OF_PLAYER, "#" + String.valueOf(i + 1));

                        // Προσθέτει το HashList στο ArrayList
                        playersRankList.add(map);
                    }
                }
                else {
                    // Δεν βρέθηκαν παίκτες
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.error_message_in_rank_of_players),
                            Toast.LENGTH_LONG).show();

                    finish();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        /**
         * Μετά την ολοκλήρωση του background task κλείνει τον διάλογο προόδου
         * **/
        protected void onPostExecute(String file_url) {
            // Κλείνει τον διάλογο, μετά που παίρνει όλους τους παίκτες
            pDialog.dismiss();
            // ενημερώνει το UI από το Background Thread
            runOnUiThread(new Runnable() {
                public void run() {
                    /**
                     * Ενημερώνει τα parsed JSON δεδομένα μέσα στην ListView
                     * */
                    ListAdapter adapter = new SimpleAdapter(
                            RankListOfPlayersActivity.this, playersRankList,
                            R.layout.list_item, new String[] { TAG_NAME,
                            TAG_RANKING_OF_PLAYER, TAG_POINTS},
                            new int[] { R.id.name, R.id.rankingOfPlayer, R.id.total_points });
                    // ενημερώνει την listview
                    setListAdapter(adapter);
                }
            });

        }

    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        GoogleAnalytics.getInstance(RankListOfPlayersActivity.this).reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        GoogleAnalytics.getInstance(RankListOfPlayersActivity.this).reportActivityStop(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Διόγκωση (Inflate) του μενού. Προσθέτει στοιχεία στην γραμμή ενεργειών (action bar) αν υπάρχει.
        getMenuInflater().inflate(R.menu.rank_list_of_players, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Εδώ γίνεται ο χειρισμός των action bar item κλικ. Η action bar θα χειριστεί
        // αυτόματα τα κλικ του Home/Up button,
        // αν έχει καθοριστεί μια parent activity στο AndroidManifest.xml.
		/*int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}*/

        switch (item.getItemId()) {
            // Απόκριση του action bar's Up/Home κουμπιού
            case android.R.id.home:

                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRankingMenuID)
                        .setAction(actionBackToRecord)
                        .build());

                finish();
                return true;
            case R.id.action_menu_rank_list_of_players:

                Tracker t1 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t1.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRankingMenuID)
                        .setAction(actionMenuChoiseID)
                        .build());

                return true;
            case R.id.submenu_show_maps:

                Tracker t2 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t2.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRankingMenuID)
                        .setAction(actionShowMapsID)
                        .build());

                Intent mapsIntent= new Intent(RankListOfPlayersActivity.this,MapsActivity.class);
                mapsIntent.putExtra("mapsID",2);
                startActivity(mapsIntent);
                finish();

                return true;
			

            case R.id.submenu_review_paths:

                Tracker t3 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t3.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRankingMenuID)
                        .setAction(actionMakeReviewID)
                        .build());

                Intent intent= new Intent(RankListOfPlayersActivity.this,ReviewPathActivity.class);
                startActivity(intent);
                finish();
                return true;

        }

        return super.onOptionsItemSelected(item);
    }
}

