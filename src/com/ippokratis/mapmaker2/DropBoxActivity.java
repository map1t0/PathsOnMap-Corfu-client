package com.ippokratis.mapmaker2;

import java.io.File;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.android.AuthActivity;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.ippokratis.mapmaker2.library.UploadFile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

/*******************************************************************************************************************
 * Η activity για την οθόνη σύνδεσης της εφαρμογής με το DropBox.Μετά την σύνδεση ο χρήστης μπορεί να ανεβάσει τα  *
 * αρχεία gpx της διαδρομής που έχει καταγράψει στο DropBox.                                                       *
 * *****************************************************************************************************************/
public class DropBoxActivity extends Activity {

    //Τα Id για τα googleAnalytics events
    private static String categoryDropBoxId = "DropBoxActivity buttons";
    private static String actionLinkUnlinkId = "Link_Unlink button";
    private static String actionUploadGPSGpxId = "Upload gpsGpx button";
    private static String actionUploadGoogleGpxId = "Upload GoogleGpx button";
    private static String labelLinkID ="Link";
    private static String labelUnlinkID ="Unlink";
    private static String categoryDropBoxID = "DropBox Activity Menu";
    private static String actionBackToRecordFinish = "Back To Record Finish";

    private static final String TAG = "DropBoxMapMaker";

    ///////////////////////////////////////////////////////////////////////////
    //                Συγκεκριμένες ρυθμίσεις της εφαρμογής.                 //
    ///////////////////////////////////////////////////////////////////////////

    // Το κλειδί (key) και το μυστικό (secret) που έχει ανατεθεί στην εφαρμογή μας από DropBox.
    final static private String APP_KEY = "bnfqknk0q6zaa7d";
    final static private String APP_SECRET = "xii4bdf3rlmva74";

    ///////////////////////////////////////////////////////////////////////////
    //                Τέλος συγκεκριμένων ρυθμίσεων.                         //
    ///////////////////////////////////////////////////////////////////////////

    final static private String ACCOUNT_PREFS_NAME = "prefs";
    final static private String ACCESS_KEY_NAME = "ACCESS_KEY";
    final static private String ACCESS_SECRET_NAME = "ACCESS_SECRET";

    private static final boolean USE_OAUTH1 = false;//Το βάζουμε false, αφού θα χρησιμοποιήσουμε το OAUTH2. (Θα μπορούσε να λείπει)

    DropboxAPI<AndroidAuthSession> mApi;

    private boolean mLoggedIn;

    private Button mSubmit;//Το κουμπι σύνδεσης στο DropBox
    private LinearLayout mDisplay;//Η γραμμικη διάταξη που εμφανίζεται αν συνδεθεί ο χρήστης στο DropBox
    private Button mUpLoad;//Το κουμπί που ανεβάζει το αρχείο στο DropBox
    private Button mUpLoad2;//Το κουμπί που ανεβάζει το αρχείο του fused provider στο DropBox
    private String mGPXFileName;//Το όνομα του αρχείου που θα ανέβει στο DropBox
    private String mGPXFileNameGoogle;//Το όνομα του αρχείου που θα ανέβει στο DropBox (και έχει δημιουργηθεί με την google play service)

    //Ο φάκελος του DropBox στον οποίο θα αποθηκεύσουμε το αρχείο που θα ανεβάσουμε
    private final String GPX_DIR = "/GPXfile/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.drop_box_upload);

        //Παίρνουμε τα ονόματα των αρχείων αν έχουν σωθεί προηγουμένως
        if (savedInstanceState != null) {
            mGPXFileName = savedInstanceState.getString("mGPXFileName");

            mGPXFileNameGoogle = savedInstanceState.getString("mGPXFileNameGoogle");//το αρχείο από την google play location service
        }

        Intent intent = getIntent();
        mGPXFileName = intent.getExtras().getString("filename");//Το όνομα του αρχείου που μας το έστειλε η SavePathActivity

        mGPXFileNameGoogle = intent.getExtras().getString("filenameGoogle");//Το όνομα του αρχείου που μας το έστειλε η SavePathActivity

        // Δημιουργούμε ένα νέο AuthSession έτσι ώστε να μπορούμε να το χρησιμοποιήσουμε με το Dropbox API.
        AndroidAuthSession session = buildSession();
        mApi = new DropboxAPI<AndroidAuthSession>(session);

        setContentView(R.layout.activity_drop_box);//Θέτουμε το layout

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        t.setScreenName("Upload To DropBox screen");
        t.send(new HitBuilders.AppViewBuilder().build());

        checkAppKeySetup();//Ελέγχει αν έχουμε ένα έγκυρο κλειδί (αυτή η συνάρτηση βοηθάει μόνο τον προγραμματιστή), θα μπορούσε να λείπει

