package com.ippokratis.mapmaker2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import com.ippokratis.mapmaker2.library.AsynGPXWriter;
import com.ippokratis.mapmaker2.library.ConnectionDetector;
import com.ippokratis.mapmaker2.library.UserFunctions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/*********************************************************************************************************************
 * Η Activity για την οθόνη που επιτρέπει στον χρήστη να ανεβάσει την διαδρομή του στον server καθώς και να δει τους *
 * πόντους που θα κερδίσει στην περίπτωση που το κάνει.                                                              *
 * *******************************************************************************************************************/
public class SavePathActivity extends Activity {

    private static String uploadPathUrl = "http://corfu.pathsonmap.eu/request_log_reg_store_path.php";//Το Url στο οποίο θα ανέβει η διαδρομή

    //private static String uploadPathUrl = "http://192.168.1.65/mapmaker_local/request_log_reg_store_path.php";//Το Url στο οποίο θα ανέβει η διαδρομή (τοπικά)

    //Τα Id για τα googleAnalytics events
    private static String categorySavePathId = "SavePathActivity buttons";
    private static String actionDiscardId = "Discard button";
    private static String actionUploadPathId = "Upload Path button";
    private static String categoryRecordFinishID = "Record Finish Menu";
    private static String actionMenuChoiseID = "Menu choise";
    private static String actionUploadDropBoxID = "UploadDropbox";
    private static String actionBackToRecordID = "Back To Record";
    private static String categoryDiscardDialogID = "Discard Alter Dialog";
    private static String actionNoID = "No";
    private static String actionYesID = "Yes";

