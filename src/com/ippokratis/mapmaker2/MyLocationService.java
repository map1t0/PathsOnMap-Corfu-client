package com.ippokratis.mapmaker2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;


/**********************************************************************************************************************
 * Η υπηρεσία που καταγράφει την διαδρομή των χρηστών σε δύο αρχεία gpx στο ένα μόνο με την βοήθεια του gps provider  *
 * και στο άλλο μόνο με την βοήθεια του fused provider.                                                               *
 * ********************************************************************************************************************/
public class MyLocationService extends Service implements GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener{

    Context c;//Το πλαίσιο (θα το χρησιμοποιήσουμε για να πάρουμε τον φάκελο που θα αποθηκευτούν τα αρχεία
    File segmentsOfTrackPointsFile;//Το αρχείο που θα αποθηκευτoύν τα trackpoints τμήματα της διαδρομής
    File segmentsOfWayPointsFile; //Το αρχείο που θα αποθηκεύει τα waypoints τμήματα της διαδρομής

    File segmentsOfTrackPointsFileGoogle;//Το αρχείο που θα αποθηκευτoύν τα trackpoints τμήματα της διαδρομής με το google play location service
    File segmentsOfWayPointsFileGoogle; //Το αρχείο που θα αποθηκεύει τα waypoints τμήματα της διαδρομής με το google play location service


    // Milliseconds ανά δευτερόλεπτο
    private static final int MILLISECONDS_PER_SECOND = 1000;
    //Συχνότητα ενημέρωσης σε δευτερόλεπτα
    public static final int UPDATE_INTERVAL_IN_SECONDS = 8;
    // Συχνότητα ενημέρωσης σε milliseconds
    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    // Η γρηγορότερη συχνότητα ενημέρωσης σε δευτερόλεπτα
    private static final int FASTEST_INTERVAL_IN_SECONDS = 5;
    //Ανώτατο όριο ενημέρωσης σε milliseconds
    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;
    LocationRequest mLocationRequest;//Αιτείται τις θέσεις του χρήστη
    LocationClient mLocationClient;// Ο πελάτης (client) θέσης

    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",java.util.Locale.getDefault());//Το format του χρόνου

    LatLng  bestCoordinatesOfLocation;//Οι καλύτερες συντεταγμένες (για κάθε 2 θέσεις)
    float bestCoordinatesAccuracy;//Η καλύτερη ακρίβεια θέσης (για κάθε 2 θέσεις)

    int counterForUILocations=0;
    ArrayList<LatLng> coordinatesOfLocationsList = new ArrayList<LatLng>();

    boolean numberOfLocationsGreaterThanZero=false;//Αν έχει βρεθεί έστω μία θέση
    //------------------------------------------------------------------------------------------
    boolean userHasGoneOutOfRegion=false;
    private static String town = "Corfu";
    //------------------------------------------------------------------------------------------

    ArrayList<LatLng> coordinatesOfTagLocationsList = new ArrayList<LatLng>();

    ArrayList<String> pathTypeArrayList= new ArrayList<String>();//Μία παράλληλη λίστα με την από επάνω (taglistLoc) που περιέχει τα είδη της διαδρομής

    Location mCurrentLocation; //Η τρέχουσα θέση του χρήστη

    Location mCurrentLocationGoogle;//Η τρέχουσα θέση του χρήστη από την Google play location service

    private float totalDistance=0;//Αθροιστής που μετράει την συνολική απόσταση που διένυσε ο χρήστης σε μέτρα

    WakeLock wakeLock;//Θα χρησιμοποιηθεί για να κλειδώσει τον επεξεργαστή όταν η υπηρεσία τρέχει, ώστε να μπορούμε να παίρνουμε συνέχεια τις νέες θέσεις του χρήστη

    private static boolean isRunning = false;//Στην αρχή η υπηρεσία δεν "τρέχει"
    private static boolean fixed=false;//Αν υπάρχει "φιξάρισμα" Location
    private static boolean locationHasFirstFixedEvent=false;//Αν έχει υπάρξει πρώτο "φιξάρισα"

    private static boolean fixedGPS=false;//Αν υπάρχει "φιξάρισμα" GPS
    private static boolean gpsHasFirstFixedEvent=false;//Αν έχει υπάρξει πρώτο "φιξάρισα" GPS

    boolean newGpxFromGpsOnly = false;//Για να ξέρουμε αν θα ανεβάσουμε το gpx file που δημιουργείται από το gps
    // Παρακολουθεί όλους τους τρέχοντες εγγεγραμμένους πελάτες. Στην περίπτωση μας είναι μόνο ένας (την φορά), αλλά το βάλαμε προς χάρη γενίκευσης (και πιθανής μελλοντικής επέκτασης)
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    Messenger mClient;//Ο messenger που στέλνονται τα μηνύματα στον πελάτη

    static final int MSG_REGISTER_CLIENT = 1;//Μήνυμα για να εγγραφεί ο πελάτης (από τον πελάτη στην υπηρεσία)
    static final int MSG_UNREGISTER_CLIENT = 2;//Μήνυμα για να απεγγραφεί ο πελάτης (από τον πελάτη στην υπηρεσία)
    static final int MSG_SET_LAST_LOCATION = 3;//Μήνυμα με την τελευταία θέση του χρήστη (από την υπηρεσία στον πελάτη)
    static final int MSG_SET_LOCATION_LOST=4;
    static final int MSG_SET_LOCATION_FIXED=5;//Μήνυμα ότι η πρώτη σωστή θέση του χρήστη βρέθηκε (από την υπηρεσία στον πελάτη)
    static final int MSG_REQUEST_POINTS_OF_POLYLINE_AND_TAGS=6;//Μήνυμα που ζητάει τις θέσεις του χρήστη μέχρι τώρα (από τον πελάτη στην υπηρεσία)
    static final int MSG_SEND_POINTS_OF_POLYLINE_AND_TAGS=7;//Μήνυμα που στέλνει τις θέσεις του χρήστη μέχρι τώρα (από την υπηρεσία στον πελάτη)
    static final int MSG_REQUEST_ALL_LOCATIONS_AND_TOTAL_DISTANCE_BEFORE_STOP=8;//Μήνυμα που ζητάει όλες τις θέσεις του χρήστη μέχρι τώρα, για τελευταία φορά
    static final int MSG_SEND_ALL_LOCATIONS_AND_TOTAL_DISTANCE_BEFORE_STOP=9;
    static final int MSG_REQUEST_CURRENT_LOCATION_FOR_TAG=10;
    static final int MSG_SEND_CURRENT_TAG_LOCATION=11;
    static final int MSG_GOOGLE_PLAY_SERVICE_RESULT_CODE=14;//Μήνυμα με το κώδικα λάθους κατά την σύνδεση της google play service (από την υπηρεσία στον πελάτη)
    static final int MSG_SEND_OUT_OF_REGION=15;

