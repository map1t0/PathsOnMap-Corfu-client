package com.ippokratis.mapmaker2.library;

import java.net.InetAddress;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/*********************************************************************************************************
 * Κλάση με τρεις μεθόδους που εντοπίζουν αν υπάρχει σύνδεση δικτύου, internet ή υπάρχει δυνατότητα data *
 * μέσω παρόχου κινητής τηλεφωνίας στην συσκευή.                                                         *
 * *******************************************************************************************************/
public class ConnectionDetector {
    private Context mContext;

    public ConnectionDetector(Context context){
        this.mContext = context;
    }
    //Γυρίζει αν υπάρχει σύνδεση δικτύου
    public boolean isNetworkConnected(){

        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected() == true)
        {
            return true;
        }

        return false;

    }

    //Γυρίζει αν υπάρχει internet - Για να το κάνει αυτό κάνει ping στην διεύθυνση pathsonmap.eu (αφού αυτή η διεύθυνση μας ενδιαφέρει)
    public boolean isInternetAvailable() {
        try {
            InetAddress ipAddr = InetAddress.getByName("pathsonmap.eu");

            if (ipAddr.equals("")) {
                return false;
            } else {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
    }

    //Γυρίζει αν η συσκευή έχει mobile data δυνατότητα
    public boolean hasMobileDatacapability(){

        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if(ni == null)
        {
            // Device does not have mobile data capability
            return false;
        }

        return true;

    }

}
