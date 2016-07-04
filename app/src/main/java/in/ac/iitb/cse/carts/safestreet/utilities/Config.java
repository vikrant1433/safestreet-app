package in.ac.iitb.cse.carts.safestreet.utilities;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;

import java.io.IOException;
import java.util.Date;

import in.ac.iitb.cse.carts.safestreet.automatedDetection.AccelerometerDataProcessor;

/** Class containing all the URLs and other configuration */
public class Config {

    // Server
    public static final String IP = "http://safestreet.cse.iitb.ac.in:80/";

    // User
    public static String userID;
    public static final String AddUser = IP + "api/user/";
    public static String ModifyUser; // PUT for modifying details, DELETE for deleting account
    public static final String GetUserId = IP + "api/user/userFromEmail/";

    // Report
    public static final String AddComplaint = IP + "api/complaint/";
    public static final String AddAutomaticDetectionData = IP + "auto_pothole/add_pothole";

    // Review
    public static String GetReview;
    public static final String SendReview = IP + "api/review/";
    public static final String ImageURL = IP + "media/images/";


    // Automatic detection threshold for pothole+bump
    public static double thresh_MaxMin = 1.8;
    public static double thresh_Var = 0.18;

    // External storage directory
    public static String dataDirectory = Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/Android/data/in.ac.iitb.cse.carts.safestreet";

    /** Type of vehicle, useful for automatic detection
     * a = auto
     * b = bus
     * c = car
     * d = bike
     */
    public static char typeOfVehicle;

    /** Is the automatic event detection running or not?
     *  y = yes
     *  n = no
     */
    public static char isAutomationRunning = 'n';

    /** Is the pending data sender service running or not?
     *  y = yes
     *  n = no
     */
    public static char isDataSending = 'n';

    public static Date rideStartTime;
    public static boolean isSignedOut = false;

    // Setter for userID
    public static void setUserID(String userID) {
        Config.userID = userID;
        ModifyUser = IP + "api/user/" + userID + "/";;
        GetReview = IP + "api/user/" + userID + "/complaintForReview";
    }

    // Check if WiFi or Mobile Network Data is connected
    public static boolean isConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    // Check if internet is connected
    /*public static boolean isInternetConnected(Context context) {
        boolean ans = isConnected(context) && hasInternet();
        return ans;
    }*/

    // Check if safestreet server is reachable
    public static boolean isServerReachable() {

        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
            //Process ipProcess = runtime.exec("/system/bin/ping -c 1 safestreet.cse.iitb.ac.in");
            int     exitValue = ipProcess.waitFor();
            return (exitValue == 0);

        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
        }

        return false;
    }

    public static void initializeRide() {
        rideStartTime = new Date();

        AccelerometerDataProcessor.filewriter = null;
        AccelerometerDataProcessor.rideDetails = null;
        AccelerometerDataProcessor.potholeCount = 0;
        AccelerometerDataProcessor.speedSum = 0;
        AccelerometerDataProcessor.speedSumCount = 0;

        GPSLocation.distance = 0;
    }
}
