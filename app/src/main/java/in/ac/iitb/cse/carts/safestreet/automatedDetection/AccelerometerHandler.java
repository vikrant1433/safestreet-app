package in.ac.iitb.cse.carts.safestreet.automatedDetection;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import in.ac.iitb.cse.carts.safestreet.utilities.GPSLocation;

public class AccelerometerHandler implements SensorEventListener {

    private static final String TAG = "AccelerometerHandler";
    Context context;
    GPSLocation location;
    SensorManager sensormanager;
    ArrayList<AccelerometerData> data;
    List<AccelerometerData> returnData;  // used for checking stability of phone
    Date startTime;

    int analysisWindowTime = 15*1000; // 15 seconds data will be sent to automaticEventDetector for analysis
    public static double speed = 0;

    public AccelerometerHandler(Context context, GPSLocation location) {
        this.location = location;
        this.context = context;
        data = new ArrayList<>();
        sensormanager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void startService() {
        sensormanager.registerListener(this, sensormanager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
        startTime = new Date();
    }

    public void stopService() {
        sensormanager.unregisterListener(this);
        AccelerometerDataProcessor.closeFileWriter(context);
        data.clear();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            Date d = new Date();
            long dminutes = (d.getTime() - startTime.getTime()) / analysisWindowTime;

            //data array contains data of analysisWindowTime then pass that data to process by autoPotholeDetector
            if(dminutes > 0 ) {
                new AccelerometerDataProcessor(data).start();
                startTime = new Date();
                data = new ArrayList<>();
            }
            if(location!= null && location.getmLastLocation() != null) {
                double lat = location.getmLastLocation().getLatitude();
                double lon = location.getmLastLocation().getLongitude();
                speed = location.getmLastLocation().getSpeed()*3.6f; //this speed is in km/hr
                data.add(new AccelerometerData(event.values[0],event.values[1],event.values[2], d, lat, lon, speed));
            }
            else
                data.add(new AccelerometerData(event.values[0],event.values[1],event.values[2], d, 0, 0, 0));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /********************************
     **  Code for user stability  ***
     ********************************/

    List<AccelerometerData> getLatestData(int time) {
        // Data should be less than 5 sec
        returnData = data.subList(0,data.size()-1);

        AccelerometerData last = returnData.get(returnData.size()-1);
        for(int i = returnData.size()-1; i > 0; i--) {
            if( last.getTime().getTime() - returnData.get(i).getTime().getTime()  > time*1000)
                return returnData.subList(i,returnData.size()-1);
        }
        return returnData;
    }

    // Check if a user is moving or not by checking the threshold
    // of standard deviation for 5 seconds data
    public boolean isUserMoving() {
        if(getSD(getLatestData(5)) < 0.5)
            return false;
        else
            return true;
    }

    double getSD(List<AccelerometerData> arrayList) {
        if(arrayList.size() == 0)
            return 1;

        double x,y,z;
        double mean = 0;
        for (int i = 0; i < arrayList.size(); i++) {
            x = arrayList.get(i).getX();
            y = arrayList.get(i).getY();
            z = arrayList.get(i).getZ();
            mean +=  Math.sqrt(x*x + y*y +z*z);
        }
        mean = mean/arrayList.size();

        double sum = 0;
        for (int i = 0; i < arrayList.size(); i++) {
            x = arrayList.get(i).getX();
            y = arrayList.get(i).getY();
            z = arrayList.get(i).getZ();
            sum +=  Math.pow(Math.sqrt(x * x + y * y + z * z) - mean, 2);
        }

        sum = sum / arrayList.size();
        //Log.d(TAG, "Standard deviation: " + sum);
        return Math.sqrt(sum);
    }




}
