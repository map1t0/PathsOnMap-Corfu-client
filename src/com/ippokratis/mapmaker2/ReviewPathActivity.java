package com.ippokratis.mapmaker2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import com.ippokratis.mapmaker2.library.ConnectionDetector;
import com.ippokratis.mapmaker2.library.UserFunctions;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
//import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

/*******************************************************************************************
 * Η Activity στην οποία οι χρήστες μπορούν να σχολιάσουν τις διαδρομές των άλλων χρηστών  *
 * *****************************************************************************************/
public class ReviewPathActivity extends Activity {

    private static String request_path_url = "http://corfu.pathsonmap.eu/requestPath.php";
    private static String show_no_path_url = "http://corfu.pathsonmap.eu/noPath.php?path=";
    private static String show_path_url = "http://corfu.pathsonmap.eu/showGpxFileInMap.php?path=";

    //Αν ο server έχει στηθεί τοπικά
    //private static String request_path_url = "http://192.168.1.65/mapmaker_local/requestPath.php";
    //private static String show_no_path_url = "http://192.168.1.65/mapmaker_local/noPath.php?path=";
    //private static String show_path_url = "http://192.168.1.65/mapmaker_local/showGpxFileInMap.php?path=";

    //Τα Id για τα googleAnalytics events
    private static String categoryReviewPathId = "ReviewPathActivity buttons";
    private static String actionDiscardNewPathId = "Discard_NewPath button";
    private static String actionSubmitId = "Submit button";
    private static String categoryMakeReviewID = "Make a Review Menu";
    private static String actionMenuChoiseID = "Menu choise";
    private static String actionShowMapsID = "Show Maps";
    private static String actionRankingID="Ranking";
    private static String actionBackToRecord = "Back To Record";