    public static int Satellites = -1;//Οι δορυφόροι που χρησιμοποιεί κάθε στιγμή το GPS για "φιξάρισμα"

    private long mLastLocationMillis; //Ο χρόνος που βρέθηκε η τελευταία τοποθεσία
    private Location mLastLocation;//Η τελευταία τοποθεσία (που θα χρησιμοποιηθεί για να δούμε πόση ώρα έχει κάνει μέχρι την αλλαγή της κατάστασης του GPS)

    LocationManager locationManager;//Ο Manager για την εύρεση της τοποθεσίας

    //"Ακούει" για αλλαγές της κατάστασης του GPS
    GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            Log("In onGpsStatusChanged event: " + event);//Καταγράφει στο log ποιο γεγονός πυροδότησε την αλλαγή της κατάστασης

            if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS || event == GpsStatus.GPS_EVENT_FIRST_FIX) {
                GpsStatus status = locationManager.getGpsStatus(null);
                Iterable<GpsSatellite> sats = status.getSatellites(); //γυρίζει τους τρέχοντες δορυφόρους που βλέπει η μηχανή GPS
                // Ελέγχει τον αριθμό των δορυφόρων στη λίστα για να προσδιορίσει την fix κατάσταση fix
                Satellites = 0;
                for (GpsSatellite sat : sats) {//για όλους τους δορυφόρους που βλέπει το GPS
                    if(sat.usedInFix()){//μετράει τον δορυφόρο μόνο αν χρησιμοποιείται για τον καθοριμό του GPX fix
                        Satellites++;
                    }
                }
                Log("Setting Satellites from GpsStatusListener: " + Satellites);//Καταγράφει στο log τον αριθμό των δορυφόρων που χρησιμοποιούνται για το fix
            }
            //Αυτά θα τα χρησιμοποιούσαμε αν θέλουμε να δούμε αν το gps είναι φιξαρισμένο. Τελικά αποφασίσαμε να χρησιμοποιήσουμε το Fused Location provider σαν κύριο,
            //επομένως μας ενδιαφέρει μόνο αν έχει βρεθεί μια αρχική "καλή" θέση
            switch (event) {
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    if (mLastLocation != null)
                        fixedGPS = (SystemClock.elapsedRealtime() - mLastLocationMillis) < 3000;//Αν έχουν περάσει πάνω από τρία δευτερόλεπτα από την τελευταία διεύθυνση, τότε το GPS δεν είναι "φιξαρισμένο"

                    if (fixedGPS) { //Έχει αποκτηθεί "φιξάρισμα"
                        fixedGPS = true;
                        //sendLocationFixedToUI(MSG_SET_GPS_FIXED);//Ενημερώνει το UI
                    } else { // To "φιξάρισμα" έχει χαθεί

                        fixedGPS = false;

                        //Αν μας ενδιέφερομασταν να αλλάξουμε το UI ανάλογα με το φιξάρισμα του GPS
                    	/*if (gpsHasFirstFixedEvent){
                    		//sendLocationFixedToUI(MSG_SET_GPS_LOST);//Ενημερώνει το UI
                    	}*/
                    }

                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:

                    fixedGPS = true; // Δηλώνει ότι το GPS είναι "φιξαρισμένο"
                    gpsHasFirstFixedEvent=true;
                    //sendLocationFixedToUI(MSG_SET_GPS_FIXED);//Ενημερώνει το UI ότι το GPS είναι "φιξαρισμένο" για πρώτη φορά

                    break;
            }
        }
    };


    //"Ακούει" για αλλαγές θέσης. Χρησιμοποίησα όλη την διαδρομή για να μην έχω collides με την com.google.android.gms.location.LocationListener
    android.location.LocationListener locationListener = new android.location.LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Καλείται όταν βρεθεί μια καινούργια διεύθυνση
            if (location == null) return;//Αν δεν έχει βρεθεί νέα διεύθυνση δεν κάνει τίποτα

            newGpxFromGpsOnly = true;//Θα δημιουργηθεί καινούργιο gpx αρχείο από gps

            try
            {
                Log("Location changed! Speed = " + location.getSpeed() + " & Satellites = " + location.getExtras().getInt("satellites"));
                //boolean isBetter = isBetterLocation(location, Location);//Τα σχολιασμένα είναι για την περίπτωση που είχαμε και άλλους παρόχους διεύθυνσης
                //if(isBetter)
                // {
                Log("Set to new location");
                //Location = location;
                Satellites = location.getExtras().getInt("satellites");//Ο αριθμός των δορυφόρων που χρησιμοποιούνται για την εξαγωγή της τοποθεσίας
                Log("Setting Satellites from LocationListener: " + Satellites);

                //}
            }
            catch(Exception exc)
            {
                Log(exc.getMessage());
            }
              /*Θα το χρησιμοποιούσαμε αν ο κύριος provider ήταν ο gps provider και άρα το UI ενημερωνόταν από αυτόν
                //Το γεωγραφικό πλάτος, μήκος και η ακρίβεια της θέσης που βρέθηκε
        			Double lat =  location.getLatitude();
        			Double lng =  location.getLongitude();
        			float accur = location.getAccuracy();
        			
        			//Βάζουμε κάθε 10 εντοπισμούς θέσεις στην ArrayList για να μην γεμίσει η μνήμη
        			counterForUILocations=counterForUILocations+1;
        			
        			
        			numberOfLocationsGreaterThanZero=true;//Έχουν βρεθεί τουλάχιστον μία θέση
        			
        			//Στο UI στέλνει την καλύτερη θέση που έχει εντοπιστεί (όταν έχει βρει 10 θέσεις)
        			if(counterForUILocations==11 || counterForUILocations==1){//Αν είναι η 11η έχουν βρεθεί προηγουμένως 10, άρα ξαναμετράει από την αρχή
        				counterForUILocations=1;
        				//Θεωρεί την πρώτη θέση σαν την καλύτερη
        				bestCoordinatesOfLocation=new LatLng(lat,lng );
        				bestCoordinatesAccuracy=accur;
        			}
        			
        			//Βάζουμε αυτήν που έχει την καλύτερη ακρίβεια από τις 10
        			if(counterForUILocations>1 && accur <=bestCoordinatesAccuracy){
        				bestCoordinatesOfLocation=new LatLng(lat,lng );
        				bestCoordinatesAccuracy=accur;
        			}
        			
        			if(counterForUILocations==10){//Αν είναι η 10η θέση βάζει την θέση με την καλύτερη ακρίβεια στο UI
        				
        				coordinatesOfLocationsList.add(bestCoordinatesOfLocation);
        				
        				sendLastLocationToUI(bestCoordinatesOfLocation.latitude,bestCoordinatesOfLocation.longitude);//Στέλνουμε τη νέα καλύτερη θέση στην Activity
        				if (coordinatesOfLocationsList.size()>=2){//Μετράει την απόσταση που έχει διανυθεί κάθε 10 θέσεις (ουσιαστικά βγάζει και τις πρώτες 10 θέσεις άρα το GPS έχει προλάβει να βρει με σχετική ακρίβεια την πρώτη θέση του χρήστη
            				totalDistance = totalDistance + distance (coordinatesOfLocationsList.get(coordinatesOfLocationsList.size()-2).latitude, coordinatesOfLocationsList.get(coordinatesOfLocationsList.size()-2).longitude,  lat,  lng);
            			}
        			
        			
        			}*/

            //Καταγράφει τα trackpoints στον στο gpx αρχείο που δημιουργείται από τον gps provider
            String segment = "<trkpt lat=\"" + location.getLatitude() + "\" lon=\"" + location.getLongitude() + "\"><time>" + df.format(new Date(location.getTime())) + "</time>"
                    + "<sat>" + Satellites +"</sat>"+ "<hdop>" +location.getAccuracy() +"</hdop>"+"</trkpt>\n";

            try {
                FileOutputStream fOut = openFileOutput(segmentsOfTrackPointsFile.getName(),
                        MODE_APPEND);
                OutputStreamWriter osw = new OutputStreamWriter(fOut);
                osw.write(segment);
                osw.flush();
                osw.close();

            } catch (FileNotFoundException e) {

                e.printStackTrace();
            } catch (IOException e) {

                e.printStackTrace();
            }

            mLastLocationMillis = SystemClock.elapsedRealtime();

        }
        public void onStatusChanged(String provider, int status, Bundle extras) {}
        public void onProviderEnabled(String provider) {}
        public void onProviderDisabled(String provider) {}
    };


    /*----------------------------------------------------------------------------------------------------------------------------------------------*/
    //Τα παρακάτω είναι για την περίπτωση που είχαμε και άλλες θέσεις
 /*  protected static boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // Μια νέα θέση είναι πάντα καλύτερη από καμία θέση
            return true;
        }

        // 'Ελεγξε αν η νέα τοποθεσία είναι νεότερη ή παλιότερη
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // Εάν έχουν περάσει περισσότερα από δύο λεπτά από την τρέχουσα θέση, χρησιμοποιήσε τη νέα θέση, επειδή ο χρήστης έχει πιθανότατα μετακινηθεί
        if (isSignificantlyNewer) {
            return true;
        // Αν η νέα τοποθεσία είναι περισσότερο από δύο λεπτά παλιότερη, πρέπει να είναι χειρότερη
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Έλεγξε αν η νέα τοποθεσία είναι πιο ακριβής ή όχι
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 5;//Στην εφαρμογή μας θέλουμε μεγάλη ακρίβεια...

        // Έλεγξε αν η παλιά και η νέα τοποθεσία είναι από τον ίδιο πάροχο
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        //Καθόρισε την ποιότητα της θέσης, χρησιμοποιώντας ένα συνδυασμό επικαιρότητας και την ακρίβεια
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Έλεγξε αν οι δύο πάροχοι είναι οι ίδιοι */
   /* private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
          return provider2 == null;
        }
        return provider1.equals(provider2);
    }*/
