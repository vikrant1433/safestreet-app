package in.ac.iitb.cse.carts.safestreet.utilities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import in.ac.iitb.cse.carts.safestreet.HomeActivity;
import in.ac.iitb.cse.carts.safestreet.ReportActivity;

public class GPSLocation implements GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks, LocationListener{

    private static final String TAG = "GPSLocationClass";
    public GoogleApiClient mGoogleApiClient;
    private Location mLastLocation = null, prevLocation = null;
    private LocationRequest mLocationRequest;
    private Context context;
    private boolean once = true; // onConnected gets called many times
    int initialGPSTime = 0; // Ignore first 3 sec to get an accurate GPS fix
    public static double distance = 0;

    public GPSLocation(Context context) {
        this.context = context;
    }

    public void reset() {
        once=true;
        initialGPSTime = 0;
        prevLocation = null;
    }

    // Getter for getting the last known location
    public Location getmLastLocation() {
        return mLastLocation;
    }

    public LocationRequest getmLocationRequest() {
        return mLocationRequest;
    }

    /** Creating google api client object */
    public synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
    }

    /** Creating location request object */
    public void createLocationRequest() {
        int UPDATE_INTERVAL = 1000; // 1 sec
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /** Starting location updates */
    public void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    /** Stopping location updates */
    public void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //Log.d(TAG, "Google API client for GPS connected");
        createLocationRequest();

        if (once) {
            if (context.getClass().getSimpleName().equals("ReportActivity")) {
                ReportActivity reportActivity = (ReportActivity) context;
                reportActivity.checkGPSisON();
            } else if (context.getClass().getSimpleName().equals("HomeActivity")) {
                HomeActivity homeActivity = (HomeActivity) context;
                homeActivity.checkGPSisON();
                once=false;
                return;
            }
            once = false;
        }
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        //Log.d(TAG, "GPS connection suspended");
        mLastLocation = null;
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //Log.d(TAG, "Google API client for GPS cannot be connected, " +
                //"Error: " + connectionResult.getErrorMessage());
        mLastLocation = null;
    }

    @Override
    public void onLocationChanged(Location location) {

        if (prevLocation != null) {
            if (initialGPSTime>=3)
                updateDistance(prevLocation.getLatitude(), location.getLatitude(),
                        prevLocation.getLongitude(), location.getLongitude());
            else
                initialGPSTime++;
        }
        // Assign the new location
        prevLocation = location;
        mLastLocation = location;
    }

    /** Latitude, Longitude to a street address, requires internet */
    public String displayLocation() {
        String addressString = "";
        if (mLastLocation != null) {
            double latitude = mLastLocation.getLatitude();
            double longitude = mLastLocation.getLongitude();
            Geocoder geocoder;
            List<Address> addresses;
            geocoder = new Geocoder(context, Locale.getDefault());

            try {
                addresses = geocoder.getFromLocation(latitude, longitude, 1);
                // Here 1 represent max location result to returned, by documents it recommended 1 to 5

                /*String address = addresses.get(0).getAddressLine(0); // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
                String area = addresses.get(0).getSubLocality();
                String city = addresses.get(0).getLocality();
                String state = addresses.get(0).getAdminArea();
                String country = addresses.get(0).getCountryName();
                String postalCode = addresses.get(0).getPostalCode();

                tvLocation.setText(address+area+"\n"+city+state+"\n"+country+postalCode);
                */
                Address address = addresses.get(0);
                for (int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                    addressString = addressString + address.getAddressLine(i) + "\n";
                }
                //addressString += latitude + "," + longitude;
            } catch (IOException e) {
                //e.printStackTrace();
            }
        } else {

        }
        return addressString;
    }

    private void updateDistance(double startLatitude, double stopLatitude,
                                      double startLongitude, double stopLongitude) {

        double dist = getDistance(startLatitude,stopLatitude,startLongitude,stopLongitude);
        // if distance covered is more than 4 meters in a second then only update the distance
        if(dist > 4)
            distance += dist;
    }

    // Calculate distances on the basis of latitude and longitude in meters
    double getDistance(double startLatitude, double stopLatitude, double startLongitude, double stopLongitude) {

        startLatitude = toRad(startLatitude);
        stopLatitude = toRad(stopLatitude);
        startLongitude = toRad(startLongitude);
        stopLongitude = toRad(stopLongitude);

        double dlat = stopLatitude - startLatitude;
        double dlon = stopLongitude - startLongitude;


        double a = Math.sin(dlat/2)* Math.sin(dlat/2) + Math.cos(startLatitude)*Math.cos(stopLatitude)* Math.sin(dlon/2) * Math.sin(dlon/2);
        double c = 2* Math.asin(Math.sqrt(a));
        return 6367*c*1000;
    }


    private static Double toRad(Double value) {
        return value * Math.PI / 180;
    }
}