    private int path_id;//Το uid του μονοπατιού
    private WebView browser;//Σε αυτό το WebView θα φαίνεται το μονοπάτι που θα γίνει review (ή ότι δεν υπάρχει μονοπάτι για review)
    private Button btnSubmit;//Το κουμπί που ο παίκτης θα υποβάλει το review του
    private Button btnDiscard;//Το κουμπί για να μπορεί ο παίκτης να απορρίψει το μονοπάτι
    private ProgressDialog pDialog;//Για να δείξει στον χρήστη ότι αιτείται ένα μονοπάτι
    private int user_id;//Το uid του παίκτη
    private Spinner spinnerReview;//Για επιλογή κριτικής μονοπατιού
    private Spinner spinnerReviewTags;//Για επιλογή κριτικής μονοπατιού

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.submenu_review_paths);
        setContentView(R.layout.activity_review_path);

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        t.setScreenName("Make a review screen");
        t.send(new HitBuilders.AppViewBuilder().build());

        spinnerReview = (Spinner)findViewById(R.id.spinnerReview);
        spinnerReviewTags = (Spinner)findViewById(R.id.spinnerReview2);
        browser = (WebView)findViewById(R.id.webView1);
        btnSubmit = (Button)findViewById(R.id.btnSubmit);
        btnDiscard = (Button)findViewById(R.id.btnDiscard);

        spinnerReview.setSelection(4);
        spinnerReviewTags.setSelection(4);

        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        //Αν υπάρχει σύνδεδη internet θα γίνει προσπάθεια φόρτωσης ενός μονοπατιού - αλλιώς θα βγάλει ένα μήνυμα στον χρήστη
        ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());

        if(mConnectionDetector.isNetworkConnected() == true &&  mConnectionDetector.isInternetAvailable()==true){

            UserFunctions userFunctions = new UserFunctions();
            user_id = userFunctions.getUserUid(getApplicationContext());//Γυρίζει το uid του χρήστη που είναι αποθηκευμένο στην sqlite database του κινητού
            new RequestRandomPath(user_id).execute();

        }
        else
        {	//Μήνυμα στον χρήστη ότι δεν υπάρχει σύνδεση internet
            Toast.makeText(getApplicationContext(), "You must be connected to the internet", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * Background Async Task για να πάρει ένα τυχαίο μονοπάτι(την διαδρομή ουσιαστικά του gpx αρχείου του στον server)
     * */
    class RequestRandomPath extends AsyncTask<String, String, String> {

        private int mplayerID;
        private String path_request = "pathRequest";//αυτή την τιμή θα πάρει η ετικέτα tag στο http έτοιμα για path
        JSONObject jObj = null;//Ένα json αντικείμενο
        String data;
        String json = "";

        // όνομα κόμβου(node) JSON απόκρισης
        String KEY_SUCCESS = "success";
        String KEY_MESSAGE = "message";
        String KEY_ERROR = "error";
        String KEY_ERROR_MESSAGE = "error_msg";
        String PATH = "path";//Η διαδρομή του gpx αρχείου
        String PATH_ID= "path_id";//Το uid της διαδρομής που γυρίζει

        public RequestRandomPath(int playerID){

            mplayerID=playerID;//Το id του χρήστη που αιτείται το μονοπάτι

        }

        /**
         * Πριν αρχίσει το background thread δείξε ένα διάλογο προόδου
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(ReviewPathActivity.this);
            pDialog.setMessage("Request path...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
        }

        //Γυρίζει το path (την διαδρομή) του gpx αρχείου του μονοπατιού-Αν υπάρχει σφάλμα το path παίρνει το μήνυμα σφάλματος
        @Override
        protected String doInBackground(String... params) {
            InputStream is = null;//Διαβάζει δεδομένα από το δίκτυο
            String path ="";//Εδώ θα περιέχεται η διαδρομή του gpx αρχείου στον server

            try{

                DefaultHttpClient httpClient = new DefaultHttpClient();//Προεπιλεγμένη υλοποίηση ενός πελάτη HTTP
                HttpPost httpPost = new HttpPost(request_path_url);//Η μέθοδος HTTP POST
                List<NameValuePair> parames = new ArrayList<NameValuePair>();
                parames.add(new BasicNameValuePair("tag", path_request));//To tag στο http αίτημα θα είναι path_request
                parames.add(new BasicNameValuePair("playerID", String.valueOf(mplayerID)));//To playerID στο http αίτημα θα πάρει το uid του παίκτη

                //UrlEncodedFormEntity είναι μια οντότητα που αποτελείται από μια λίστα url-κωδικοποιημένα ζεύγη. Η setEntity χειρίζεται την οντότητα στην αίτηση
                UrlEncodedFormEntity form = new UrlEncodedFormEntity(parames,"UTF-8");
                form.setContentEncoding(HTTP.UTF_8);
                httpPost.setEntity(form);//Το utf-8 το βάλαμε ώστε να υποστηρίζει ελληνικά
                HttpResponse httpResponse = httpClient.execute(httpPost);//Εκτελεί την αίτηση και γυρίζει την απάντηση
                HttpEntity httpEntity = httpResponse.getEntity();//Λαμβάνει το μήνυμα οντότητας αυτής της απάντησης, αν υπάρχει
                is = httpEntity.getContent();//Δημιουργεί ένα νέο InputStream αντικείμενο της οντότητας.
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                //Καλύπτει(wrap) έναν υπάρχον reader και κάνει buffer την είσοδο
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        is, "utf-8"), 8);//InputStreamReader είναι μια κλάση που μετατρέπει ένα ρεύμα bytes σε ένα ρεύμα χαρακτήρων. Εδώ χρησιμοποιείται η κωδικοποίηση utf-8
                StringBuilder sb = new StringBuilder();//Μία τροποποιήσιμη ακολουθία χαρακτήρων για χρήση στη δημιουργία αλφαριθμητικών
                String line = null;
                while ((line = reader.readLine()) != null) {//Διαβάζει μια γραμμή κειμένου
                    sb.append(line + "\n");//Προσθέτει την συμβολοσειρά
                }
                is.close();
                json = sb.toString();//Επιστρέφει τα περιεχόμενα αυτού του builder (του sb εδώ)-Τα περιεχόμανα θα είναι ένα json αντικείμενο
                Log.e("JSON", json);
            } catch (Exception e) {
                Log.e("Buffer Error", "Error converting result " + e.toString());
            }

            // Προσπαθεί να αναλύσει την συμβολοσειρά σε ένα αντκείμνο JSON
            try {
                jObj = new JSONObject(json);//Δημιουργεί ένα νέο JSONObject με τις αντιστοιχίσεις όνοματος / τιμής από τη συμβολοσειρά JSON.
            }
            catch (JSONException e) {
                Log.e("JSON Parser", "Error parsing data " + e.toString());
            }

            //Αναλύει το json αντικείμενο και βάζει στο path την διαδρομή του gpx αρχείου και στο path_id το uid του μονοπατιού-Αν δεν υπάρχει μονοπάτι τότε το θεωρεί 0
            try {

                if (jObj.getString(KEY_SUCCESS) != null) {

                    String result = jObj.getString(KEY_SUCCESS);

                    if (Integer.parseInt(result) == 1){//Αν η αίτηση ήταν επιτυχής
                        JSONObject json_path =jObj.getJSONObject("path");
                        path = json_path.getString(PATH);//Η διαδρομή του gpx στον server
                        path_id= json_path.getInt(PATH_ID);//To uid της διαδρομής
                    }
                    else{
                        path = jObj.getString(KEY_ERROR_MESSAGE);//Αν υπήρξε σφάλμα, βάλε στο path το μήνυμα σφάλματος
                        path_id = 0;
                    }
                }
                else{
                    path="Oops! An error occurred!";//Υπήρξε σφάλμα
                }

            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return path;
        }


        //Εμφανίζει την σελίδα με το μονοπάτι στον χάρτη ή τη σελίδα με το μήνυμα σφάλματος
        @Override
        protected void onPostExecute(String path) {

            String url ="";//Το url της σελίδας που θα γυρίσει
            // Απορρίπτει τον διάλογο όταν γυρίσει το path
            pDialog.dismiss();
            //Toast.makeText(getApplicationContext(), path + String.valueOf(path_id),
            //	   Toast.LENGTH_LONG).show();

            if (path_id == 0){//Αν δεν υπάρχει διαδρομή

                url = show_no_path_url + path;//Βάλε για url τη σελίδα σφάλματος. Η αίτηση θα γίνει με μέθοδο get με τιμή path το μήνυμα σφάλματος
            }
            else{

                //Εμφάνισε τα κουμπιά ώστε ο χρήστης να μπορεί να κάνει review
                btnSubmit.setVisibility(View.VISIBLE);
                btnDiscard.setVisibility(View.VISIBLE);
                url = show_path_url + path;//Βάλε για url τη σελίδα του χάρτη με το μονοπάτι. Η αίτηση θα γίνει με μέθοδο get με τιμή path τη διαδρομή του gpx στο server
            }

            setUpWebViewDefaults(browser);
            browser.setWebViewClient(new WebViewClient(){

                public void onPageFinished(WebView view, String url) {
                    //Δείχνει ένα μήνυμα μόλις η σελίδα φορτώσει- Εδώ δείχνει ότι τα μονοπάτι φορτώνει αφού η σελίδα είναι ασύγχρονη
                    Toast.makeText(getApplicationContext(), "Τhe path is loading",
                            Toast.LENGTH_LONG).show();
                }});

            browser.loadUrl(url);

        }
    }

    @SuppressLint({ "NewApi", "SetJavaScriptEnabled" })
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

    //Το κουμπί submit
    public void submit(View view){

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        //Χτίζει και στέλνει το Analytics Event.
        t.send(new HitBuilders.EventBuilder()
                .setCategory(categoryReviewPathId)
                .setAction(actionSubmitId)
                .setLabel(String.valueOf(spinnerReview.getSelectedItem())+" "+String.valueOf(spinnerReviewTags.getSelectedItem()))
                .build());

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);

        UserFunctions userFunction = new UserFunctions();

        int rated = spinnerReview.getSelectedItemPosition() + 1;//Η βαθμολογία του χρήστη είναι η θέση που επέλεξε + 1 (αφού οι θέσεις αρχίζουν από το 0)
        int rated_tags = spinnerReviewTags.getSelectedItemPosition() + 1;//Η βαθμολογία του χρήστη είναι η θέση που επέλεξε + 1 (αφού οι θέσεις αρχίζουν από το 0)
        JSONObject json = userFunction.reviewPath(user_id, path_id,rated,rated_tags);//Καλεί το storeReview.php script ώστε να γραφεί το review και γυρίζει ένα κατάλληλο μήνυμα

        String message ="Oops! Something goes wrong";
        try {

            if (json.getString("success") != null) {

                String result = json.getString("success");

                if (Integer.parseInt(result) == 1){//Αν το review γράφτηκε επιτυχώς, το message παίρνει το κατάλληλο μήνυμα
                    message = json.getString("message");
                }
                else{
                    message = json.getString("error_msg");//Αν το review δεν γράφτηκε επιτυχώς, το message παίρνει το κατάλληλο μήνυμα

                }
            }
            else{//Κάποιο λάθος έγινε...
                message="Oops! Something goes wrong";
            }

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Toast.makeText(getApplicationContext(), message,
                Toast.LENGTH_LONG).show();
        startActivity(new Intent(ReviewPathActivity.this,ReviewPathActivity.class));//Η activity καλέι τον ευατό της για να μπορεί ο χρήστης να κάνει ένα νέο review
        finish();
    }


    public void discard(View view){

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        //Χτίζει και στέλνει το Analytics Event.
        t.send(new HitBuilders.EventBuilder()
                .setCategory(categoryReviewPathId)
                .setAction(actionDiscardNewPathId)
                .build());

        Toast.makeText(getApplicationContext(), "Try loading new path",
                Toast.LENGTH_LONG).show();

        startActivity(new Intent(ReviewPathActivity.this,ReviewPathActivity.class));//Η activity καλεί τον εαυτό της για να μπορεί ο χρήστης να κάνει ένα νέο review
        finish();
    }


    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        GoogleAnalytics.getInstance(ReviewPathActivity.this).reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        GoogleAnalytics.getInstance(ReviewPathActivity.this).reportActivityStop(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Διόγκωση (Inflate) του μενού. Προσθέτει στοιχεία στην γραμμή ενεργειών (action bar) αν υπάρχει.
        getMenuInflater().inflate(R.menu.review_path, menu);
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
                        .setCategory(categoryMakeReviewID)
                        .setAction(actionBackToRecord)
                        .build());

                finish();
                return true;
            case R.id.action_menu_review_path:

                Tracker t1 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t1.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryMakeReviewID)
                        .setAction(actionMenuChoiseID)
                        .build());

                return true;
            case R.id.submenu_show_maps:

                Tracker t2 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t2.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryMakeReviewID)
                        .setAction(actionShowMapsID)
                        .build());

                Intent mapsIntent= new Intent(ReviewPathActivity.this,MapsActivity.class);
                mapsIntent.putExtra("mapsID",2);
                startActivity(mapsIntent);
                finish();

                return true;

            case R.id.submenu_rank__list_of_players:

                Tracker t3 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t3.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryMakeReviewID)
                        .setAction(actionRankingID)
                        .build());

                Intent intent2= new Intent(ReviewPathActivity.this,RankListOfPlayersActivity.class);
                startActivity(intent2);
                finish();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }
}