        mSubmit = (Button)findViewById(R.id.auth_button);//Το κουμπί της σύνδεσης με το DropBox

        mSubmit.setOnClickListener(new OnClickListener() {
            @SuppressWarnings("deprecation")//Δεν χρειάζεται να αλλάξει κάτι. Έχει μπει για την περίπτωση που υπάρχει OAUTH1 αντί OAUTH2
            //Εμείς χρησιμοποπιούμε το OAUTH2
            public void onClick(View v) {
                // Aποσυνδέει τον χρήστη αν είναι συνδεδεμένος, ή το αντίστροφο
                if (mLoggedIn) {

                    //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
                    Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                    //Χτίζει και στέλνει το Analytics Event.
                    t.send(new HitBuilders.EventBuilder()
                            .setCategory(categoryDropBoxId)
                            .setAction(actionLinkUnlinkId)
                            .setLabel(labelUnlinkID)
                            .build());

                    logOut();
                } else {

                    //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
                    Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                    //Χτίζει και στέλνει το Analytics Event.
                    t.send(new HitBuilders.EventBuilder()
                            .setCategory(categoryDropBoxId)
                            .setAction(actionLinkUnlinkId)
                            .setLabel(labelLinkID)
                            .build());

                    // Ξεκινά την απομακρυσμένη πιστοποίηση
                    if (USE_OAUTH1) {
                        mApi.getSession().startAuthentication(DropBoxActivity.this);
                    } else {
                        mApi.getSession().startOAuth2Authentication(DropBoxActivity.this);
                    }
                }
            }
        });

        mDisplay = (LinearLayout)findViewById(R.id.logged_in_display);//Το LinearLayout που εμφανίζεται αν ο χρήστης είναι συνδεδεμένος

        // Αυτό είναι το κουμπί που ανεβάζει ένα gpx αρχείο
        mUpLoad = (Button)findViewById(R.id.upload_button);
        mUpLoad2 = (Button)findViewById(R.id.upload_button2);

        //το κουμπί που ανεβάζει το gpx αρχείο του gps
        mUpLoad.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {

                //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryDropBoxId)
                        .setAction(actionUploadGPSGpxId)
                        .build());

