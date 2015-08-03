package com.ippokratis.mapmaker2;

import java.io.File;
import java.util.ArrayList;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.ippokratis.mapmaker2.library.ConnectionDetector;
import com.ippokratis.mapmaker2.library.DownloadGPXFileAsync;
import com.ippokratis.mapmaker2.library.UserFunctions;
import com.ippokratis.mapmaker2.library.ParsingGPXForDrawing;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


/*********************************************************************************************************************
 * FragmentActivity που είναι υπεύθυνη για την αρχική οθόνη της εφαρμογής μετά το login. Δείχνει τον χάρτη της πόλης *
 * μαζί με τις διαδρομές που έχουν κάνει μέχρι τώρα οι χρήστες. Επίσης δίνει την δυνατότητα για να καταγραφεί μια    *
 * διαδρομή. Επίσης, ο χρήστης μπορεί να επιλέξει να επισημάνει τον δρόμο κατά την καταγραφή.                        *
 * *******************************************************************************************************************/
public class MainActivity extends FragmentActivity {

    //Τα Id για τα googleAnalytics events
    private static String categoryRecordScreenbtId = "Record Activity buttons";
    private static String actionStartStopId = "Start_Stop button";
    private static String actionSubmitId = "Submit path type button";
    private static String labelStartID ="start";
    private static String labelStopID ="stop";
    private static String categoryRecordMenuID = "Record Activity Menu";
    private static String actionMenuChoiseID = "Menu choise";
    private static String actionShowMapsID = "Show Maps";
    private static String actionMakeReviewID="Make a review";
    private static String actionRankingID="Ranking";
    private static String actionLogOutID = "Log Out";
    private static String categoryHighAccuracyID = "HighAccuracy Alter Dialog";
    private static String categoryWiFiID = "WiFi Alter Dialog";
    private static String categoryMobileDataID = "Mobile Data Alter Dialog";
    private static String actionCancelID = "Cancel";
    private static String actionGoSettingsID = "GoToSetting";

    private static String  allPathsFileUrl = "http://corfu.pathsonmap.eu/mergeFile/merge_gpx.gpx";

    UserFunctions userFunctions;//Την βάζουμε ώστε να μπορούμε να καλέσουμε την κατάλληλη συνάρτηση όταν ο χρήστης κάνει logout

    Polyline polyline;//H "πολυγραμμή" που θα δείχνει την διαδρομή του χρήστη.
    private PolylineOptions rectOptions = new PolylineOptions()//Αρχικοποίηση των επιλογών της "πολυγραμμής" που θα δείχνει την διαδρομή του χρήστη
            .width(5)
            .color(Color.GREEN)
            .geodesic(true);

    //Την enableNewLocationAddToPolyline την χρησιμοποιήμε, γιατί στην περίπτωση που η MyLocationService έχει γυρίσει πίσω τις προηγούμενες θέσεις του χρήστη
    //(όταν η MainActivity γίνει onResume ή onCreate ενώ η MyLocationService τρέχει), θέλουμε πρώτα να δημιουργηθεί η polyline του χρήστη που δείχνει την
    // διαδρομή του μέχρι τώρα, και μετά να προστεθούν σε αυτή οι καινούργιες θέσεις του χρήστη
    boolean enableNewLocationAddToPolyline=true;

    //Η firstTimeOnResumeAfterCreated δηλώνει αν είναι η πρώτη φορά που η MainActivity μπαίνει στην onResume(). Αυτή χρησιμοποιείται σε συνδυασμό με το αν η
    //MyLocationService τρέχει, ώστε οι παλιές θέσεις του χρήστη να μην ξαναζητηθούν αν είναι η πρώτη φορά, αφού αν αυτές υπάρχουν έχουν ζητηθεί στην onCreate()
    boolean firstTimeOnResumeAfterCreated=true;
    //Η firstTimeTheActivityIsBindedToMyLocationService δείχνει αν η MainActivity συνδέται για πρώτη φορά στην υπηρεσία. Αν δεν είναι η πρώτη φορά
    //βοηθάει στο να ζητήσουμε τις παλιές θέσεις του χρήστη κατά την σύνδεση στην υπηρεσία (αφού σε αυτή την περίπτωση η MaiActivity έχει καταστραφεί) και όταν
    //ξαναδημιουργείται η υπηρεσία "τρέχει", άρα θα έχει τις παλιές θέσεις του χρήστη
    boolean firstTimeTheActivityIsBindedToMyLocationService=true;

    private ToggleButton togbtnStartRoute;//Το κουμπί (on-off) για να αρχίσει η καταγραφή της διαδρομής
    private TextView tvLocation;//Μήνυμα ανάλογα με αν έχει φιξαριστεί η θέση ή όχι ή όχι
    private ProgressBar pbLocationProgress;//Δείχνει στον χρήστη ότι προσπαθεί να βρει την τοποθεσία του, ώστε αυτός να μπορεί να ξεκινήσει την διαδρομή του
    private Button btnSubmitPathType;//Επιτρέπει στον χρήστη να υποβάλει το είδος της διαδρομής
    private Spinner spinnerPathType;//Το spinner με τα είδη της διαδρομής
    private TextView tvSelectPedestrianType;
    private GoogleMap googleMapv2;//Ο χάρτης της Google από το API v2

    Messenger mService = null;//O Messenger της υπηρεσίας (μέσω του οποίου στέλνονται τα μηνύματα στην υπηρεσία)
    boolean mIsBound;//Δείχνει αν η activity έχει συνδεθεί στην υπηρεσία ή όχι

    //Ο messenger που ορίζεται από τον client ώστε η service να μπορεί να στέλνει μηνύματα πίσω.
    final Messenger mMessenger = new Messenger(new Handler(new IncomingHandlerCallback()));

