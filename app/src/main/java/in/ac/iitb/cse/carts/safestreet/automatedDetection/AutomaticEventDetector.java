package in.ac.iitb.cse.carts.safestreet.automatedDetection;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

import in.ac.iitb.cse.carts.safestreet.utilities.GPSLocation;

public class AutomaticEventDetector extends Service {

    private static final String TAG = "AutomaticEventDetector";

    AccelerometerHandler ah;
    GPSLocation location;
    PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        //Log.d(TAG, "Event detector started");

        location = new GPSLocation(this);
        location.buildGoogleApiClient();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // CPU should be ON
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        ah = new AccelerometerHandler(this.getApplicationContext(), location);
        ah.startService();
        location.mGoogleApiClient.connect();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //Log.d(TAG, "Event detector stopped");

        wakeLock.release();
        if (location.mGoogleApiClient.isConnected()) {
            location.stopLocationUpdates();
        }
        location.mGoogleApiClient.disconnect();
        ah.stopService();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
