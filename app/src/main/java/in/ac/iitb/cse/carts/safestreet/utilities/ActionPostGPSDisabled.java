package in.ac.iitb.cse.carts.safestreet.utilities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import in.ac.iitb.cse.carts.safestreet.automatedDetection.AutomaticEventDetector;

public class ActionPostGPSDisabled extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().matches("android.location.PROVIDERS_CHANGED")) {
            LocationManager locationManager =
                    (LocationManager) context.getSystemService(context.LOCATION_SERVICE);
            if (!(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)))
                if (Config.isAutomationRunning=='y')
                    context.stopService(new Intent(context, AutomaticEventDetector.class));
        }
    }
}