    //Ο Handler που χειρίζεται τα μηνύματα που στέλνει η υπηρεσία MyLocationService
    class IncomingHandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) { //Ο client χειρίζεται τα message (από την service: MyLocationService) στην handle message μέθοδο
            switch (msg.what) {
                case MyLocationService.MSG_SET_LAST_LOCATION: //Η MyLocationService στέλνει την τελευταία εντοπισμένη θέση του χρήστη
                    double latitude =msg.getData().getDouble("latitude");//Το γεωγρφικό πλάτος του χρήστη
                    double longitude =msg.getData().getDouble("longitude");//Το γεωγραφικό μήκος του χρήστη
                    gotoMyLocation(latitude,longitude);//Καλείται η gotoMyLocation ώστε ο χάρτης να κεντραριστεί στη νέα θέση, αλλά και να προστεθεί αυτή στη polyline
                    break;
                case MyLocationService.MSG_GOOGLE_PLAY_SERVICE_RESULT_CODE://Λαμβάνεται ο κώδικας αποτελέσματος κατά την αποτυχία σύνδεσης στην google play service
                    int resultCode=msg.getData().getInt("result_code");
                    DisplayErrorDialog(resultCode);// Εμφανίζει έναν διάλογο λάθους
                    break;
                case MyLocationService.MSG_SET_LOCATION_FIXED://Ο client λαμβάνει μήνυμα ότι η πρώτη θέση του χρήστη βρέθηκε
                    pbLocationProgress.setVisibility(View.INVISIBLE);//η μπάρα προόδου γίνεται αόρατη (ώστε να δηλωθεί στον χρήστη ότι μπορεί να ξεκινήσει την διαδρομή)
                    tvLocation.setText(getString(R.string.location_is_fixed));
                    btnSubmitPathType.setVisibility(View.VISIBLE);//Ώστε ο χρήστης να μπορεί να υποβάλει είδος μονοπατιού
                    spinnerPathType.setVisibility(View.VISIBLE);
                    tvSelectPedestrianType.setVisibility(View.VISIBLE);
                    break;
                case MyLocationService.MSG_SET_LOCATION_LOST:
                    pbLocationProgress.setVisibility(View.VISIBLE);//η μπάρα προόδου γίνεται ορατή (ώστε να δηλωθεί στον χρήστη ότι χάθηκε  η θέση)
                    tvLocation.setText(getString(R.string.location));
                    btnSubmitPathType.setVisibility(View.INVISIBLE);//Ώστε ο χρήστης να μην μπορεί να υποβάλει είδος μονοπατιού (αφού δεν έχουμε ακριβής τοποθεσία)
                    spinnerPathType.setVisibility(View.INVISIBLE);
                    tvSelectPedestrianType.setVisibility(View.INVISIBLE);
                    break;
                case MyLocationService.MSG_SEND_POINTS_OF_POLYLINE_AND_TAGS://Λαμβάνονται οι εντοπισμένες θέσεις του χρήστη μέχρι στιγμής (και τα tags)
                    enableNewLocationAddToPolyline=false;//Εμποδίζει την προσθήκη νέας θέσης στον χάρτη
                    ArrayList <LatLng> arrayOfCoordinates = msg.getData().getParcelableArrayList("coordinatesArrayList");//Λίστα με τις συντεταγμένες των εντοπισμένων θέσεων του χρήστη
                    ArrayList<LatLng> arrayOfTagLocations = msg.getData().getParcelableArrayList("tagLocationArrayList");//Λίστα με τις θέσεις του χρήστη που έχουν tags
                    ArrayList<String> arrayOfPathTypes = msg.getData().getStringArrayList("pathTypeArrayList");//Λίστα με τα είδη της διαδρομής (παράλληλη λίστα με την arrayOfTagLocations)

                    if (googleMapv2 != null){
                        googleMapv2.clear();//Καθαρίζει τις polyline (και τα tags) που έχουν ζωγραφιστεί στον χάρτη

                        loadAllPathsIfServiceRunning();

                        //Για να κεντράρει στα γρήγορα τον χάρτη κοντά στην θέση του χρήστη εάν υπήρχε εντοπισμένη θέση
                        if (arrayOfCoordinates.size()!=0){
                            CameraPosition cameraPosition = new CameraPosition.Builder().target(
                                    new LatLng(arrayOfCoordinates.get(arrayOfCoordinates.size()-1).latitude, arrayOfCoordinates.get(arrayOfCoordinates.size()-1).longitude)).zoom(16.5f).build();
                            googleMapv2.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                        }
                        rectOptions=new PolylineOptions();//Αρχικοποίηση των επιλογών της νέας polyline που θα ζωγραφιστεί
                        rectOptions.width(5)
                                .color(Color.GREEN)
                                .geodesic(true);

                        if (arrayOfCoordinates.size()!=0){
                            //Προσθέτει όλες τις εντοπισμένες θέσεις του χρήστη στην polyline (κατά χρονολογική σειρά) -εάν υπάρχουν τέτοιες
                            for (int i = 0; i < arrayOfCoordinates.size(); i++){
                                rectOptions.add(new LatLng(arrayOfCoordinates.get(i).latitude, arrayOfCoordinates.get(i).longitude));
                            }
                        }

                        polyline = googleMapv2.addPolyline(rectOptions);//Ζωγραφίζει την διαδρομή του χρήστη μέχρι τώρα

                        //Προσθέτει όλες τις θέσεις του χρήστη με ετικέτα εάν υπάρχουν
                        if (arrayOfTagLocations.size()!=0){
                            for (int i = 0; i < arrayOfTagLocations.size(); i++){
                                googleMapv2.addMarker(new MarkerOptions()
                                        .position(new LatLng(arrayOfTagLocations.get(i).latitude, arrayOfTagLocations.get(i).longitude))
                                        .title(arrayOfPathTypes.get(i))
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                            }
                        }
                    }

                    enableNewLocationAddToPolyline=true;//Επιτρέπει την προσθήκη νέας θέσης στον χάρτη
                    break;
                case MyLocationService.MSG_SEND_ALL_LOCATIONS_AND_TOTAL_DISTANCE_BEFORE_STOP://Λαμβάνονται όλες οι εντοπισμένες θέσεις του χρήστη και η απόσταση που διένυσε

                    boolean numberOfLcationsGreaterThanZero = msg.getData().getBoolean("numberOfLocationsDifferentZero");//Αν έχουν εντοπιστεί θέσεις
                    int numberOfTagLocations = msg.getData().getInt("numberOfTags");//Αριθμός των tags
                    float distance = msg.getData().getFloat("totalDistance");//Η απόσταση που έχει διανύσει ο χρήστης
                    boolean userHasGoneOut = msg.getData().getBoolean("hasGoneOutOfTown");
                    String townOfInterest = msg.getData().getString("town");
                    doUnbindService();//Ξεσυνδέεται από την υπηρεσία (αν είναι συνδεδεμένη)
                    //Σταματάει την υπηρεσία (αν τρέχει)
                    try{
                        stopService(new Intent(MainActivity.this, MyLocationService.class));//Σταματάει την υπηρεσία
                    }
                    catch(Throwable t){
                        Log.e("MainActivity", "Failed to stop the service", t);
                    }
                    if (googleMapv2 != null){
                        googleMapv2.clear();//Καθαρίζει τις polyline (και τα tags) που έχουν ζωγραφιστεί στον χάρτη ώστε να είναι "καθαρός" αν γυρίσει ο χρήστης πίσω
                    }

                    startSavePathActivity(numberOfLcationsGreaterThanZero,numberOfTagLocations, distance,userHasGoneOut,townOfInterest);//Θα ξεκινήσει την Activity που ο χρήστης μπορεί να σώσει την διαδρομή
                    break;
                case MyLocationService.MSG_SEND_CURRENT_TAG_LOCATION://Η θέση που γυρίζει από την αίτηση για το tag
                    double currentLatitude=msg.getData().getDouble("currentLatitude");
                    double currentLongitude=msg.getData().getDouble("currentLongitude");
                    String pathType = msg.getData().getString("pathType");
                    Toast.makeText(getApplicationContext(), "The path type is submitted",
                            Toast.LENGTH_SHORT).show();
                    addMarkerToMap(currentLatitude, currentLongitude,pathType);
                    break;
                case MyLocationService.MSG_SEND_OUT_OF_REGION: //Η MyLocationService στέλνει την τελευταία εντοπισμένη θέση του χρήστη
                    String town  =msg.getData().getString("town");//Το γεωγρφικό πλάτος του χρήστη
                    //double longitude =msg.getData().getDouble("longitude");//Το γεωγραφικό μήκος του χρήστη
                    //gotoMyLocation(latitude,longitude);//Καλείται η gotoMyLocation ώστε ο χάρτης να κεντραριστεί στη νέα θέση, αλλά και να προστεθεί αυτή στη polyline
                    doUnbindService();//Ξεσυνδέεται από την υπηρεσία (αν είναι συνδεδεμένη)
                    //Σταματάει την υπηρεσία (αν τρέχει)
                    try{
                        stopService(new Intent(MainActivity.this, MyLocationService.class));//Σταματάει την υπηρεσία
                    }
                    catch(Throwable t){
                        Log.e("MainActivity", "Failed to stop the service", t);
                    }
                    if (googleMapv2 != null){
                        googleMapv2.clear();//Καθαρίζει τις polyline (και τα tags) που έχουν ζωγραφιστεί στον χάρτη ώστε να είναι "καθαρός" αν γυρίσει ο χρήστης πίσω
                    }
                    togbtnStartRoute.setChecked(false);//Ξαναγύρισε το κουμπί στην off κατάσταση, ώστε ο χρήστης να μπορεί να ξεκινήσει μετά την ενεργοποίηση την καταγραφή
                    Toast.makeText(getApplicationContext(), "Sorry, you are out of " + town,
                            Toast.LENGTH_LONG).show();
                    break;
                default:
                    //Δεν κάνει τίποτα
            }
            return true;//Δηλώνει ότι η handleMessage χειρίστηκε το μήνυμα 
        }
    }

    //Μέθοδος που ξεκινάει την Activity για την αποθήκευση της διαδρομής (τις στέλνει τα δεδεομένα που εντοπίστηκαν)-αν όμως δεν έχει καταγρφεί καμιά θέση μένει στην mainActivity
    public void startSavePathActivity(boolean locationsDifferentZero,int numberofTagLocations,Float totalDistance,boolean hasGoneOut,String town){

        if(locationsDifferentZero==true && hasGoneOut==false){
            Intent intent = new Intent(this, SavePathActivity.class);
            intent.putExtra("numberofTagLocations",numberofTagLocations);
            intent.putExtra("distance",totalDistance);
            startActivity(intent);
        }
        else if(locationsDifferentZero==true && hasGoneOut==true){
            Toast.makeText(getApplicationContext(), "Sorry, you have gone out of "+town,
                    Toast.LENGTH_LONG).show();
        }
        else{
            Toast.makeText(getApplicationContext(), "Sorry, the path was not recorded, because of location provider problems",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Κλάση για την αλληλεπίδραση με την κύρια διεπαφή (interface) της MyLocationService
     */

    private ServiceConnection mConnection = new ServiceConnection() {//Καλείται όταν ο client κάνει σύνδεση (bind) στην υπηρεσία
        public void onServiceConnected(ComponentName className, IBinder service) {//Το σύστημα καλεί αυτήν ώστε να παραδοθεί το IBinder που γυρίζει η onBind() μέθοδος της MyLocationService.
            mService = new Messenger(service); //Ο messenger με τον οποίο στέλνουμε μηνύματα στην υπηρεσία

            if(firstTimeTheActivityIsBindedToMyLocationService==false){//Αν δεν είναι η πρώτη φορά η υπηρεσία τρέχει και περιέχει τις θέσεις του χρήστη
                try{
                    //Ζητάει τις θέσεις του χρήστη μέχρι τώρα
                    Message msg = Message.obtain(null, MyLocationService.MSG_REQUEST_POINTS_OF_POLYLINE_AND_TAGS);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                }
                catch (RemoteException e) {
                    //Η υπηρεσία έχει καταρρεύσει
                }
            }

            try {
                Message msg = Message.obtain(null, MyLocationService.MSG_REGISTER_CLIENT);//Δημιουργία μηνύματος ώστε ο client να καταγραφεί στην υπηρεσία: MyLocationService 
                msg.replyTo = mMessenger;//Η υπηρεσία θα απαντήσει στον mMessenger του client, για αυτό τον στέλνουμε με το μήνυμα 
                mService.send(msg);//Στέλνει ένα μήνυμα σε αυτόν τον Handler(mService), δηλ. της υπηρεσίας. Το συγκεκριμένο είναι για να ξέρει σε ποιο handler θα απαντάει η υπηρεσία (αφού γίνεται register o client στου mClients της υπηρεσίας).
            }
            catch (RemoteException e) {
                // Σε αυτή την περίπτωση η υπηρεσία έχει καταρρεύσει πριν προλάβουμε να κάνουμε κάτι με αυτήν
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // Αυτή καλείται όταν η σύνδεση με την υπηρεσία έχει αποσυνδεθεί απροσδόκτητα - η διαδικασία κατάρρευσε
            mService = null;//Αφού η service δεν είναι πια bind, κάνουμε τον messeger με τον οποίο στέλναμε μηνύματα στην υπηρεσία null.
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ελέγχει αν ο χρήστης είναι συνδεδεμένος - log in - (από την βάση δεδομένων) - διαφορετικά θα φορτώσει την login activity
        userFunctions = new UserFunctions();
        if(userFunctions.isUserLoggedIn(getApplicationContext())){
            setTitle(R.string.record_path);
            setContentView(R.layout.activity_main);//μόνο αν ο χρήστης είναι συνδεδεμένος φορτώνει το layout

            //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
            Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
            t.setScreenName("Record Path screen");
            t.send(new HitBuilders.AppViewBuilder().build());

            tvLocation = (TextView)findViewById(R.id.tvLocation);
            togbtnStartRoute = (ToggleButton)findViewById(R.id.togbtnStartRoute);
            pbLocationProgress = (ProgressBar)findViewById(R.id.pbLocationProgress);
            btnSubmitPathType = (Button)findViewById(R.id.btnSubmitPathType);
            spinnerPathType = (Spinner)findViewById(R.id.spinnerPathType);
            tvSelectPedestrianType = (TextView)findViewById(R.id.tvSelectPedestrianType);


            //Προσπαθεί να φορτώσει τον χάρτη
            try {
                // Φορτώνει τον χάρτη
                initilizeMap(false);

            } catch (Exception e) {
                e.printStackTrace();
            }
            CheckIfServiceIsRunning();//Αν η MyLocationService τρέχει όταν η activity ξεκινάει, θέλουμε να συνδεθούμε αυτόματα σε αυτή.

        }
        else{//Ο χρήστης δεν είναι συνδεδεμένος-δείξε την οθόνη σύνδεσης
            Intent login = new Intent(getApplicationContext(), LoginActivity.class);
            login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(login);
            // Σταματάει την MainActivity
            finish();
        }
    }

    private void CheckIfServiceIsRunning() {
        //Εάν η υπηρεσία τρέχει όταν η υπηρεσία ξεκινάει, θέλουμε να συνδεθούμε αυτόματα σε αυτήν.
        if (MyLocationService.isRunning()) {//Η isRunning (που την έχουμε υλοποιήσει εμείς) είναι true αν η υπηρεσία τρέχει
            togbtnStartRoute.setChecked(true);//Αφού η υπηρεσία τρέχει, σημαίνει ότι ο χρήστης έχει πατήσει την καταγραφή
            if(MyLocationService.locationIsFixed()){ //Η locationIsFixed (που την έχουμε υλοποιήσει εμείς) είναι true αν η θέση είναι φιξαριμένη
                pbLocationProgress.setVisibility(View.INVISIBLE);//Τότε εξαφανίζουμε την μπάρα προόδου
                tvLocation.setText(getString(R.string.location_is_fixed));
                tvLocation.setVisibility(View.VISIBLE);
                btnSubmitPathType.setVisibility(View.VISIBLE);
                spinnerPathType.setVisibility(View.VISIBLE);
                tvSelectPedestrianType.setVisibility(View.VISIBLE);
            }
            else if(MyLocationService.locationHasFirstFixedEvent() && !MyLocationService.locationIsFixed()){//Η θέση έχει "φιξαριστεί" κάποια στιγμή και δεν είναι "φιξαριμένη"
                pbLocationProgress.setVisibility(View.VISIBLE);//Τότε εμφανίζουμε την μπάρα προόδου
                tvLocation.setText(getString(R.string.location_try_to_fix_again));//Εμφανίζουμε για κείμενο Location try to fix again:
                tvLocation.setVisibility(View.VISIBLE);//Kάνουμε το κείμενο ορατό
            }
            else if(!MyLocationService.locationHasFirstFixedEvent() && !MyLocationService.locationIsFixed()){//Η θέση δεν έχει "φιξαριστεί" κάποια στιγμή και δεν είναι "φιξαριμένη"
                pbLocationProgress.setVisibility(View.VISIBLE);//Τότε εμφανίζουμε την μπάρα προόδου
                tvLocation.setText(getString(R.string.location));//Εμφανίζουμε για κείμενο Location:
                tvLocation.setVisibility(View.VISIBLE);//Kάνουμε το κείμενο ορατό
            }
            firstTimeTheActivityIsBindedToMyLocationService=false;//Αφού η υπηρεσία τρέχει σημαίνει ότι έχει συνδεθεί παλιότερα activity στην υπηρεσία
            doBindService();
        }
    }

    //Συνδέει την activity στην υπηρεσία
    void doBindService() {
        bindService(new Intent(this, MyLocationService.class), mConnection, Context.BIND_AUTO_CREATE);//Εδώ συνδέουμε την υπηρεσία
        mIsBound = true;//για να ξέρουμε αν η υπηρεσία είναι συνδεδεμένη
    }

    void doUnbindService() {
        if (mIsBound) {
            //Αν έχουμε λάβει την υπηρεσία, και έτσι έχουμε εγγραφεί σε αυτή, τώρα είναι η ώρα να απεγγραφούμε.
            if (mService != null) {//Αν η υπηρεσία δεν έχει αποσυνδεθεί από κάποιο απρόσμενο λόγο και έχουμε συνδεθεί σε αυτήν
                try {
                    //Στέλνουμε μήνυμα αποσύνδεσης
                    Message msg = Message.obtain(null, MyLocationService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                }
                catch (RemoteException e) {
                    // Δεν υπάρχει κάτι ιδιαίτερο να κάνουμε αν η υπηρεσία έχει καταρρεύσει
                }
            }
            //Αποσυνδέουμε την υπάρχουσα σύνδεση μας
            unbindService(mConnection);//Εδώ γίνεται η αποσύνδεση
            mIsBound = false;//Για να ξέρουμε ότι η υπηρεσία δεν είναι πια συνδεδεμένη

            tvLocation.setVisibility(View.INVISIBLE);
            tvLocation.setText(getString(R.string.location));
            pbLocationProgress.setVisibility(View.INVISIBLE);//Δεν χρησιμοποιούμε άλλο τον fused provider
            btnSubmitPathType.setVisibility(View.INVISIBLE);//Κάνουμε αόρατο το κουμπί με το οποίο ο χρήστης μπορεί να κάνει tag
            spinnerPathType.setVisibility(View.INVISIBLE);
            tvSelectPedestrianType.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        GoogleAnalytics.getInstance(MainActivity.this).reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        GoogleAnalytics.getInstance(MainActivity.this).reportActivityStop(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MyLocationService.isRunning()) {//Η isRunning (που την έχουμε υλοποιήσει εμείς) είναι true αν η υπηρεσία τρέχει
            togbtnStartRoute.setChecked(true);//Αφού η υπηρεσία τρέχει, σημαίνει ότι ο χρήστης έχει πατήσει την καταγραφή
            if(MyLocationService.locationIsFixed()){ //Η locationIsFixed (που την έχουμε υλοποιήσει εμείς) είναι true αν η θέση είναι φιξαριμένη
                pbLocationProgress.setVisibility(View.INVISIBLE);//Τότε εξαφανίζουμε την μπάρα προόδου
                tvLocation.setText(getString(R.string.location_is_fixed));
                tvLocation.setVisibility(View.VISIBLE);
                btnSubmitPathType.setVisibility(View.VISIBLE);
                spinnerPathType.setVisibility(View.VISIBLE);
                tvSelectPedestrianType.setVisibility(View.VISIBLE);
            }
            else if(MyLocationService.locationHasFirstFixedEvent() && !MyLocationService.locationIsFixed()){//Η θέση έχει "φιξαριστεί" κάποια στιγμή και δεν είναι "φιξαριμένη"
                pbLocationProgress.setVisibility(View.VISIBLE);//Τότε εμφανίζουμε την μπάρα προόδου
                tvLocation.setText(getString(R.string.location_try_to_fix_again));//Εμφανίζουμε για κείμενο Location try to fix again:
                tvLocation.setVisibility(View.VISIBLE);//Kάνουμε το κείμενο ορατό
            }
            else if(!MyLocationService.locationHasFirstFixedEvent() && !MyLocationService.locationIsFixed()){//Η θέση δεν έχει "φιξαριστεί" κάποια στιγμή και δεν είναι "φιξαριμένη"
                pbLocationProgress.setVisibility(View.VISIBLE);//Τότε εμφανίζουμε την μπάρα προόδου
                tvLocation.setText(getString(R.string.location));//Εμφανίζουμε για κείμενο Location:
                tvLocation.setVisibility(View.VISIBLE);//Kάνουμε το κείμενο ορατό
            }
        }

        initilizeMap(true);//Αρχικοποιεί εκ νέου τον χάρτη
        //Αν η υπηρεσία τρέχει και δεν είναι η πρώτη φορά στο onResume μετά την δημιουργία της υπηρεσίας (που αν ήταν, θα έχουμε στείλει ένα μήνυμα στην
        //υπηρεσία για να μας δώσει τις παλιές θέσεις του χρήστη)
        if (MyLocationService.isRunning() && firstTimeOnResumeAfterCreated==false){
            //Ζητάμε από την υπηρεσία τις παλιές θέσεις του χρήστη
            try{
                Message msg = Message.obtain(null, MyLocationService.MSG_REQUEST_POINTS_OF_POLYLINE_AND_TAGS);
                msg.replyTo = mMessenger;
                mService.send(msg);
            }
            catch (RemoteException e) {
                //Η υπηρεσία έχει καταρρεύσει
            }
        }
        firstTimeOnResumeAfterCreated=false;//Η activity έχει μπει ήδη μια φορά στην onResume.
    }

    /*Καλείται όταν ο χρήστης κάνει κλικ στο κουμπί υποβολής είδους διαδρομής*/
    public void onBtnSubmitPathTypeClicked(View view){
        //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
        //Χτίζει και στέλνει το Analytics Event.
        t.send(new HitBuilders.EventBuilder()
                .setCategory(categoryRecordScreenbtId)
                .setAction(actionSubmitId)
                .setLabel(String.valueOf(spinnerPathType.getSelectedItem()))
                .build());

        try{
            //Στέλνουμε ένα μήνυμα ώστε η υπηρεσία να μας γυρίσει την τωρινή θέση του χρήστη (που θα μπει το tag του είδους της διαδρομής)
            Message msg = Message.obtain(null, MyLocationService.MSG_REQUEST_CURRENT_LOCATION_FOR_TAG);
            msg.replyTo = mMessenger;
            Bundle bundle = new Bundle();
            bundle.putString("pathType", String.valueOf(spinnerPathType.getSelectedItem()));
            msg.setData(bundle);
            mService.send(msg);
        }
        catch (RemoteException e) {
            //Η υπηρεσία έχει καταρρεύσει
        }
    }

    /*Καλείται όταν ο χρήστης κάνει κλικ στο κουμπί εναλλαγής:Start Route toggle button */
    public void onTogBtnstartRouteClicked(View view) {

        boolean on = togbtnStartRoute.isChecked();//Αν το κουμπί έχει πατηθεί (isChecked==true), τότε θεωρούμε πως είναι on

        if (on) {//Αν πατηθεί το on (δηλαδή το ξεκίνημα της καταγραφής)
            if (android.os.Build.VERSION.SDK_INT > 9)
            {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                StrictMode.setThreadPolicy(policy);
            }


            //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
            Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
            //Χτίζει και στέλνει το Analytics Event.
            t.send(new HitBuilders.EventBuilder()
                    .setCategory(categoryRecordScreenbtId)
                    .setAction(actionStartStopId)
                    .setLabel(labelStartID)
                    .build());

            //Θα ξεκινήσουμε την υπηρεσία μόνο αν ο provider του gps και του wifi είναι ενεργοποιημένος (αλλιώς για την εφαρμογή μας δεν έχει νόημα η καταγραφή θέσεων)
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            //Αν ο GPS και Network provider είναι ενεργοποιημένος συνδέσου με την υπηρεσία (δηλαδή ξεκίνα την καταγραφή) - αν το wifi ειναι ενεργοποιημένο
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER )){//Ξεκινά την υπηρεσία αν ο gps provider είναι ενεργοποιημένος
                //Μόνο αν το wifi είναι ενεργοποιημένο ξεκινά η υπηρεσία - αλλιώς δεν έχει ιδιαίτερο νόημα η καταγραφή
                WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
                if (wifi.isWifiEnabled()){
                    ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());
                    //Αν δεν υπάρχει δυνατότητα mobile data τότε επιτέπουμε την έναρξη
                    if(mConnectionDetector.hasMobileDatacapability()==false){
                        if(mConnectionDetector.isInternetAvailable()){



                            startService(new Intent(MainActivity.this, MyLocationService.class));//Ξεκινά την υπηρεσία
                            doBindService();//Συνδέεται στην υπηρεσία
                            pbLocationProgress.setVisibility(View.VISIBLE);
                            tvLocation.setVisibility(View.VISIBLE);
                        }
                        else{
                            togbtnStartRoute.setChecked(false);//Ξαναγύρισε το κουμπί στην off κατάσταση, ώστε ο χρήστης να μπορεί να ξεκινήσει μετά την ενεργοποίηση την καταγραφή
                            Toast.makeText(getApplicationContext(), R.string.internet_connection_required,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    else{//Ζητάει να ανοίξουν τα mobile data για να συνεχίσει

                        //------------------------------------------------------------------------------------------------//
                        //κοιτάζει να δει αν τα mobile data επιτρέπονται
                        boolean mobileDataAllowed = Settings.Secure.getInt(getContentResolver(), "mobile_data", 1) == 1;
                        if(mobileDataAllowed){
                            if(mConnectionDetector.isInternetAvailable()){



                                startService(new Intent(MainActivity.this, MyLocationService.class));//Ξεκινά την υπηρεσία
                                doBindService();//Συνδέεται στην υπηρεσία
                                pbLocationProgress.setVisibility(View.VISIBLE);
                                tvLocation.setVisibility(View.VISIBLE);
                            }
                            else{
                                togbtnStartRoute.setChecked(false);//Ξαναγύρισε το κουμπί στην off κατάσταση, ώστε ο χρήστης να μπορεί να ξεκινήσει μετά την ενεργοποίηση την καταγραφή
                                Toast.makeText(getApplicationContext(), R.string.internet_connection_required,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                        else{
                            togbtnStartRoute.setChecked(false);//Ξαναγύρισε το κουμπί στην off κατάσταση, ώστε ο χρήστης να μπορεί να ξεκινήσει μετά την ενεργοποίηση την καταγραφή
                            showMobileDataDisabledToUser();
                        }
                    }
                }
                else{
                    togbtnStartRoute.setChecked(false);//Ξαναγύρισε το κουμπί στην off κατάσταση, ώστε ο χρήστης να μπορεί να ξεκινήσει μετά την ενεργοποίηση την καταγραφή
                    showWiFiDisabledToUser();//Δείξε στον χρήστη μια προειδοποίηση ότι το wifi είναι κλειστό
                }
            }else{
                togbtnStartRoute.setChecked(false);//Ξαναγύρισε το κουμπί στην off κατάσταση, ώστε ο χρήστης να μπορεί να ξεκινήσει μετά την ενεργοποίηση την καταγραφή
                showHighAccuracyDisabledAlertToUser();//Δείξε στον χρήστη μια προειδοποίηση ότι το GPS είναι κλειστό
            }

        }
        else {//Αν πατηθεί το off
            //Στέλνουμε ένα μήνυμα ώστε η υπηρεσία να μας γυρίσει όλες τις θέσεις του χρήστη μέχρι τώρα
            //μόλις αυτές γυρίσουν ο handler θα διαχειριστεί το μήνυμα και στη συνέχεια θα σταματήσει την υπηρεσία
            //και επίσης θα ξεκινήσει μία άλλη Activity στην οποία θα στείλει τις θέσεις του χρήστη, ώστε ο χρήστης να
            //μπορεί να σώσει την διαδρομή του

            //Παίρνει έναν tracker (κάνει αυτο-αναφορά)
            Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
            //Χτίζει και στέλνει το Analytics Event.
            t.send(new HitBuilders.EventBuilder()
                    .setCategory(categoryRecordScreenbtId)
                    .setAction(actionStartStopId)
                    .setLabel(labelStopID)
                    .build());

            try{
                Message msg = Message.obtain(null, MyLocationService.MSG_REQUEST_ALL_LOCATIONS_AND_TOTAL_DISTANCE_BEFORE_STOP);
                msg.replyTo = mMessenger;
                mService.send(msg);
            }
            catch (RemoteException e) {
                //Η υπηρεσία έχει καταρρεύσει
            }
        }
    }

    //Η προειδοποίηση στον χρήστη (ότι πρέπει να ανοίξει τον GPS πάροχο)
    private void showHighAccuracyDisabledAlertToUser(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(getString(R.string.alter_dialog_for_gps))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok_button_of_alter_dialog_for_gps),
                        new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int id){

                                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                                //Χτίζει και στέλνει το Analytics Event.
                                t.send(new HitBuilders.EventBuilder()
                                        .setCategory(categoryHighAccuracyID)
                                        .setAction(actionGoSettingsID)
                                        .build());

                                Intent callGPSSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(callGPSSettingIntent);//Πηγαίνει τον χρήστη στην Activity με τις ρυθμίσεις του GPS, ώστε να μπορεί να το ενεργοποιήσει
                            }
                        });
        alertDialogBuilder.setNegativeButton(getString(R.string.cancel_button_of_alter_dialog_for_gps),
                new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id){

                        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                        //Χτίζει και στέλνει το Analytics Event.
                        t.send(new HitBuilders.EventBuilder()
                                .setCategory(categoryHighAccuracyID)
                                .setAction(actionCancelID)
                                .build());

                        dialog.cancel();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    //Η προειδοποίηση στον χρήστη (ότι πρέπει να ανοίξει το WiFi)
    private void showWiFiDisabledToUser(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(getString(R.string.alter_dialog_for_wifi))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok_button_of_alter_dialog_for_wifi),
                        new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int id){

                                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                                //Χτίζει και στέλνει το Analytics Event.
                                t.send(new HitBuilders.EventBuilder()
                                        .setCategory(categoryMobileDataID)
                                        .setAction(actionGoSettingsID)
                                        .build());

                                Intent callGPSSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_WIFI_SETTINGS);
                                startActivity(callGPSSettingIntent);//Πηγαίνει τον χρήστη στην Activity με τις ρυθμίσεις του WiFi, ώστε να μπορεί να το ενεργοποιήσει
                            }
                        });
        alertDialogBuilder.setNegativeButton(getString(R.string.cancel_button_of_alter_dialog_for_wifi),
                new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id){

                        Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                        //Χτίζει και στέλνει το Analytics Event.
                        t.send(new HitBuilders.EventBuilder()
                                .setCategory(categoryMobileDataID)
                                .setAction(actionCancelID)
                                .build());

                        dialog.cancel();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    //Η προειδοποίηση στον χρήστη (ότι πρέπει να ανοίξει τα MobileData)
    private void showMobileDataDisabledToUser(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(getString(R.string.alter_dialog_for_mobile_data))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok_button_of_alter_dialog_for_mobile_data),
                        new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int id){

                                Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                                //Χτίζει και στέλνει το Analytics Event.
                                t.send(new HitBuilders.EventBuilder()
                                        .setCategory(categoryWiFiID)
                                        .setAction(actionGoSettingsID)
                                        .build());

                                Intent callDataRoamingSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
                                startActivity(callDataRoamingSettingIntent);//Πηγαίνει τον χρήστη στην Activity με τις ρυθμίσεις του WiFi, ώστε να μπορεί να το ενεργοποιήσει
                            }
                        });
        alertDialogBuilder.setNegativeButton(getString(R.string.cancel_button_of_alter_dialog_for_mobile_data),
                new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id){
			            	
			            	/*Tracker t = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
			    			//Χτίζει και στέλνει το Analytics Event.
			    			t.send(new HitBuilders.EventBuilder()
			    			.setCategory(categoryWiFiID)
			    			.setAction(actionCancelID)
			    			.build());*/

                        dialog.cancel();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            doUnbindService();//Αποσυνδεόμαστε από την υπηρεσία
        }
        catch (Throwable t) {
            Log.e("MainActivity", "Failed to unbind from the service", t);
        }
    }

    //* Λειτουργία για να φορτωθεί ο χάρτης. Αν ο χάρτης δεν έχει δημιουργηθεί αυτή θα τον δημιουργήσει.
    private void initilizeMap(boolean fromResume) {
        if (googleMapv2 == null) {
            googleMapv2 = ((MapFragment) getFragmentManager().findFragmentById(
                    R.id.mapv2)).getMap();
            
           /* CameraPosition cameraPosition = new CameraPosition.Builder().target(
                    new LatLng(35.516622, 24.016934)).zoom(16.5f).build();//Για κέντρο βάλαμε μια θέση στα Χανιά*/

            CameraPosition cameraPosition = new CameraPosition.Builder().target(
                    new LatLng(39.622429, 19.924130)).zoom(14.5f).build();//Για κέντρο βάλαμε μια θέση στα Χανιά

            googleMapv2.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            googleMapv2.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            googleMapv2.setMyLocationEnabled(false); // Δεν επιτρέπουμε τον εντοπισμό θέσης από το GoogleMap API
            googleMapv2.getUiSettings().setMyLocationButtonEnabled(false);//Ούτε το κουμπί εντοπισμόύ θέσης




            //Ελέγχει αν ο χάρτης δημιουργήθηκε με επιτυχία η όχι
            if (googleMapv2 == null) {
                Toast.makeText(getApplicationContext(),
                        getString(R.string.unable_to_creat_maps), Toast.LENGTH_SHORT)
                        .show();
            }
        }

        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());

        //Αν υπάρχει σύνδεση internet και χάρτης
        if(mConnectionDetector.isInternetAvailable() && googleMapv2 != null){

            //Εάν η οθόνη δεν εμφανίζεται από Resume τότε κατεβάζει τις διαδρομές των χρηστών από τον server
            if(fromResume == false){
                new DownloadGPXFileAsync(MainActivity.this,googleMapv2).execute(allPathsFileUrl);
            }

            //αλλιώς εμφανίζει τις διαδρομές από το merge.gpx αρχείο αν έχει κατέβει στην συσκευή
            else{
                String myNewFileName = "merge.gpx";
                Context mContext=MainActivity.this.getApplicationContext();
                File mFile = new File (mContext.getFilesDir(), myNewFileName);
                if(mFile.exists()){
                    ParsingGPXForDrawing parsingForDrawing = new ParsingGPXForDrawing(mFile,googleMapv2);

                    parsingForDrawing.decodeGPXForTrksegs();

                    parsingForDrawing.decodeGpxForWpts();
                }
                //αλλιώς κατεβάζει τις διαδρομές των χρηστών από τον server
                else{
                    new DownloadGPXFileAsync(MainActivity.this,googleMapv2).execute(allPathsFileUrl);
                }

            }

        }
        //αν δεν υπάρχει χάρτης ότε σύνδεση internet
        else if (googleMapv2 != null ){
            String myNewFileName = "merge.gpx";
            Context mContext=MainActivity.this.getApplicationContext();
            File mFile = new File (mContext.getFilesDir(), myNewFileName);

            //αν έχει κατέβει το αρχείο με τις διαδρομές τις εμφανίζει
            if(mFile.exists()){
                ParsingGPXForDrawing parsingForDrawing = new ParsingGPXForDrawing(mFile,googleMapv2);

                parsingForDrawing.decodeGPXForTrksegs();

                parsingForDrawing.decodeGpxForWpts();
            }
            //αλλιώς εμφανίζεται κατάλληλο μήνυμα
            else{
                Toast.makeText(getApplicationContext(),
                        getString(R.string.unable_to_load_all_paths), Toast.LENGTH_SHORT)
                        .show();
            }

        }



    }

    //Συνάρτηση για να φορτώσει τις διαδρομές των χρηστών αν τρέχει η υπηρεσία καταγραφής της διαδρομής
    private void loadAllPathsIfServiceRunning(){
        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        ConnectionDetector mConnectionDetector = new ConnectionDetector(getApplicationContext());
        if(mConnectionDetector.isInternetAvailable() && googleMapv2 != null){




            String myNewFileName = "merge.gpx";
            Context mContext=MainActivity.this.getApplicationContext();
            File mFile = new File (mContext.getFilesDir(), myNewFileName);
            if(mFile.exists()){
                ParsingGPXForDrawing parsingForDrawing = new ParsingGPXForDrawing(mFile,googleMapv2);

                parsingForDrawing.decodeGPXForTrksegs();

                parsingForDrawing.decodeGpxForWpts();
            }
            else{
                new DownloadGPXFileAsync(MainActivity.this,googleMapv2).execute(allPathsFileUrl);
            }



        }
        else if (googleMapv2 != null ){
            String myNewFileName = "merge.gpx";
            Context mContext=MainActivity.this.getApplicationContext();
            File mFile = new File (mContext.getFilesDir(), myNewFileName);

            if(mFile.exists()){
                ParsingGPXForDrawing parsingForDrawing = new ParsingGPXForDrawing(mFile,googleMapv2);

                parsingForDrawing.decodeGPXForTrksegs();

                parsingForDrawing.decodeGpxForWpts();
            }

            else{
                Toast.makeText(getApplicationContext(),
                        getString(R.string.unable_to_load_all_paths), Toast.LENGTH_SHORT)
                        .show();
            }

        }

    }


    //Κεντράρει τον χάρτη στην θέση του χρήστη και σχηματίζει μια polyline από τις θέσεις που έχει περάσει ο χρήστης
    private void gotoMyLocation(double lat, double lng){

        if (googleMapv2 != null){

            if(enableNewLocationAddToPolyline=true){//Όταν δηλαδή δεν δημιουργείται ένα polyline με τις παλιές θέσεις του χρήστη
                rectOptions.add(new LatLng(lat, lng));//Προσθέτει την καινούγια θέση του χρήστη
                polyline = googleMapv2.addPolyline(rectOptions);//Και την εμφανίζει εδώ
            }

            CameraPosition cameraPosition = new CameraPosition.Builder().target(
                    new LatLng(lat, lng)).zoom(16.5f).build();

            googleMapv2.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));//Κεντράρει τον χάρτη στη νέα θέση του χρήστη

        }
    }

    //Προσθέει το tag που έβαλε ο χρήστης
    private void addMarkerToMap(double lat, double lng, String pathtype){
        if (googleMapv2 != null){
            googleMapv2.addMarker(new MarkerOptions()
                    .position(new LatLng(lat, lng))
                    .title(pathtype)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        }
    }

    //Εμφανίζει ένα διάλογο λάθους κατά την αποτυχή προσπάθεια σύνδεσης της MyLocationService στην google play service
    private void DisplayErrorDialog(int resultCode){
        // Εμφανίζει τον διάλογο λάθους
        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
        if (dialog != null) {
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();
            errorFragment.setDialog(dialog);
            errorFragment.show(getSupportFragmentManager(), getString(R.string.app_name));
        }
    }

    //Ορίζει ένα DialogFragment για να εμφανίχει τον διάλογο λάθους που δημιουργείται στην showErrorDialog.
    public static class ErrorDialogFragment extends DialogFragment {

        // "Γενικό" (Global) πεδίο που περιέχει τον διάλογο λάθους
        private Dialog mDialog;


        // Προεπιλεγμένος κατασκευαστής. Θέτει τον διάλογο του πεδίου σε null.
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        // Θέτει το διαλόγο για να εμφανισεί.  @param dialog Ένας διάλογος λάθους
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }


        // Η μέθοδος αυτή πρέπει να επιστρέψει έναν Dialog στο DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Διόγκωση (Inflate) του μενού. Προσθέτει στοιχεία στην γραμμή ενεργειών (action bar) αν υπάρχει.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Χειρίζεται τα κλικ του χρήστη στο μενού.
        int id = item.getItemId();

        switch (id) {
			/*case R.id.action_settings:
				return true;*/
            case R.id.action_menu:

                Tracker t1 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t1.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRecordMenuID)
                        .setAction(actionMenuChoiseID)
                        .build());

                return true;
            case R.id.submenu_show_maps:

                Tracker t2 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t2.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRecordMenuID)
                        .setAction(actionShowMapsID)
                        .build());

                Intent mapsIntent= new Intent(MainActivity.this,MapsActivity.class);
                mapsIntent.putExtra("mapsID",2);
                startActivity(mapsIntent);

                return true;

            case R.id.submenu_review_paths:

                Tracker t3 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t3.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRecordMenuID)
                        .setAction(actionMakeReviewID)
                        .build());

                Intent intent= new Intent(MainActivity.this,ReviewPathActivity.class);
                startActivity(intent);
                return true;
            case R.id.submenu_rank__list_of_players:

                Tracker t4 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t4.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRecordMenuID)
                        .setAction(actionRankingID)
                        .build());

                Intent intent2= new Intent(MainActivity.this,RankListOfPlayersActivity.class);
                startActivity(intent2);
                return true;
            case R.id.submenu_log_out:

                Tracker t5 = ((GoogleAnalyticsApp) getApplication()).getTracker(GoogleAnalyticsApp.TrackerName.APP_TRACKER);
                //Χτίζει και στέλνει το Analytics Event.
                t5.send(new HitBuilders.EventBuilder()
                        .setCategory(categoryRecordMenuID)
                        .setAction(actionLogOutID)
                        .build());

                userFunctions.logoutUser(getApplicationContext());
                if (MyLocationService.isRunning()){
                    doUnbindService();//Ξεσυνδέεται από την υπηρεσία (αν είναι συνδεδεμένη)
                    //Σταματάει την υπηρεσία (αν τρέχει)
                    try{
                        stopService(new Intent(MainActivity.this, MyLocationService.class));//Σταματάει την υπηρεσία
                    }
                    catch(Throwable thr){
                        Log.e("MainActivity", "Failed to stop the service", thr);
                    }

                }
                Intent login = new Intent(getApplicationContext(), LoginActivity.class);
                login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(login);
                // Σταματάει την MainActivity
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