    private boolean userHasUploadedThePath = false;//Αν ο χρήστης έχει ανεβάσει το αρχείο στο server
    private TextView tvDistanceMessage;//Εμφανίζει μήνυμα πόσους πόντους θα κερίδει ανάλογα με την απόσταση που διένυσε ο χρήστης
    private TextView tvTagLocationsMessage;//Εμφανίζει μήνυμα πόσους πόντους θα κερίδει ανάλογα με τα tags που έβαλε ο χρήστης
    private int numberofTagLocations;//Αριθμός των tags που έβαλε ο χρήστης
    private float totaldistance;//Η απόσταση που έχει διανύσει ο χρήστης
    private ProgressDialog pDialog;//Για να δείξει στον χρήστη ότι ανεβαίνει το gpx αρχείο


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.record_finish);
        setContentView(R.layout.activity_save_path);

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        t.setScreenName("Record Finish screen");
        t.send(new HitBuilders.AppViewBuilder().build());

        tvDistanceMessage=(TextView)findViewById(R.id.distance_message_to_user);
        tvTagLocationsMessage=(TextView)findViewById(R.id.tag_locations_message_to_user);
        Intent intent = getIntent();//Παίρνει το intent από την mainactivity
        numberofTagLocations = intent.getExtras().getInt("numberofTagLocations");
        totaldistance = intent.getExtras().getFloat("distance");//Η απόσταση που έχει διανύσει ο χρήστης
        double points=0;//Οι πόντοι που θα κερδίσει ο χρήστης

        points = Math.round(totaldistance) * 0.05;//Ο χρήστης κερδίζει 5 πόντους για κάθε 100 μέτρα

        int pointsOfTagLocations = numberofTagLocations * 20;//Ο χρήστης κερδίζει 20 πόντους για κάθε tag location;

        //Το μήνυμα που λέει στον χρήστη πόσους πόντους θα κερδίσει για την απόσταση που διένυσε
        tvDistanceMessage.setText(getString(R.string.first_part_of_distance_message_to_user)+" "+String.valueOf(points)+" "
                +getString(R.string.second_part_of_distance_message_to_user)+" "+String.valueOf(Math.round(totaldistance))+" "
                +getString(R.string.third_part_of_distance_message_to_user));
        tvTagLocationsMessage.setText(getString(R.string.first_part_of_tag_locations_message_to_user)+" "+String.valueOf(pointsOfTagLocations)+" "
                +getString(R.string.second_part_of_tag_locations_message_to_user)+" "+String.valueOf(numberofTagLocations)+" "
                +getString(R.string.third_part_of_tag_locations_message_to_user));

        //Η δημιουργία του gpx αρχείου που δημιουργείται από το gps
        Context c = getApplicationContext();
        File file = new File(c.getFilesDir(), "path.gpx");//Το όνομα του αρχείου που θα αποθηκευτεί η διαδρομή
        File segmentfile = new File(c.getFilesDir(), "segmentOfTrkpt.txt");//Tο όνομα του αρχείου που περιέχει το τμήμα με τα trackpoints
        File segmentOfWayPointsFile = new File(c.getFilesDir(), "segmentOfWpt.txt");//Tο όνομα του αρχείου που περιέχει το τμήμα με τα waypoints

        if (file.exists()){//Αν το αρχείο περιέχει δεδομένα από μια παλιότερη διαδρομή, το "καθαρίζουμε"
            String string1 = "";
            FileWriter fWriter;
            try{
                fWriter = new FileWriter(file);
                fWriter.write(string1);
                fWriter.flush();
                fWriter.close();
            }
            catch (Exception e) {
                e.printStackTrace();}
        }

        //Η δημιουργία του gpx αρχείου που δημιουργείται από τον fused provider
        File fileGoogle = new File(c.getFilesDir(), "pathGoogle.gpx");//Το όνομα του αρχείου που θα αποθηκευτεί η διαδρομή που έχει προκύψει από την google service
        File segmentfileGoogle = new File(c.getFilesDir(), "segmentOfTrkptGoogle.txt");//Tο όνομα του αρχείου που περιέχει το τμήμα με τα trackpoints που έχουν προκύψει από την google service
        File segmentOfWayPointsFileGoogle = new File(c.getFilesDir(), "segmentOfWptGoogle.txt");

        if (fileGoogle.exists()){//Αν το αρχείο περιέχει δεδομένα από μια παλιότερη διαδρομή, το "καθαρίζουμε"
            String string1 = "";
            FileWriter fWriter;
            try{
                fWriter = new FileWriter(fileGoogle);
                fWriter.write(string1);
                fWriter.flush();
                fWriter.close();
            }
            catch (Exception e) {
                e.printStackTrace();}
        }

        //Εδώ καταγράφεται η διαδρομή στα δύο GPX αρχεία
        AsynGPXWriter asynWrFile = new AsynGPXWriter(file,segmentfile,segmentOfWayPointsFile, SavePathActivity.this,fileGoogle,segmentfileGoogle,segmentOfWayPointsFileGoogle);

        asynWrFile.execute();
    }

    //Όταν ο χρήστης απορίψει την διαδρομή του δείχνει ένα μήνυμα ότι η διαδρομή θα χαθεί
    public void onBtnDiscardClicked(View view){

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        //Χτίζει και στέλνει το Analytics Event.
        t.send(new HitBuilders.EventBuilder()
                .setCategory(categorySavePathId)
                .setAction(actionDiscardId)
                .build());

        showDiscardAlertToUser();
    }

    //Η προειδοποίηση στον χρήστη, ότι η διαδρομή θα χαθεί
    private void showDiscardAlertToUser(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(getString(R.string.alter_dialog_for_discard))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok_button_of_alter_dialog_for_discard),
                        new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int id){

                                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                                //Χτίζει και στέλνει το Analytics Event.
                                t.send(new HitBuilders.EventBuilder()
                                        .setCategory(categoryDiscardDialogID)
                                        .setAction(actionYesID)
                                        .build());

                                finishActivity();
                            }
                        });
        alertDialogBuilder.setNegativeButton(getString(R.string.cancel_button_of_alter_dialog_for_discard),
                new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id){

                        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                        //Χτίζει και στέλνει το Analytics Event.
                        t.send(new HitBuilders.EventBuilder()
                                .setCategory(categoryDiscardDialogID)
                                .setAction(actionNoID)
                                .build());

                        dialog.cancel();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    public void finishActivity(){
        this.finish();//Σταματάει την SavePathActivity
    }

    //Η συνάρτηση που καλείται όταν ο χρήστης επιλέξει να ανεβάσει την διαδρομή
    public void onBtnUploadPathClicked(View view){

        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        //Χτίζει και στέλνει το Analytics Event.
        t.send(new HitBuilders.EventBuilder()
                .setCategory(categorySavePathId)
                .setAction(actionUploadPathId)
                .build());

        //Αν ο χρήστης έχει ανεβάσει ήδη την διαδρομή, δεν τον αφήνει να την ξανανεβάσει
        if(userHasUploadedThePath == true){
            Toast.makeText(this,
                    getString(R.string.msg_already_uploaded), Toast.LENGTH_LONG).show();
        }
        else{
            //Εδώ θα ανέβουν τα GPX αρχεία στον Server

            if (android.os.Build.VERSION.SDK_INT > 9)
            {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                StrictMode.setThreadPolicy(policy);
            }

            //Αν υπάρχει σύνδεδη internet η διαδρομή θα ανέβει - αλλιώς θα βγάλει ένα μήνυμα στον χρήστη
            ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());

            if(mConnectionDetector.isNetworkConnected() == true &&  mConnectionDetector.isInternetAvailable()==true){

                //Υπάρχει σύνδεση internet και έτσι καλείται η UploadGpxFileToServer ώστε να ανέβουν τα αρχεία
                new UploadGpxFileToServer(numberofTagLocations,totaldistance).execute();
                userHasUploadedThePath = true;//Τα αρχεία ανέβηκαν
            }
            else{//Δεν υπάρχει σύνδεση internet
                Toast.makeText(getApplicationContext(), "You must be connected to the internet", Toast.LENGTH_LONG).show();
            }
        }
    }


    /**
     * Background Async Task για να ανέβουν τα gpx αρχεία - με αίτημα HTTP
     * */
    class UploadGpxFileToServer extends AsyncTask<String, String, String> {

        private int mnumberOfTags;//ο αριθμός των tags (waypoints)
        private float mdistance;
        String data;
        String json = "";
        String KEY_SUCCESS = "success";
        String KEY_MESSAGE = "message";
        String KEY_ERROR = "error";
        String KEY_ERROR_MESSAGE = "error_msg";

        public UploadGpxFileToServer(int numberOfTags,float distance){

            mnumberOfTags=numberOfTags;
            mdistance=distance;
        }

        @Override
        protected void onPreExecute() {//Δείχνουμε ότι η διαδρομή ανεβαίνει στον χρήστη
            super.onPreExecute();
            pDialog = new ProgressDialog(SavePathActivity.this);
            pDialog.setMessage("Uploading path...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            String res ="";
            String resMes="";
            try{

                Context c = getApplicationContext();
                File file = new File(c.getFilesDir(), "path.gpx");//Το όνομα του αρχείου που έχει αποθηκευτεί η διαδρομή

                File fileGoogle = new File(c.getFilesDir(), "pathGoogle.gpx");//Το όνομα του αρχείου που έχει αποθηκευτεί η διαδρομή από την google service

                Log.v("SavePathActivity.java", "postURL: " + uploadPathUrl);

                // ένας νέος HttpClient
                HttpClient httpClient = new DefaultHttpClient();

                // post header - κεφαλίδα
                HttpPost httpPost = new HttpPost(uploadPathUrl);

                //Το fileBody θα περιέχει το αρχείο gpx που δημιούργησε το gps
                FileBody fileBody = new FileBody(file);

                //Το fileBodyGoogle θα περιέχει το αρχείο gpx που δημιούργησε ο fused provider
                FileBody fileBodyGoogle = new FileBody(fileGoogle);

                //Εδώ αποκτάται το uid του χρήστη
                UserFunctions userFunctions = new UserFunctions();
                String uid = String.valueOf(userFunctions.getUserUid(getApplicationContext()));
                StringBody stringBody = new StringBody(uid,ContentType.TEXT_PLAIN);

                StringBody stringBody2 = new StringBody(String.valueOf(mnumberOfTags),ContentType.TEXT_PLAIN);//ο αριθμός των tags της διαδρομής
                StringBody stringBody3 = new StringBody("storageFile",ContentType.TEXT_PLAIN);//στο post θα βάλουμε στο tag = storageFile για να ξέρει ο server τι να κάνει
                int metersOfPath = Math.round(mdistance);//Το μέτρα της διαδρομής
                StringBody stringBody4 = new StringBody(String.valueOf(metersOfPath),ContentType.TEXT_PLAIN);

                //Δημιουργία του builder
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

                //Προσθέτουμε τα αρχεία και τα strings στον builder
                builder.addPart("file", fileBody);
                builder.addPart("fileGoogle", fileBodyGoogle);
                builder.addPart("tag", stringBody3);
                builder.addPart("player_id",stringBody);
                builder.addPart("tagsOfPath",stringBody2);
                builder.addPart("meters",stringBody4);
                HttpEntity reqEntity = builder.build();

                httpPost.setEntity(reqEntity);

                // εκτέλεση του HTTP post αιτήματος
                HttpResponse response = httpClient.execute(httpPost);
                HttpEntity resEntity = response.getEntity();

                //Εδώ θα διαβαστεί η απόκριση του αιτήματος
                InputStream is = null;
                is = resEntity.getContent();//Δημιουργεί ένα νέο InputStream αντικείμενο της οντότητας.
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

                    json = sb.toString();//Επιστρέφει τα περιεχόμενα αυτού του builder (του sb εδώ)
                    Log.e("JSON", json);
                } catch (Exception e) {
                    Log.e("Buffer Error", "Error converting result " + e.toString());
                }
                // Προσπαθεί να αναλύσει την συμβολοσειρά σε ένα αντκείμνο JSON
                JSONObject jObj = null;//Ένα json αντικείμενο
                try {
                    jObj = new JSONObject(json);//Δημιουργεί ένα νέο JSONObject με τις αντιστοιχίσεις όνοματος / τιμής από τη συμβολοσειρά JSON.
                } catch (JSONException e) {
                    Log.e("JSON Parser", "Error parsing data " + e.toString());
                }

                if (jObj.getString(KEY_SUCCESS) != null){

                    res = jObj.getString(KEY_SUCCESS);

                    if(Integer.parseInt(res) == 1){//Αν ήταν επιτυχής η αποθήκευση

                        resMes=jObj.getString(KEY_MESSAGE);//Πάρε το μήνυμα της απόκρισης
                    }
                    else if(Integer.parseInt(res) == 0 && jObj.getString(KEY_ERROR) != null){
                        if(Integer.parseInt(jObj.getString(KEY_ERROR)) == 1){//Αν δεν ήταν επιτυχής η αποθήκευση
                            resMes=jObj.getString(KEY_ERROR_MESSAGE);//Πάρε το μήνυμα λάθους
                        }

                    }
                }

                //Γράφει στο log την απόκριση του αιτήματος
                if (resEntity != null) {

                    String responseStr = EntityUtils.toString(resEntity).trim();
                    Log.v("SavePathActivity.java", "Response: " +  responseStr);
                }



            } catch (NullPointerException e) {
                e.printStackTrace();
                return resMes="Error Connecting to server";

            }
            catch (Exception e) {
                e.printStackTrace();

            }

            return resMes;//Γυρίζει το μήνυμα που θα εμφανιστεί στον χρήστη
        }

        //Όταν τελειώση η προσπάθεια ανεβάσματος της διαδρομής σταμάτησε τον διάλογο - εμφάνισε το κατάλληλο μήνυμα και σταμάτα την activity
        @Override
        protected void onPostExecute(String resMes) {

            // dismiss the dialog after getting all products
            pDialog.dismiss();
            Toast.makeText(getApplicationContext(), resMes,
                    Toast.LENGTH_LONG).show();
            finishActivity();
        }
    }


    //Για το harware button
    @Override
    public void onBackPressed() {
        //Αν χρήστης δεν έχει ανεβάσει το path θα τον ρωτήσει
        //αν θέλει να γυρίσει πίσω
        if(userHasUploadedThePath==false){
            showDiscardAlertToUser();
        }
        else{
            super.onBackPressed();
        }//αλλιώς θα γυρίσει πίσω
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        GoogleAnalytics.getInstance(SavePathActivity.this).reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        GoogleAnalytics.getInstance(SavePathActivity.this).reportActivityStop(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Διόγκωση (Inflate) του μενού. Προσθέτει στοιχεία στην γραμμή ενεργειών (action bar) αν υπάρχει.
        getMenuInflater().inflate(R.menu.save_path, menu);
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
                        .setCategory(categoryRecordFinishID)
                        .setAction(actionBackToRecordID)
                        .build());

                //Αν χρήστης δεν έχει ανεβάσει το path θα τον ρωτήσει
                //αν θέλει να γυρίσει πίσω
                if(userHasUploadedThePath==false){
                    showDiscardAlertToUser();
                }
                else{
                    NavUtils.navigateUpFromSameTask(this);
                }//αλλιώς θα γυρίσει πίσω

                return true;

            case R.id.action_menu1:

                Tracker t1 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t1.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRecordFinishID)
                        .setAction(actionMenuChoiseID)
                        .build());

                return true;

            //Απόκριση του DropBox μενού
            case R.id.submenu_drop_box:

                Tracker t2 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t2.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRecordFinishID)
                        .setAction(actionUploadDropBoxID)
                        .build());

                if (android.os.Build.VERSION.SDK_INT > 9)
                {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                }

                //αν υπάρχει σύνδεση internet, ξεκινάει την dropbox activity
                ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());

                if(mConnectionDetector.isNetworkConnected() == true &&  mConnectionDetector.isInternetAvailable()==true){
                    Intent intent2 = new Intent(this, DropBoxActivity.class);
                    intent2.putExtra("filename", "path.gpx");
                    intent2.putExtra("filenameGoogle", "pathGoogle.gpx");
                    startActivity(intent2);
                }
                else{//Αλλιώς λέει στον χρήστη ότι πρέπει να συνδεθεί στο internet

                    Toast.makeText(getApplicationContext(), "You must be connected to the internet", Toast.LENGTH_LONG).show();

                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
