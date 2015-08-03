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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/****************************************************
 * Η Activity για την οθόνη σύνδεσης στην εφαρμογή. *
 ****************************************************/
public class LoginActivity extends Activity {

    //Τα Id για τα googleAnalytics events
    private static String categoryLoginId = "LoginActivity buttons";
    private static String actionLoginId = "Login button";
    private static String actionRegisterMeId = "Register me button";

    Button btnLogin;
    Button btnLinkToRegister;
    EditText inputEmail;
    EditText inputPassword;
    TextView loginErrorMsg;

    // όνομα κόμβου(node) JSON απόκρισης
    private static String KEY_SUCCESS = "success";
    private static String KEY_UID = "uid";
    private static String KEY_NAME = "name";
    private static String KEY_EMAIL = "email";
    private static String KEY_CREATED_AT = "created_at";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.login);
        setContentView(R.layout.activity_login);

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        t.setScreenName("Login screen");
        t.send(new HitBuilders.AppViewBuilder().build());

        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        // εισαγωγή κουμπιών, πλαισίων, κτλ.
        inputEmail = (EditText) findViewById(R.id.loginEmail);
        inputPassword = (EditText) findViewById(R.id.loginPassword);
        btnLogin = (Button) findViewById(R.id.btnLogin);
        btnLinkToRegister = (Button) findViewById(R.id.btnLinkToRegisterScreen);
        loginErrorMsg = (TextView) findViewById(R.id.login_error);

        //κλικ συμβάν του κουμπιού σύνδεσης
        btnLogin.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {

                //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryLoginId)
                        .setAction(actionLoginId)
                        .build());

                //Αν υπάρχει σύνδεδη internet θα γίνει προσπάθεια σύνδεσης - αλλιώς θα βγάλει ένα μήνυμα στον χρήστη
                ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());

                if(mConnectionDetector.isNetworkConnected() == true &&  mConnectionDetector.isInternetAvailable()==true){

                    String email = inputEmail.getText().toString();
                    String password = inputPassword.getText().toString();
                    UserFunctions userFunction = new UserFunctions();
                    Log.d("Button", "Login");
                    //Καλεί την μέθοδο σύνδεσης που γυρίζει ένα JSON αντικείμενο
                    JSONObject json = userFunction.loginUser(email, password);

                    // ελέγχει την απόκριση σύνδεσης
                    try {
                        if (json.getString(KEY_SUCCESS) != null) {
                            loginErrorMsg.setText("");
                            String res = json.getString(KEY_SUCCESS);
                            if(Integer.parseInt(res) == 1){
                                // Ο χρήστης συνδέθηκε επιτυχώς
                                // Αποθηκεύει τις πληροφορίες του χρήστη στην SQLite Βάση
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

                                // Κλείνει την οθόνη σύνδεσης
                                finish();
                            }else{
                                // Έγινε λάθος κατά την σύνδεση
                                loginErrorMsg.setText("Incorrect username/password");
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                else
                {	//Μήνυμα στον χρήστη ότι δεν υπάρχει σύνδεση internet
                    Toast.makeText(getApplicationContext(), "You must be connected to the internet", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Σύνδεσμος για την οθόνη εγγραφής
        btnLinkToRegister.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {

                //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryLoginId)
                        .setAction(actionRegisterMeId)
                        .build());

                Intent i = new Intent(getApplicationContext(),
                        RegisterActivity.class);
                startActivity(i);
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        GoogleAnalytics.getInstance(LoginActivity.this).reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        GoogleAnalytics.getInstance(LoginActivity.this).reportActivityStop(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Διόγκωση (Inflate) του μενού. Προσθέτει στοιχεία στην γραμμή ενεργειών (action bar) αν υπάρχει.
        getMenuInflater().inflate(R.menu.login, menu);
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