/*------------------------------------------------------------------------------------------------------------*/
    //Συνάρτηση για να γράφει μηνύματα στο αρχείο Log
    private static void Log(String message) {
        if(message != null && message.length() > 0) {
            Log.i("LCAT", message);

        }
    }


    //Ο messenger που ορίζεται από την υπηρεσία ώστε οι πελάτες να μπορούν να στέλνουν μηνύματα.
    Handler mIncomingHandler = new Handler(new IncomingHandlerCallback());
    final Messenger mMessenger = new Messenger(mIncomingHandler);

    //Ο Handler που χειρίζεται τα μηνύματα που στέλνουν οι πελάτες
    class IncomingHandlerCallback implements Handler.Callback {

        @Override
        public boolean  handleMessage(Message msg) {//Η υπηρεσία χειρίζεται τα message (από την service: MyLocationService) στην handle message μέθοδο
            switch (msg.what) {
                case MSG_REGISTER_CLIENT://Ο πελάτης στέλνει μήνυμα με τον messenger του και αιτείται εγγραφή
                    mClients.add(msg.replyTo);//Προσθέτει τον messenger του client στον οποίο θα απαντάει η υπηρεσία
                    break;
                case MSG_UNREGISTER_CLIENT://Ο πελάτης στέλνει μήνυμα με τον messenger του και αιτείται απεγγραφή
                    mClients.remove(msg.replyTo);//Αφαιρεί τον messenger του client στον οποίο απαντούσε η υπηρεσία
                    break;
                case MSG_REQUEST_POINTS_OF_POLYLINE_AND_TAGS://Ο πελάτης αιτείται τις θέσεις του χρήστη (και τα tags)
                    mClient = msg.replyTo;//Ο messenger του πελάτη που αιτήθηκε το μήνυμα
                    if(coordinatesOfLocationsList.size() > 1){//Η υπηρεσία στέλνει τις θέσεις
                        sendArrayListLocationToUI(coordinatesOfLocationsList,pathTypeArrayList,coordinatesOfTagLocationsList,totalDistance,mClient);
                    }
                    break;
                case MSG_REQUEST_ALL_LOCATIONS_AND_TOTAL_DISTANCE_BEFORE_STOP://Ο πελάτης αιτείται τις θέσεις του χρήστη (και τα tags) για τελευταία φορά
                    mClient = msg.replyTo;//Ο messenger του πελάτη που αιτήθηκε το μήνυμα

                    sendNumberOfTagsAndDistanceToUI(numberOfLocationsGreaterThanZero,coordinatesOfTagLocationsList.size(),totalDistance,mClient,userHasGoneOutOfRegion);

                    break;
                case MSG_REQUEST_CURRENT_LOCATION_FOR_TAG://Ο πελάτης αιτείται την τρέχουσα θέση του χρήστη (για το tag)
                    mClient = msg.replyTo;//Ο messenger του πελάτη που αιτήθηκε το μήνυμα
                    String pathType = msg.getData().getString("pathType");
                    pathTypeArrayList.add(pathType);//Η λίστα με το είδη της διαδρομής (Παράλληλη λίστα με την taglistLoc)
                    mCurrentLocation =locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);//Αν ο κύριος πάροχος θέσης είναι ο gps
                    //Αν χρησιμοπιούσαμε τον gps provider
	        	  /*double currentLatitude = mCurrentLocation.getLatitude();
	        	   double currentLongitude = mCurrentLocation.getLongitude();
	        	   
	        	   
	        	   /*coordinatesOfTagLocationsList.add(new LatLng(currentLatitude,currentLongitude));//Η λίστα με τις τοποθεσίες που έχουν ετικέτες (Παράλληλη λίστα με την pathTypeArrayList)
	        	   
	        	   try{
	        		   Bundle bundle = new Bundle();
	        		   bundle.putDouble("currentLatitude", currentLatitude);
	        		   bundle.putDouble("currentLongitude", currentLongitude);
	        		   bundle.putString("pathType",pathType);
	        		   Message msg2 = Message.obtain(null, MSG_SEND_CURRENT_TAG_LOCATION);
	        		   msg2.setData(bundle);
	        		   mClient.send(msg2);
	        	   }
	       			catch(RemoteException e){
	       				//Ο πελάτης έχει καταρρεύσει
	       			}*/

                    //Θα βάλουμε tag στο αρχείο gpx που δημιουργεί το GPS μόνο αν έχουμε φιξάρισμα GPS αλλιώς μας είναι άχρηστο και λάθος σημείο
                    if(fixedGPS && gpsHasFirstFixedEvent){

                        String segmentOfWaypoint = "<wpt lat=\"" + mCurrentLocation.getLatitude() + "\" lon=\"" + mCurrentLocation.getLongitude() + "\"><time>" + df.format(new Date(mCurrentLocation.getTime())) + "</time>"
                                + "<name>" + pathType +"</name>"+ "<sat>" + Satellites +"</sat>"+ "<hdop>" +mCurrentLocation.getAccuracy() +"</hdop>"+ "</wpt>\n";


                        try {
                            FileOutputStream fOut = openFileOutput(segmentsOfWayPointsFile.getName(),
                                    MODE_APPEND);
                            OutputStreamWriter osw = new OutputStreamWriter(fOut);
                            osw.write(segmentOfWaypoint);
                            osw.flush();
                            osw.close();

                        } catch (FileNotFoundException e) {

                            e.printStackTrace();
                        } catch (IOException e) {

                            e.printStackTrace();
                        }
                    }

                    //Ενημέρωση του UI και του gpx αρχείου από τον fused provider για τα waypoints
                    mCurrentLocationGoogle = mLocationClient.getLastLocation();//Η τρέχουσα θέση από την google play service
                    //Αφού στο UI θα χρησιμοποιήσουμε τον fused provider
                    double currentLatitude = mCurrentLocationGoogle.getLatitude();
                    double currentLongitude = mCurrentLocationGoogle.getLongitude();

                    coordinatesOfTagLocationsList.add(new LatLng(currentLatitude,currentLongitude));//Η λίστα με τις τοποθεσίες που έχουν ετικέτες (Παράλληλη λίστα με την pathTypeArrayList)
                    try{
                        Bundle bundle = new Bundle();
                        bundle.putDouble("currentLatitude", currentLatitude);
                        bundle.putDouble("currentLongitude", currentLongitude);
                        bundle.putString("pathType",pathType);
                        Message msg2 = Message.obtain(null, MSG_SEND_CURRENT_TAG_LOCATION);
                        msg2.setData(bundle);
                        mClient.send(msg2);
                    }
                    catch(RemoteException e){
                        //Ο πελάτης έχει καταρρεύσει
                    }


                    String segmentOfWaypointGoogle = "<wpt lat=\"" + mCurrentLocationGoogle.getLatitude() + "\" lon=\"" + mCurrentLocationGoogle.getLongitude() + "\"><time>" + df.format(new Date(mCurrentLocationGoogle.getTime())) + "</time>"
                            + "<name>" + pathType +"</name>" + "<hdop>" +mCurrentLocationGoogle.getAccuracy() +"</hdop>"+ "</wpt>\n";

                    try {
                        FileOutputStream fOut = openFileOutput(segmentsOfWayPointsFileGoogle.getName(),
                                MODE_APPEND);
                        OutputStreamWriter osw = new OutputStreamWriter(fOut);
                        osw.write(segmentOfWaypointGoogle);
                        osw.flush();
                        osw.close();

                    } catch (FileNotFoundException e) {

                        e.printStackTrace();
                    } catch (IOException e) {

                        e.printStackTrace();
                    }

                    break;
                default:
                    //Δεν κάνει τίποτα
            }
            return true;//Δηλώνει ότι η handleMessage χειρίστηκε το μήνυμα
        }
    }

    //Στέλνει ότι η πρώτη ακριβής θέση του χρήστη βρέθηκε
    private void sendLocationFixedToUI(int msgCode){

        for (int i=mClients.size()-1; i>=0; i--) {//Στέλνει το μήνυμα σε όλους του εγγεγραμένους πελάτες
            try {
                Message msg = Message.obtain(null, msgCode);
                mClients.get(i).send(msg);// Ο mClients.get(i) είναι ο Messenger που θα στείλει το μήνυμα
            }
            catch (RemoteException e) {
                // Ο πελάτης έχει "πεθάνει". Τον βγάζουμε από την λίστα. Περνάμε την λίστα από το τέλος προς την αρχή επομένως είναι ασφαλές να το κάνουμε μέσα στο βρόχο. 
                mClients.remove(i);
            }
        }
    }

    //Στέλνει αν έχουν εντοπιστεί θέσεις, τα tags και την απόσταση στην activity του UI
    private void sendNumberOfTagsAndDistanceToUI(boolean numberOfLocationsDifferentZero, int numberOfTags,Float distance, Messenger mClient,boolean hasGoneOut){
        try{
            Bundle bundle = new Bundle();
            bundle.putBoolean("numberOfLocationsDifferentZero",numberOfLocationsDifferentZero);
            bundle.putInt("numberOfTags", numberOfTags);
            bundle.putFloat("totalDistance", distance);
            bundle.putBoolean("hasGoneOutOfTown", hasGoneOut);
            bundle.putString("town", town);
            Message msg = Message.obtain(null, MSG_SEND_ALL_LOCATIONS_AND_TOTAL_DISTANCE_BEFORE_STOP);
            msg.setData(bundle);
            mClient.send(msg);
        }
        catch(RemoteException e){
            //Ο πελάτης έχει καταρρεύσει
        }
    }


    //Στέλνουμε την λίστα με τις θέσεις του χρήστη (μόνο στον πελάτη που την αιτήθηκε)-Το msgSendCode δείχνει αν είναι η τελευταί φορά που αιτείται ο πελάτης τις θέσεις
    //ή την αιτείται επειδή μπήκε σε resume ή στην create(). Επίσης, στέλνει όλες τις θέσεις που έχουν ετικέτα και τις ετικέτες
    private void sendArrayListLocationToUI(ArrayList <LatLng> listWithCoordinates,ArrayList<String> pathTypeArrayList,ArrayList<LatLng> listWithTagLocations,Float distance, Messenger mClient){
        try{
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("pathTypeArrayList", pathTypeArrayList);
            bundle.putParcelableArrayList("tagLocationArrayList", listWithTagLocations);
            bundle.putFloat("totalDistance", distance);
            bundle.putParcelableArrayList("coordinatesArrayList",listWithCoordinates);
            Message msg = Message.obtain(null, MSG_SEND_POINTS_OF_POLYLINE_AND_TAGS);
            msg.setData(bundle);
            mClient.send(msg);
        }
        catch(RemoteException e){
            //Ο πελάτης έχει καταρρεύσει
        }
    }


    //Στέλνει την τελευταία θέση του χρήστη
    private void sendLastLocationToUI(double latitude,double longitude) {
        for (int i=mClients.size()-1; i>=0; i--) {//Θα στείλει τιμές σε όλους τους client που έχουν συνδεθεί (αρχίζοντας από τον τελευταίο που συνδέθηκε).
            try {
                Bundle bundle = new Bundle();
                bundle.putDouble("latitude", latitude);
                bundle.putDouble("longitude", longitude);
                Message msg = Message.obtain(null, MSG_SET_LAST_LOCATION);
                msg.setData(bundle);
                mClients.get(i).send(msg);// Ο mClients.get(i) είναι ο Messenger που θα στείλει το μήνυμα
            }
            catch (RemoteException e) {
                // Ο πελάτης έχει "πεθάνει". Τον βγάζουμε από την λίστα. Περνάμε την λίστα από το τέλος προς την αρχή επομένως είναι ασφαλές να το κάνουμε μέσα στο βρόχο. 
                mClients.remove(i);
            }
        }
    }

    //-------------------------------------------------------------------------------------------------------------------------------------------------------
    //Στέλνει την τελευταία θέση του χρήστη
    private void sendOutOfRegionToUI(String town) {
        for (int i=mClients.size()-1; i>=0; i--) {//Θα στείλει τιμές σε όλους τους client που έχουν συνδεθεί (αρχίζοντας από τον τελευταίο που συνδέθηκε).
            try {
                Bundle bundle = new Bundle();
                //bundle.putDouble("latitude", latitude);
                bundle.putString("town",town );
                Message msg = Message.obtain(null, MSG_SEND_OUT_OF_REGION);
                msg.setData(bundle);
                mClients.get(i).send(msg);// Ο mClients.get(i) είναι ο Messenger που θα στείλει το μήνυμα
            }
            catch (RemoteException e) {
                // Ο πελάτης έχει "πεθάνει". Τον βγάζουμε από την λίστα. Περνάμε την λίστα από το τέλος προς την αρχή επομένως είναι ασφαλές να το κάνουμε μέσα στο βρόχο.
                mClients.remove(i);
            }
        }
    }
    //----------------------------------------------------------------------------------------------------------------------------------------------------------

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();//Όταν ο πελάτης συνδέεται στην υπηρεσία, η υπηρεσία γυρίζει μια διεπαφή με τον messenger της ώστε ο πελάτης να στέλνει μηνύματα στην υπηρεσία.
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("MyLocationService", "Service Started.");
        showNotification();//Δείχνει μια ειδοποίηση στον χρήστη ότι η υπηρεσία άρχισε
        isRunning = true;//Η υπηρεσία τρέχει

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);//Προσθέτει τον location manager

        c = getApplicationContext();
        //Τα ονόματα των αρχείων που θα αποθηκεύονται τα trackpoints και waypoints από τον gpx provider
        segmentsOfTrackPointsFile = new File(c.getFilesDir(), "segmentOfTrkpt.txt");//Το όνομα του αρχείου που θα αποθηκευτούν τα trackpoints της διαδρομής
        segmentsOfWayPointsFile = new File(c.getFilesDir(),"segmentOfWpt.txt");

        //Τα ονόματα των αρχείων που θα αποθηκεύονται τα trackpoints και waypoints από τον fused provider
        segmentsOfTrackPointsFileGoogle = new File(c.getFilesDir(), "segmentOfTrkptGoogle.txt");//Το όνομα του αρχείου που θα αποθηκευτούν τα trackpoints της διαδρομής από την google location
        segmentsOfWayPointsFileGoogle = new File(c.getFilesDir(),"segmentOfWptGoogle.txt");

        //Αν είναι η πρώτη φορά που τρέχουμε το πρόγραμμα, δημιουργούμε τα αρχεία, αλλιώς αν το αρχεία περιέχουν δεδομένα από μια παλιότερη διαδρομή, τα "καθαρίζουμε"
        //Τα αρχεία που παίρνουν τιμές από τον gps provider
        String string1 = "";
        FileWriter fWriter;
        try{
            fWriter = new FileWriter(segmentsOfTrackPointsFile);
            fWriter.write(string1);
            fWriter.flush();
            fWriter.close();
        }
        catch (Exception e) {
            e.printStackTrace();}

        String string2 = "";
        FileWriter fWriter2;
        try{
            fWriter2 = new FileWriter(segmentsOfWayPointsFile);
            fWriter2.write(string2);
            fWriter2.flush();
            fWriter2.close();
        }
        catch (Exception e) {
            e.printStackTrace();}

        //Τα αρχεία που παίρνουν τιμές από τον fused provider
        String string3 = "";
        FileWriter fWriter3;
        try{
            fWriter3 = new FileWriter(segmentsOfTrackPointsFileGoogle);
            fWriter3.write(string3);
            fWriter3.flush();
            fWriter3.close();
        }
        catch (Exception e) {
            e.printStackTrace();}

        String string4 = "";
        FileWriter fWriter4;
        try{
            fWriter4 = new FileWriter(segmentsOfWayPointsFileGoogle);
            fWriter4.write(string4);
            fWriter4.flush();
            fWriter4.close();
        }
        catch (Exception e) {
            e.printStackTrace();}


        // Δημιουργεί το αντικείμενο LocationRequest
        mLocationRequest = LocationRequest.create();
        // Δηλώνουμε ότι θέλουμε υψηλή ακρίβεια
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Θέτει το διάστημα ανανέωσης στα 8 δευτερόλεπτα - παρατηρήθηκε ότι αν είναι μικρότερο τότε παίρνει (συνήθως) πάντα θέση από το wifi
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        //Θέτει το γρηγορότερο διάστημα ανανέωσης στα 5 δευτερόλεπτα
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        //Δημιουργεί ένα καινούργιο πελάτη θέσης, χρησιμοποιώντας την περιβαλλόμενη κλάση για να χειρίζεται τα μηνύμνατα
        mLocationClient = new LocationClient(this, this, this);

    }

    //Δείχνει μια ειδοποίηση στον χρήστη ότι η υπηρεσία τρέχει
    private void showNotification() {

        //Χτίζει την ειδοποίηση
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setTicker(getText(R.string.my_location_service_started))//Το πρώτο κείμενο που εμφανίζεται στον χρήστη όταν ξεκινάει η υπηρεσία
                .setSmallIcon(R.drawable.notification_icon)//Η εικόνα της ειδοποίησης
                .setWhen(System.currentTimeMillis())//Δείχνει την ειδοποίηση αμέσως
                .setContentTitle(getText(R.string.my_location_service_label))//Ο τίτλος της ειδοποίησης
                .setContentText(getText(R.string.my_location_service_content));//Το κείμενο της ειδοποίησης

        // To Intent που θα ξεκινάει την MainActivity αν πατηθεί η ειδοποίηση
        Intent resultIntent = new Intent(this, MainActivity.class);

        //Το αντικείμενο stackBuilder θα περιέχει μια τεχνητή (πίσω) στοίβα για την Activity που ξεκίνησε
        //Αυτό εξασφαλίζει ότι η πλοήγηση προς τα πίσω από την Activity θα οδηγήσει την εφαρμογή στην αρχική οθόνη
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);

        //Προσθέτει την (πίσω) στοίβα για το Intent (αλλά όχι το ίδιο το Intent)
        stackBuilder.addParentStack(MainActivity.class);

        // Προσθέτει το Intent που ξεκινά την Activity στην κορυφή της στοίβας
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent resultPendingIntent =stackBuilder.getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentIntent(resultPendingIntent);

        //Ξεκινά την υπηρεσία ώς υπηρεσία προσκηνίου (ώστε να μην είναι υποψήφια προς "σκότωμα" αν υπάρχει λίγη μνήμη
        startForeground(R.string.my_location_service_started, mBuilder.build());

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("MyService", "Received start id " + startId + ": " + intent);

        //Προσθέτει έναν "ακροατή" κατάστασης GPS
        locationManager.addGpsStatusListener(gpsStatusListener);
        //Προσθέτει έναν "ακροατή" για αλλαγή θέσης που ανιχνεύεται από το GPS
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);//Ζητάει ενημερώσεις από τον GPS πάροχο

        if(servicesConnected()) {//Τσεκάρει αν υπάρχει σύνδεση στις google play services
            mLocationClient.connect();//Συνδέει τον πελάτη θέσης
        }

        //Θέλουμε η υπηρεσία να τρέχει συνέχεια ώστε να παίρνει τις νέες θέσεις του χρήστη (ακόμα και αν η οθόνη του χρήστη έχει κλειδώσει)
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyWakelockTag");//Το wakeLock είναι μόνο για τον επεξεργσατή
        wakeLock.acquire();//Αποκτάται το κλείδωμα του επεξεργαστή

        return START_STICKY; // τρέχει μέχρι να σταματήσει ρητά.

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i("MyLocationService", "Service Stopped.");
        isRunning = false;//Δηλώνει ότι η υπηρεσία σταμάτησε
        fixed = false;//Δηλώνει ότι δεν έχει βρεθεί μια ακριβής (νέα) θέση του χρήστη

        //Σταματάει τις ενημερώσεις θέσης
        locationManager.removeUpdates(locationListener);
        //-----------------------------------------------------------------------------------------------------------------------
        if(servicesConnected()) {//Αν οι google play servicew είναι συνδεδεμένες
            // Εάν ο πελάτης θέσης είναι συνδεδεμένος
            if (mLocationClient.isConnected()) {
                stopPeriodicUpdates();//Σταματά την ζήτηση για νέες θέσεις του χρήστη
            }

            // Μετά το κάλεσμα της disconnect(), ο πελάτης θέσης θεωρείται "πεθαμένος".
            mLocationClient.disconnect();
        }

        //Απελευθερώνουμε το κλείδωμα στον επεξεργστή, αφού δεν θέλουμε άλλο να παίρνουμε συνέχεια τις θέσεις του χρήστη
        wakeLock.release();
    }

    //Δηλώνει αν η υπηρεσία τρέχει
    public static boolean isRunning(){

        return isRunning;//Γυρίζει αν η υπηρεσία τρέχει
    }

    //Δηλωνει αν η θέση είναι "φιξαριμένη"
    public static boolean locationIsFixed(){
        return fixed;
    }

    public static boolean locationHasFirstFixedEvent(){
        return locationHasFirstFixedEvent;
    }

    //Γυρίζει την απόσταση δύο σημείων σε μέτρα
    public float distance (double lat_a, double lng_a, double lat_b, double lng_b )
    {
        double earthRadius = 3958.75;
        double latDiff = Math.toRadians(lat_b-lat_a);
        double lngDiff = Math.toRadians(lng_b-lng_a);
        double a = Math.sin(latDiff /2) * Math.sin(latDiff /2) +
                Math.cos(Math.toRadians(lat_a)) * Math.cos(Math.toRadians(lat_b)) *
                        Math.sin(lngDiff /2) * Math.sin(lngDiff /2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = earthRadius * c;

        int meterConversion = 1609;

        return Float.valueOf(Double.toString(distance * meterConversion));
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.e(getString(R.string.app_name), "onLocationChanged: " + location);

        //Θεωρούμε ότι η πρώτη νέα (ακριβής) θέση έχει βρεθεί αν η ακρίβεια είναι κάτω των 37m (Σχετικά καλή ακρίβεια WiFi)
        if (location.getAccuracy()<=37.0 && fixed == false){
            fixed = true;//Είναι φιξαρισμένος ο fused provider
            locationHasFirstFixedEvent=true;
            sendLocationFixedToUI(MSG_SET_LOCATION_FIXED);//Ενημερώνει το UI
        }

        //Θα αρχίσουμε να καταγράφουμε μόνο όταν o fused provider είναι φιξαρισμένος για πρώτη φορά (από προηγουμένως)
        //Παίρνουμε όλες τις θέσεις από εκεί και έπειτα (οι κακές θα εξαλειφθούν στον server. Απλά αν ακρίβεια είναι μεγαλύτερη από 47 δεν θα ενημερώνουμε το
        //UI επομένως θα βγάλουμε ένα μήνυμα στον χρήστη, ώστε να καταλαβαίνει γιατί η διαδρομή του δεν ανανεώνεται στο UI
        if(locationHasFirstFixedEvent == true){

            fixed = (location.getAccuracy()<=47.0);
            if (fixed) { //Έχει αποκτηθεί "φιξάρισμα"
                fixed = true;
                sendLocationFixedToUI(MSG_SET_LOCATION_FIXED);//Ενημερώνει το UI

                //Το γεωγραφικό πλάτος, μήκος και η ακρίβεια της θέσης που βρέθηκε
                Double lat =  location.getLatitude();
                Double lng =  location.getLongitude();
                float accur = location.getAccuracy();

                //-----------------------------------------------------------------------------------------------
                //Κοιτάει να δει αν είναι μέσα στα Χανιά-αν είναι έξω ενημερώνει το UI
                if(lat>39.634068 || lat<39.610548 || lng<19.888666 || lng>19.936624){
                    //if(lat>35.512407 || lat<35.510022 || lng<24.027810 || lng>24.031169){
                    userHasGoneOutOfRegion=true;//Για την περίπτωση που η mainactivity δεν υπάρχει
                    sendOutOfRegionToUI(town);//Για την περίπτωση που υπάρχει η mainactivity
                }

                //-----------------------------------------------------------------------------------------------

                //Βάζουμε κάθε 2 εντοπισμούς θέσεις στην ArrayList για να μην γεμίσει η μνήμη
                counterForUILocations=counterForUILocations+1;


                numberOfLocationsGreaterThanZero=true;//Έχει βρεθεί τουλάχιστον μία θέση

                //Στο UI στέλνει την καλύτερη θέση που έχει εντοπιστεί (όταν έχει βρει 2 θέσεις)
                if(counterForUILocations==3 || counterForUILocations==1){//Αν είναι η 3η έχουν βρεθεί προηγουμένως 2, άρα ξαναμετράει από την αρχή
                    counterForUILocations=1;
                    //Θεωρεί την πρώτη θέση σαν την καλύτερη
                    bestCoordinatesOfLocation=new LatLng(lat,lng );
                    bestCoordinatesAccuracy=accur;
                }

                //Βάζουμε αυτήν που έχει την καλύτερη ακρίβεια από τις 2
                if(counterForUILocations>1 && accur <=bestCoordinatesAccuracy){
                    bestCoordinatesOfLocation=new LatLng(lat,lng );
                    bestCoordinatesAccuracy=accur;
                }

                if(counterForUILocations==2){//Αν είναι η 2η θέση βάζει την θέση με την καλύτερη ακρίβεια στο UI

                    coordinatesOfLocationsList.add(bestCoordinatesOfLocation);

                    sendLastLocationToUI(bestCoordinatesOfLocation.latitude,bestCoordinatesOfLocation.longitude);//Στέλνουμε τη νέα καλύτερη θέση στην Activity
                    if (coordinatesOfLocationsList.size()>=2){//Μετράει την απόσταση που έχει διανυθεί κάθε 2 θέσεις
                        totalDistance = totalDistance + distance (coordinatesOfLocationsList.get(coordinatesOfLocationsList.size()-2).latitude, coordinatesOfLocationsList.get(coordinatesOfLocationsList.size()-2).longitude,  bestCoordinatesOfLocation.latitude,  bestCoordinatesOfLocation.longitude);
                    }


                }



            } else { // To "φιξάρισμα" έχει χαθεί

                fixed = false;

                if (locationHasFirstFixedEvent){
                    sendLocationFixedToUI(MSG_SET_LOCATION_LOST);//Ενημερώνει το UI
                }

            }

            //Καταγράφει τα trackpoints στο gpx αρχείο που δημιουργείται από τον fused provider
            String segment = "<trkpt lat=\"" + location.getLatitude() + "\" lon=\"" + location.getLongitude() + "\"><time>" + df.format(new Date(location.getTime())) + "</time>"
                    + "<hdop>" +location.getAccuracy() +"</hdop>"+"</trkpt>\n";

            try {

                FileOutputStream fOut = openFileOutput(segmentsOfTrackPointsFileGoogle.getName(),
                        MODE_APPEND);
                OutputStreamWriter osw = new OutputStreamWriter(fOut);
                osw.write(segment);
                osw.flush();
                osw.close();

            } catch (FileNotFoundException e) {

                e.printStackTrace();
            } catch (IOException e) {

                e.printStackTrace();
            }

        }
    }

    //Καλείται όταν ο πελάτης θέσης δεν καταφέρει να συνδεθεί
    @Override
    public void onConnectionFailed(ConnectionResult arg0) {

    }

    //Καλείται από τις υπηρεσίες θέσης όταν η σύνδεση του πελάτη (θέσης) τελειώσει επιτυχώς
    @Override
    public void onConnected(Bundle bundle) {

        startPeriodicUpdates();//Αιτείται περιοδικές ενημερώσεις θέσης του χρήστη

    }

    //Καλείται απο τις υπηρεσίες θέσης αν η σύνδεση με τον πελάτη θέσης καταρρεύσει (εξαιτίας κάποιου σφάλματος)
    @Override
    public void onDisconnected() {

        Toast.makeText(getApplicationContext(), R.string.disconnected,Toast.LENGTH_SHORT).show();

    }

    //Σε απάντηση του αιτήματος για να ξεκινήσουν οι ενημερώσεις θέσης στείλε ένα αίτημα στις υπηρεσίες θέσης
    private void startPeriodicUpdates() {

        mLocationClient.requestLocationUpdates(mLocationRequest, this);

    }

    //Σε απάντηση του αιτήματος για να σταματήσουν οι ενημερώσεις θέσης στείλε ένα αίτημα στις υπηρεσίες θέσης
    private void stopPeriodicUpdates() {

        mLocationClient.removeLocationUpdates(this);

    }

    //Βεβαιωνόμαστε ότι οι υπηρεσίες του Google Play είναι διαθέσιμες πριν από την υποβολή του αιτήματος.
    //Γυρίζει true αν οι υπηρεσίες του Google Play είναι διαθέσιμες, αλλιώς false
    private boolean servicesConnected() {

        //Ελέγχει ότι οι υπηρεσίες του Google Play είναι διαθέσιμες
        int resultCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // Εάν οι υπηρεσίες του Google Play είναι διαθέσιμες
        if (ConnectionResult.SUCCESS == resultCode) {
            // Στη λειτουργία εντοπισμού σφαλμάτων καταγράφει την κατάσταση
            Log.d("MapMaker", getString(R.string.play_services_available));

            // Συνέχισε
            return true;
            // Οι υπηρεσίες του Google Play δεν είναι διαθέσιμες για κάποιο λόγο
        }
        else {
            //Στείλε ένα μήνυμα στην activity με το resultCode, ώστε αυτή να εμφανίσει ένα errorDialog στον χρήστη
            for (int i=mClients.size()-1; i>=0; i--) {//Θα στείλει τιμές σε όλους τους client που έχουν συνδεθεί (αρχίζοντας από τον τελευταίο που συνδέθηκε).
                //Πάντως, στην περιπτωση μας έχουμε για client μόνο την MainActivity.class που μάλιστα είναι foreground, αφού "μόλις" ο χρήστης έχει πατήσει το κουμπί
                //"StartRoute"
                try {
                    Bundle bundle = new Bundle();
                    bundle.putInt("result_code", resultCode);

                    Message msg = Message.obtain(null, MSG_GOOGLE_PLAY_SERVICE_RESULT_CODE);
                    msg.setData(bundle);
                    mClients.get(i).send(msg);// Ο mClients.get(i) είναι ο Messenger που θα στείλει το μήνυμα
                }
                catch (RemoteException e) {
                    // Ο πελάτης έχει "πεθάνει". Τον βγάζουμε από την λίστα. Περνάμε την λίστα από το τέλος προς την αρχή επομένως είναι ασφαλές να το κάνουμε μέσα στο βρόχο.
                    //Πάντως, στην περίπτωση μας ο client θα είναι "ζωντανός" (το πιθανότερο),  αφού "μόλις" ο χρήστης έχει πατήσει το κουμπί "StartRoute"
                    mClients.remove(i);
                }

            }

            return false;
        }
    }
}

