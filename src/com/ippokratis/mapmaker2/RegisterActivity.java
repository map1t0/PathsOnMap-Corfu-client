package com.ippokratis.mapmaker2;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import com.ippokratis.mapmaker2.library.ConnectionDetector;
import com.ippokratis.mapmaker2.library.DatabaseHandler;
import com.ippokratis.mapmaker2.library.UserFunctions;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/****************************************************
 * Η Activity για την οθόνη εγγραφής στην εφαρμογή. *
 ****************************************************/
public class RegisterActivity extends Activity {

    //Τα Id για τα googleAnalytics events
    private static String categoryRegisterId = "RegisterActivity buttons";
    private static String actionRegisterId = "Register button";
    private static String actionLoginMeId = "Login me button";

    Button btnRegister;
    Button btnLinkToLogin;
    EditText inputFullName;
    EditText inputEmail;
    EditText inputPassword;
    TextView registerErrorMsg;

    // ονόματα κόμβου(node) JSON απόκρισης
    private static String KEY_SUCCESS = "success";
    private static String KEY_ERROR = "error";
    private static String KEY_ERROR_MSG = "error_msg";
    private static String KEY_UID = "uid";
    private static String KEY_NAME = "name";
    private static String KEY_EMAIL = "email";

    private static String KEY_CREATED_AT = "created_at";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.register);
        setContentView(R.layout.activity_register);

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        t.setScreenName("Register screen");
        t.send(new HitBuilders.AppViewBuilder().build());

        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        // εισαγωγή κουμπιών, πλαισίων, κτλ.
        inputFullName = (EditText) findViewById(R.id.registerName);
        inputEmail = (EditText) findViewById(R.id.registerEmail);
        inputPassword = (EditText) findViewById(R.id.registerPassword);
        btnRegister = (Button) findViewById(R.id.btnRegister);
        btnLinkToLogin = (Button) findViewById(R.id.btnLinkToLoginScreen);
        registerErrorMsg = (TextView) findViewById(R.id.register_error);

        //κλικ συμβάν του κουμπιού εγγραφής
        btnRegister.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {

                //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRegisterId)
                        .setAction(actionRegisterId)
                        .build());

                //Αν υπάρχει σύνδεδη internet θα γίνει προσπάθεια σύνδεσης - αλλιώς θα βγάλει ένα μήνυμα στον χρήστη
                ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());

                if(mConnectionDetector.isNetworkConnected() == true &&  mConnectionDetector.isInternetAvailable()==true){

                    String name = inputFullName.getText().toString();
                    String email = inputEmail.getText().toString();
                    String password = inputPassword.getText().toString();
                    UserFunctions userFunction = new UserFunctions();
                    //Καλεί την μέθοδο σύνδεσης που γυρίζει ένα JSON αντικείμενο
                    JSONObject json = userFunction.registerUser(name, email, password);

                    // ελέγχει την απόκριση εγγραφής
                    try {
                        if (json.getString(KEY_SUCCESS) != null) {
                            registerErrorMsg.setText("");
                            String res = json.getString(KEY_SUCCESS);
                            if(Integer.parseInt(res) == 1){
                                // Ο χρήστης εγγράφηκε επιτυχώς
                                //Αποθηκεύει τις πληροφορίες του χρήστη στην SQLite Βάση
                                DatabaseHandler db = new DatabaseHandler(getApplicationContext());
                                JSONObject json_user = json.getJSONObject("player");

                                // Καθαρίζει όλα τα προηγούμενα δεδομένα της βάσης και βάζει τα καινούργια
                                userFunction.logoutUser(getApplicationContext());
                                db.addUser(json_user.getString(KEY_NAME), json_user.getString(KEY_EMAIL), json.getInt(KEY_UID),  json_user.getString(KEY_CREATED_AT));

                                // Ξεκινά την κεντρική οθόνη
                                Intent main = new Intent(getApplicationContext(), MainActivity.class);

                                // Κλείνει όλες τις προβολές- views- πριν ξεκινήσει το Dashboard
                                main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(main);

                                // Κλείνει την οθόνη εγγραφής
                                finish();
                            }else if(json.getString(KEY_ERROR) != null){
                                registerErrorMsg.setText("");
                                String resError = json.getString(KEY_ERROR);
                                if(Integer.parseInt(resError) == 2 || Integer.parseInt(resError) == 1){
                                    // Λάθος κατά την σύνδεση
                                    registerErrorMsg.setText(json.getString(KEY_ERROR_MSG));
                                }
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }else
                {	//Μήνυμα στον χρήστη ότι δεν υπάρχει σύνδεση internet
                    Toast.makeText(getApplicationContext(), "You must be connected to the internet", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Σύνδεσμος για την οθόνη σύνδεσης
        btnLinkToLogin.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {

                //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRegisterId)
                        .setAction(actionLoginMeId)
                        .build());

                Intent i = new Intent(getApplicationContext(),
                        LoginActivity.class);
                startActivity(i);
                // Σταματάει την τρέχουσα activity
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        GoogleAnalytics.getInstance(RegisterActivity.this).reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        GoogleAnalytics.getInstance(RegisterActivity.this).reportActivityStop(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Διόγκωση (Inflate) του μενού. Προσθέτει στοιχεία στην γραμμή ενεργειών (action bar) αν υπάρχει.
        getMenuInflater().inflate(R.menu.register, menu);
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
        return super.onOptionsItemSelected(item);
    }
}
