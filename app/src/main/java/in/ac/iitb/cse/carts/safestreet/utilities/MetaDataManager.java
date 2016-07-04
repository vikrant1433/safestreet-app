package in.ac.iitb.cse.carts.safestreet.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;

/** Metadata contains the following:
 * userID
 * userEmail
 * rideNo - According to the number of rides user has taken
 * currentPendingFile - Number of lines already sent to the server
 * linesAlreadySent - Which file is pending to be sent to the server
 */
public class MetaDataManager {

    private static final String TAG = "MetaDataManager";
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    public MetaDataManager() {
        sharedPref = null;
    }

    // Initialize shared preferences in seperate thread when we have the context
    public MetaDataManager(Context context) {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** Get userID from sharedpref, if available,
        Else from backup file */
    public String getUserID() {

        String userID = null;
        userID = sharedPref.getString("userID",null);

        return userID;
    }

    /** Get all the data from sharedpref or backup file */
    public HashMap<String,String> getMetadata() {

        HashMap<String,String> map = new HashMap<>();

        map.put("userID", sharedPref.getString("userID", null));
        map.put("userEmail", sharedPref.getString("userEmail", null));
        map.put("rideNo", sharedPref.getString("rideNo", null));
        map.put("currentPendingFile", sharedPref.getString("currentPendingFile", null));
        map.put("linesAlreadySent", sharedPref.getString("linesAlreadySent", null));
        map.put("distance", sharedPref.getString("distance", "0"));
        map.put("time", sharedPref.getString("time", "0"));
        map.put("potholes", sharedPref.getString("potholes", "0"));

        return map;
    }

    /** Write the metadata in sharedpref and backup file */
    public void writeMetadata(HashMap<String,String> map) {
        editor = sharedPref.edit();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            editor.putString(entry.getKey(),entry.getValue());
        }
        editor.apply();
    }

    public void clearSharedPref() {
        editor = sharedPref.edit();
        editor.clear();
        editor.commit();
    }
}