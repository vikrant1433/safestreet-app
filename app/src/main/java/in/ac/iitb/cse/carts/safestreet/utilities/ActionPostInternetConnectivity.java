package in.ac.iitb.cse.carts.safestreet.utilities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ActionPostInternetConnectivity extends BroadcastReceiver {
    private static final String TAG = "ActionPostInternet";

    @Override
    public void onReceive(Context context, Intent intent) {
        //Log.d(TAG, "Connectivity change broadcast received");

        Intent dataSender = new Intent(context, PendingDataSender.class);
        if (Config.isConnected(context)) {
            context.startService(dataSender);
            Config.isDataSending = 'y';
        } else {
            context.stopService(dataSender);
            Config.isDataSending = 'n';
        }
    }
}
