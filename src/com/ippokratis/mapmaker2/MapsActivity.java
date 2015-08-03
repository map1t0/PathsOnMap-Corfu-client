package com.ippokratis.mapmaker2;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import com.ippokratis.mapmaker2.library.ConnectionDetector;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
//import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/************************************************************************
 * Η Activity για την οθόνη που εμφανίζει τον χάρτη πεζών της Κέρκυρας  *
 * **********************************************************************/
@SuppressLint("SetJavaScriptEnabled")
public class MapsActivity extends Activity {

    private static String chaniaMapURL = "http://corfu.pathsonmap.eu/ChaniaMap.html";
    private static String corfuMapURL = "http://corfu.pathsonmap.eu/CorfuMap.html";

    //Τα Id για τα googleAnalytics events
    private static String categoryMapsMenuID = "Maps Activity Menu";
    private static String actionMenuChoiseID = "Menu choise";
    private static String actionMakeReviewID="Make a review";
    private static String actionRankingID="Ranking";
    private static String actionBackToRecord = "Back To Record";

    private WebView browser;
    private String mapUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        int mapsID = intent.getExtras().getInt("mapsID");
        if(mapsID==1){
            setTitle(R.string.map_of_chania);
            mapUrl = chaniaMapURL;
        }
        else if(mapsID==2){
            setTitle(R.string.map_of_corfu);
            mapUrl = corfuMapURL;
        }

        setContentView(R.layout.activity_maps);

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        t.setScreenName("Maps screen");
        t.send(new HitBuilders.AppViewBuilder().build());

        browser = (WebView)findViewById(R.id.wvMaps);

        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        //Αν υπάρχει σύνδεδη internet θα δείξει τον χάρτη - αλλιώς θα βγάλει ένα μήνυμα στον χρήστη
        ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());

        if(mConnectionDetector.isNetworkConnected() == true &&  mConnectionDetector.isInternetAvailable()==true){

            setUpWebViewDefaults(browser);
            // Φορτώνει το website με τον χάρτη πεζών
            // Αναγκάζει το link να ανακατευθυνθεί και να ανοίξει σε WebView αντί για τον browser
            browser.setWebViewClient(new WebViewClient(){

                public void onPageFinished(WebView view, String url) {
                    //Δείχνει ένα μήνυμα μόλις η σελίδα φορτώσει- Εδώ δείχνει ότι τα μονοπάτια φορτώνουν αφού η σελίδα είναι ασύγχρονη
                    Toast.makeText(getApplicationContext(), "Τhe map is loading",
                            Toast.LENGTH_LONG).show();
                }});
            browser.loadUrl(mapUrl);

        }
        else {
            //Μήνυμα στον χρήστη ότι δεν υπάρχει σύνδεση internet
            Toast.makeText(getApplicationContext(), "You must be connected to the internet", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @SuppressLint("NewApi")
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setUpWebViewDefaults(WebView webView) {
        WebSettings settings = webView.getSettings();

        //Ενεργοποιεί την Javascript
        settings.setJavaScriptEnabled(true);

        //browser.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        browser.getSettings().setLoadsImagesAutomatically(true);

        // Επιτρέπει απομακρισμένο debugging μέσω του chrome
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.maps, menu);
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
                        .setCategory(categoryMapsMenuID)
                        .setAction(actionBackToRecord)
                        .build());

                finish();
                return true;

            case R.id.action_maps:

                Tracker t1 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t1.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryMapsMenuID)
                        .setAction(actionMenuChoiseID)
                        .build());

                return true;


            case R.id.submenu_review_paths:

                Tracker t3 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t3.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryMapsMenuID)
                        .setAction(actionMakeReviewID)
                        .build());

                Intent intent= new Intent(MapsActivity.this,ReviewPathActivity.class);
                startActivity(intent);
                finish();
                return true;
            case R.id.submenu_rank__list_of_players:

                Tracker t4 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t4.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryMapsMenuID)
                        .setAction(actionRankingID)
                        .build());

                Intent intent2= new Intent(MapsActivity.this,RankListOfPlayersActivity.class);
                startActivity(intent2);
                finish();
                return true;

        }

        return super.onOptionsItemSelected(item);
    }
}

