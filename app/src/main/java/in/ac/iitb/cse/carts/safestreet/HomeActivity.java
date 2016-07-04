package in.ac.iitb.cse.carts.safestreet;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import in.ac.iitb.cse.carts.safestreet.automatedDetection.AccelerometerDataProcessor;
import in.ac.iitb.cse.carts.safestreet.automatedDetection.AccelerometerHandler;
import in.ac.iitb.cse.carts.safestreet.automatedDetection.AutomaticEventDetector;
import in.ac.iitb.cse.carts.safestreet.menu.AboutActivity;
import in.ac.iitb.cse.carts.safestreet.menu.HelpActivity;
import in.ac.iitb.cse.carts.safestreet.menu.SettingsActivity;
import in.ac.iitb.cse.carts.safestreet.utilities.Config;
import in.ac.iitb.cse.carts.safestreet.utilities.GPSLocation;
import in.ac.iitb.cse.carts.safestreet.utilities.MetaDataManager;
import libsvm.svm;
import libsvm.svm_model;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final int REQUEST_CHECK_SETTINGS = 1000;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;

    TextView tvCurrentRideDistance, tvCurrentRideTime, tvCurrentRidePotholes;
    TextView tvAllRideDistance, tvAllRideTime, tvAllRidePotholes;
    TextView tvGPSStatus, tvConnStatus, tvUploadStatus;
    Button bAuto, bBike, bBus, bCar, bStopRide;
    GPSLocation location;
    MetaDataManager metaDataManager;
    HashMap<String,String> map;
    DashboardUpdater dashboardUpdater;
    DashboardStatusUpdater dashboardStatusUpdater;
    Handler dashboardStatusHandler;
    Handler dashboardHandler;
    boolean stopDashboardUpdater;
    boolean stopDashboardStatusUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Create and connect GPS object to get the location
        location = new GPSLocation(this);
        if (checkPlayServices())
            location.buildGoogleApiClient();

        tvCurrentRideDistance = (TextView) findViewById(R.id.tvCurrentRideDistance);
        tvCurrentRideTime = (TextView) findViewById(R.id.tvCurrentRideTime);
        tvCurrentRidePotholes = (TextView) findViewById(R.id.tvCurrentRidePotholes);

        tvAllRideDistance = (TextView) findViewById(R.id.tvAllRideDistance);
        tvAllRideTime = (TextView) findViewById(R.id.tvAllRideTime);
        tvAllRidePotholes = (TextView) findViewById(R.id.tvAllRidePotholes);

        tvGPSStatus = (TextView) findViewById(R.id.tvGPSStatus);
        tvConnStatus = (TextView) findViewById(R.id.tvConnStatus);
        tvUploadStatus = (TextView) findViewById(R.id.tvUploadStatus);

        bAuto = (Button) findViewById(R.id.bAuto);
        bBike = (Button) findViewById(R.id.bBike);
        bBus = (Button) findViewById(R.id.bBus);
        bCar = (Button) findViewById(R.id.bCar);
        bStopRide = (Button) findViewById(R.id.bStopRide);

        new Thread(new Runnable() {
            @Override
            public void run() {
                metaDataManager = new MetaDataManager(HomeActivity.this);
                // Update "total ride" details until now of the user
                getAllRide();
            }
        }).start();

        bAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                location.mGoogleApiClient.connect();
                Config.typeOfVehicle = 'a';
                changeButtonStates(bAuto, true);
            }
        });
        bBike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                location.mGoogleApiClient.connect();
                Config.typeOfVehicle = 'd';
                changeButtonStates(bBike, true);
            }
        });
        bBus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                location.mGoogleApiClient.connect();
                Config.typeOfVehicle = 'b';
                changeButtonStates(bBus, true);
            }
        });
        bCar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                location.mGoogleApiClient.connect();
                Config.typeOfVehicle = 'c';
                changeButtonStates(bCar, true);
            }
        });
        bStopRide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);
                builder.setMessage("Are you sure you want to stop the ride?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                stopEventDetector();
                                if (location.mGoogleApiClient.isConnected()) {
                                    location.mGoogleApiClient.disconnect();
                                    location.reset();
                                }
                                changeButtonStates(bAuto, false);
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                dialog.dismiss();
                            }
                        });
                builder.show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Config.isSignedOut) {
            Config.isSignedOut=false;
            finish();
        }

        stopDashboardStatusUpdater = false;
        dashboardStatusUpdater= new DashboardStatusUpdater();
        dashboardStatusHandler = new Handler();
        dashboardStatusHandler.post(dashboardStatusUpdater);

        // Automatic event detector is running
        // Update dashboard and disable start ride buttons
        if (Config.isAutomationRunning == 'y') {
            startDashboardUpdate();
            disableStartRide(true);
        }
        else
            disableStartRide(false);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // App is going in background, stop dashboard update
        stopDashboardStatusUpdater = true;
        stopDashboardUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (location.mGoogleApiClient.isConnected()) {
            location.mGoogleApiClient.disconnect();
            location.reset();
        }
        if (Config.isAutomationRunning == 'y') {
            stopEventDetector();
            changeButtonStates(bAuto, false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            switch (resultCode) {
                case RESULT_OK:
                    startEventDetector();
                    break;
                case RESULT_CANCELED:
                    Toast.makeText(this, "GPS must be turned ON", Toast.LENGTH_LONG).show();
                    location.mGoogleApiClient.disconnect();
                    location.reset();
                    changeButtonStates(bAuto, false);
                    break;
                default:
                    break;
            }
        }
    }

    public void reportPothole(View view) {
        Intent intent = new Intent(this, ReportActivity.class);
        startActivity(intent);
    }

    public void reviewPothole(View view) {
        Intent intent = new Intent(this, ReviewActivity.class);
        startActivity(intent);
    }

    void startDashboardUpdate() {
        stopDashboardUpdater = false;
        dashboardUpdater= new DashboardUpdater();
        dashboardHandler = new Handler();
        dashboardHandler.post(dashboardUpdater);
    }

    void stopDashboardUpdate() {
        stopDashboardUpdater = true;
    }

    void startEventDetector() {

        getAllRide();
        Config.initializeRide();

        //setting classification model
        AccelerometerDataProcessor.model = getClassificationModel();

        Intent intent = new Intent(this, AutomaticEventDetector.class);
        startService(intent);

        String vehicle;
        switch (Config.typeOfVehicle) {
            case 'a':
                vehicle = "auto";
                break;
            case 'b':
                vehicle = "bus";
                break;
            case 'c':
                vehicle = "car";
                break;
            case 'd':
                vehicle = "bike";
                break;
            default:
                vehicle = "";
                break;
        }

        // Notification
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.icon)
                        .setContentTitle("Ride started")
                        .setContentText("Your vehicle is "+ vehicle);

        int mNotificationId = 001;
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = mBuilder.build();
        notification.flags = Notification.FLAG_NO_CLEAR;
        mNotifyMgr.notify(mNotificationId, notification);

        Config.isAutomationRunning = 'y';
        disableStartRide(true);
        startDashboardUpdate();
    }

    void stopEventDetector() {
        // Add current ride details to all rides
        setAllRide();

        Intent intent = new Intent(this, AutomaticEventDetector.class);
        stopService(intent);

        int mNotificationId = 001;
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.cancel(mNotificationId);

        Config.isAutomationRunning = 'n';
        disableStartRide(false);
        tvGPSStatus.setText("OFF");
        stopDashboardUpdate();
    }

    /** Enable either the start ride button or the stop ride button, not both
     *  @param b - True for enabling stop ride, False for enabling start ride
     */
    private void disableStartRide(boolean b) {
        boolean status = !b;

        bAuto.setEnabled(status);
        bBike.setEnabled(status);
        bBus.setEnabled(status);
        bCar.setEnabled(status);

        bStopRide.setEnabled(b);
    }

    private void changeButtonStates(Button b, Boolean isRideOn) {
        ArrayList<Button> arrayList = new ArrayList<>();
        arrayList.add(bAuto);
        arrayList.add(bBike);
        arrayList.add(bBus);
        arrayList.add(bCar);
        for (Button button : arrayList) {
            if (!isRideOn) {
                button.getBackground().setAlpha(255);
            }
            else {
                if (!button.equals(b))
                    button.getBackground().setAlpha(100);
            }
        }
    }

    /** Update the Textviews with all the ride details of the user */
    void getAllRide() {
        map = metaDataManager.getMetadata();

        double distance = Double.parseDouble(map.get("distance"));
        distance = Math.round( (distance) * 100.0 ) / 100.0;
        tvAllRideDistance.setText(distance + " KM");

        long millis = Long.parseLong(map.get("time"));
        String time = String.format(Locale.ENGLISH, "%d h:%02d m:%02d s",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1));
        tvAllRideTime.setText(time);

        tvAllRidePotholes.setText(map.get("potholes"));
    }

    /** Add the current ride details to the total ride details */
    void setAllRide() {
        map = metaDataManager.getMetadata();

        // Updating total ride details
        double distance = Double.parseDouble(map.get("distance"))
                + GPSLocation.distance/1000;
        int potholes = Integer.parseInt(map.get("potholes"))
                +  AccelerometerDataProcessor.potholeCount;
        long time = Long.parseLong(map.get("time"))
                + (new Date().getTime() - Config.rideStartTime.getTime());

        map.put("distance", Double.toString(distance));
        map.put("time", Long.toString(time));
        map.put("potholes", Integer.toString(potholes));

        metaDataManager.writeMetadata(map);
    }

    /** Create a dialog box if GPS is not ON or on low power mode. */
    public void checkGPSisON() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(location.getmLocationRequest());
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi
                .checkLocationSettings(location.mGoogleApiClient, builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        startEventDetector();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(HomeActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        //Log.d(TAG, "GPS cannot be started");
                        Toast.makeText(HomeActivity.this,
                                "Unable to start GPS", Toast.LENGTH_LONG).show();
                        changeButtonStates(bAuto, false);
                        finish();
                        break;
                }
            }
        });
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                finish();
            }
            return false;
        }
        return true;
    }

    class DashboardUpdater implements Runnable {
        @Override
        public void run() {

            long millis = new Date().getTime() - Config.rideStartTime.getTime();
            String time = String.format(Locale.ENGLISH, "%d h:%02d m:%02d s",
                    TimeUnit.MILLISECONDS.toHours(millis),
                    TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                    TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1));
            tvCurrentRideTime.setText(time);

            double distance = Math.round( (GPSLocation.distance/1000) * 100.0 ) / 100.0;
            tvCurrentRideDistance.setText(distance + " KM");

            tvCurrentRidePotholes.setText(Integer.toString(AccelerometerDataProcessor.potholeCount));

            double gpsSpeed = Math.round( (AccelerometerHandler.speed) * 10.0 ) / 10.0;
            tvGPSStatus.setText(gpsSpeed + " km/h");

            if(!stopDashboardUpdater) {
                // calling dashboard updater every 1 second
                dashboardHandler.postDelayed(this,1000);
            }
        }
    }

    class DashboardStatusUpdater implements Runnable {

        @Override
        public void run() {
            long remainingData = folderSize(new File(Config.dataDirectory));
            if (remainingData == 0)
                tvUploadStatus.setTextColor(Color.GREEN);
            else
                tvUploadStatus.setTextColor(Color.RED);
            tvUploadStatus.setText(remainingData + "kB remaining");

            if (Config.isConnected(HomeActivity.this)) {
                if (Config.isDataSending == 'y' || Config.isAutomationRunning == 'y') {
                    tvConnStatus.setText("Uploading");
                    if (remainingData!=0)
                        tvUploadStatus.setTextColor(Color.YELLOW);
                }
                else
                    tvConnStatus.setText("Disconnected");
            }
            else
                tvConnStatus.setText("No internet");

            if(!stopDashboardStatusUpdater) {
                // calling dashboard updater every 4 second
                dashboardStatusHandler.postDelayed(this,4000);
            }
        }
    }

    /** Get the size of all files in a directory */
    long folderSize(File directory) {
        long length = 0;
        if (!directory.exists())
            return length;
        for (File file : directory.listFiles()) {
            length += file.length();
        }
        return length/1024;
    }

    /********
     * MENU *
     ********/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.home_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_help:
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            case R.id.menu_settings:
                if (Config.isAutomationRunning=='n')
                    startActivity(new Intent(this, SettingsActivity.class));
                else {
                    Toast.makeText(this, "Settings are disabled during a ride. DRIVE SAFE!",
                            Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.menu_feedback:
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("message/rfc822");
                i.putExtra(android.content.Intent.EXTRA_EMAIL,
                        new String[]{"safestreet.carts@gmail.com"});
                i.putExtra(android.content.Intent.EXTRA_SUBJECT, "Feedback");
                startActivity(i);
                return true;
            case R.id.menu_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.menu_exit:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (Config.isAutomationRunning=='n')
            super.onBackPressed();
        else {
            new AlertDialog.Builder(this)
                    .setTitle("Really Exit?")
                    .setMessage("Are you sure you want to exit?\n" +
                            "Automated detection will stop")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface arg0, int arg1) {
                            HomeActivity.super.onBackPressed();
                        }
                    }).create().show();
        }
    }

    /* get Classification Model from file
         */
    svm_model getClassificationModel() {
        svm_model model = null;
        try {
            InputStream is = getResources().getAssets().open("pothole_detector.model");
            BufferedReader br = new BufferedReader(new InputStreamReader(is,"UTF-8"));
            model = svm.svm_load_model(br);
            is.close();
            br.close();
        } catch (IOException e) {
            //Log.d(TAG, "Model is not loaded");
        }
        return  model;
    }
}