                Context c = getApplicationContext();
                File file = new File(c.getFilesDir(), mGPXFileName);//Το αρχείο που θα ανεβάσουμε
                UploadFile upload = new UploadFile(DropBoxActivity.this, mApi, GPX_DIR, file);//Παίρνει ένα στιγμιότυπο της κλάσης UploadFile
                upload.execute();//προσπαθεί να ανεβάσει το αρχείο

            }
        });

        //το κουμπί που ανεβάζει το gpx αρχείο του fuxed provider
        mUpLoad2.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {

                //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryDropBoxId)
                        .setAction(actionUploadGoogleGpxId)
                        .build());

                Context c = getApplicationContext();
                File file2 = new File(c.getFilesDir(), mGPXFileNameGoogle);//Το δεύτερο αρχείο που θα ανεβάσουμε
                UploadFile upload2 = new UploadFile(DropBoxActivity.this, mApi, GPX_DIR, file2);//Παίρνει ένα στιγμιότυπο της κλάσης UploadFile
                upload2.execute();//προσπαθεί να ανεβάσει το δεύτερο αρχείο

            }
        });

        // Δείχνει το κατάλληλο UI ανάλογα με το αν ο χρήστης είναι συνδεδεμένος ή όχι
        setLoggedIn(mApi.getSession().isLinked());
    }

    //Σώζει τα ονόματα των αρχείων, όταν η activity έχει καταστραφεί από το σύστημα (π.χ. για απελευθέρωση χώρου)
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("mGPXFileName", mGPXFileName);
        outState.putString("mGPXFileNameGoogle", mGPXFileNameGoogle);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        GoogleAnalytics.getInstance(DropBoxActivity.this).reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        GoogleAnalytics.getInstance(DropBoxActivity.this).reportActivityStop(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AndroidAuthSession session = mApi.getSession();

        // Το επόμενο τμήμα θα πρέπει να τοποθετηθεί στη μέθοδο onResume () της activity
        // από την οποία το session.startAuthentication () καλέστηκε, έτσι ώστε
        // η πιστοποίηση του DropBox να ολοκληρωθείσωστά.
        if (session.authenticationSuccessful()) {
            try {
                // Υποχρεωτική κλήση για την ολοκλήρωση της πιστοποίησης
                session.finishAuthentication();

                // Τοπική αποθήκευση στην εφαρμογή για μελλοντική χρήση
                storeAuth(session);
                setLoggedIn(true);
            } catch (IllegalStateException e) {
                showToast("Couldn't authenticate with Dropbox:" + e.getLocalizedMessage());
                Log.i(TAG, "Error authenticating", e);
            }
        }
    }

    /**
     * Κρατάει τα κλειδιά πρόσβασης που γυρίζουν από τον Trusted Authenticator τοπικά αποθηκευμένα,
     * αντί να αποθηκεύσει το user name & password, και να κάνει εκ νέου πιστοποίηση
     * (το οποίο δεν πρέπει να γίνει, ποτέ).
     */
    private void storeAuth(AndroidAuthSession session) {
        // Αποθηκεύστε το διακριτικό πρόσβασης OAuth 2, εάν υπάρχει.
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, "oauth2:");
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.commit();
            return;
        }
        // Αποθηκεύει το διακριτικό πρόσβασης OAuth 1, εάν υπάρχει.  Αυτό είναι
        // μόνο απαραίτητο αν χρησιμοποιούσαμε το OAuth 1. Εμείς χρησιμοποιούμε το OAuth 2,
        //άρα θα μπορούσε να λείπει.
        AccessTokenPair oauth1AccessToken = session.getAccessTokenPair();
        if (oauth1AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, oauth1AccessToken.key);
            edit.putString(ACCESS_SECRET_NAME, oauth1AccessToken.secret);
            edit.commit();
            return;
        }
    }

    //Αποσυνδέει την εφαρμογή από το DropBox
    private void logOut() {
        //Καταργεί τις πιστοποιήσεις από την σύνοδο
        mApi.getSession().unlink();

        // Διαγράφει τα αποθηκευμένα κλειδιά
        clearKeys();
        // Αλλάζει το UI ώστε να δείχνει την αποσυνδεδεμένη έκδοση
        setLoggedIn(false);
    }

    // Διαγράφει τα αποθηκευμένα κλειδιά
    private void clearKeys() {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    /**
     * Συνάρτηση που αλλάζει το UI ανάλογα με το αν ο χρήστης είναι συνδεδεμένος ή όχι
     */
    private void setLoggedIn(boolean loggedIn) {
        mLoggedIn = loggedIn;
        if (loggedIn) {
            mSubmit.setText("Unlink from Dropbox");
            mDisplay.setVisibility(View.VISIBLE);
        } else {
            mSubmit.setText("Link with Dropbox");
            mDisplay.setVisibility(View.GONE);
        }
    }

    private void checkAppKeySetup() {
        //Ελέγχει αν έχουμε ένα έγκυρο κλειδί (αυτή η συνάρτηση βοηθάει μόνο τον προγραμματιστή)
        if (APP_KEY.startsWith("CHANGE") ||
                APP_SECRET.startsWith("CHANGE")) {
            showToast("You must apply for an app key and secret from developers.dropbox.com, and add them to the MapMaker ap before trying it.");
            finish();
            return;
        }

        // Ελέγχει αν η εφαρμογή έχει ένα σωστό manifest αρχείο (για να συνδεθεί με το DropBox)
        Intent testIntent = new Intent(Intent.ACTION_VIEW);
        String scheme = "db-" + APP_KEY;
        String uri = scheme + "://" + AuthActivity.AUTH_VERSION + "/test";
        testIntent.setData(Uri.parse(uri));
        PackageManager pm = getPackageManager();
        if (0 == pm.queryIntentActivities(testIntent, 0).size()) {
            showToast("URL scheme in your app's " +
                    "manifest is not set up correctly. You should have a " +
                    "com.dropbox.client2.android.AuthActivity with the " +
                    "scheme: " + scheme);
            finish();
        }
    }

    //Συνάρτηση που δείχνει ένα κατάλληλο προειδοποιητικό μήνυμα στον χρήστη
    private void showToast(String msg) {
        Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        error.show();
    }



    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
        return session;
    }

    /**
     * Φορτώνει τα κλειδιά πρόσβασης που γυρίζουν από τον Trusted Authenticator τα οποία έχουν αποθηκευτεί τοπικά,
     * αντί να αποθηκευτεί το user name & password, και να κάνει εκ νέου πιστοποίηση
     * (το οποίο δεν πρέπει να γίνει, ποτέ).
     */
    private void loadAuth(AndroidAuthSession session) {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) return;

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
            session.setOAuth2AccessToken(secret);
        } else {
            // Still support using old OAuth 1 tokens.
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Διόγκωση (Inflate) του μενού. Προσθέτει στοιχεία στην γραμμή ενεργειών (action bar) αν υπάρχει.
        getMenuInflater().inflate(R.menu.drop_box, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Χειρίζεται τα κλικ του χρήστη στο μενού.
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
                        .setCategory(categoryDropBoxID)
                        .setAction(actionBackToRecordFinish)
                        .build());

                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
