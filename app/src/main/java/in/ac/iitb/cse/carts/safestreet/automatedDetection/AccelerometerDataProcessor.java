package in.ac.iitb.cse.carts.safestreet.automatedDetection;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import in.ac.iitb.cse.carts.safestreet.utilities.Config;
import in.ac.iitb.cse.carts.safestreet.utilities.GPSLocation;
import in.ac.iitb.cse.carts.safestreet.utilities.MetaDataManager;
import in.ac.iitb.cse.carts.safestreet.utilities.RequestHandler;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

public class AccelerometerDataProcessor extends Thread {

    private static final String TAG = "AutomaticEventDetector";

    ArrayList<AccelerometerData> data;
    static int fileCount;
    public static FileWriter filewriter = null;
    public static StringBuilder rideDetails = null;
    public static double speedSum = 0;
    public static int speedSumCount = 0;

    // Current ride details
    public static int potholeCount = 0;
    public static svm_model model;

    public AccelerometerDataProcessor(ArrayList<AccelerometerData> data) {
        this.data = data;
    }

    public static void closeFileWriter(final Context context) {

        String current_filename = null;
        // If filewriter is not null then close it and rename the file
        if (filewriter != null)
            try {

                //Log.d(TAG,"filewriter closed and file renamed");
                filewriter.close();

                // Once the ride is complete, we have to rename the file according to ride number
                // Get the ride number from metadata
                MetaDataManager metaDataManager = new MetaDataManager(context);
                HashMap<String, String> map = metaDataManager.getMetadata();
                if (map.get("rideNo") == null)
                    fileCount = 1;
                else
                    fileCount = Integer.parseInt(map.get("rideNo")) + 1;
                map.put("rideNo", Integer.toString(fileCount));
                metaDataManager.writeMetadata(map);

                // Rename the file
                File oldName = new File(Config.dataDirectory + "/currentRideData.txt");
                File newName = new File(Config.dataDirectory + "/" + fileCount + ".txt");
                current_filename = Config.dataDirectory + "/" + fileCount + ".txt";
                oldName.renameTo(newName);

                //Log.d(TAG, "Current ride renamed to " + fileCount);
            } catch (IOException e) {
                e.printStackTrace();
            }

        String version = null;
        try {
            version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        class SendRideDetails extends Thread {
            String version;
            String current_filename;

            public SendRideDetails(String version, String current_filename) {
                this.version = version;
                this.current_filename = current_filename;
            }

            public void run() {

                if (rideDetails != null) {
                    // append ride details with stop(time lat lon)

                    //Log.d(TAG, rideDetails.toString());

                    rideDetails.append("AvgRideSpeed:" + Double.toString(speedSum/speedSumCount)
                            + ";StopTime:" + new Date().getTime()
                            +";Distance:" + (GPSLocation.distance/1000)
                            + ";Version:" + version
                            + ";PhoneModel:" + getDeviceName()
                            + "]");


                    HashMap<String, String> hashmap = new HashMap<>();
                    hashmap.put("reporter_id", Config.userID);
                    hashmap.put("pothole_event_data", rideDetails.toString());
                    hashmap.put("vehicle_type", Character.toString(Config.typeOfVehicle));
                    hashmap.put("win_size", Integer.toString(0));
                    hashmap.put("CurrentTime", Long.toString(new Date().getTime()));

                    // try to send the ridedetails data if not able to send then write in the file
                    RequestHandler rh = new RequestHandler();
                    String res = rh.sendPostRequest(Config.AddAutomaticDetectionData, hashmap);
                    //Log.d(TAG,"Server Response after sending ride details: " + res);
                    // If data is not sent to the server, then write it in file

                    if (res.equals("Error")) {
                        if(current_filename != null) {
                            try {
                                filewriter = new FileWriter(new File(current_filename),true);
                                filewriter.write("{reporter_id" + "||" +
                                        Config.userID + "&&" +
                                        "win_size" + "||0&&" +
                                        "vehicle_type" + "||" +
                                        Config.typeOfVehicle + "&&" +
                                        "pothole_event_data" + "||" +
                                        rideDetails.toString() + "}\n");
                                filewriter.close();
                                //Log.d(TAG,"writing to the file "+current_filename);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            MetaDataManager metaDataManager = new MetaDataManager(context);
                            HashMap<String, String> map = metaDataManager.getMetadata();
                            if (map.get("rideNo") == null)
                                fileCount = 1;
                            else
                                fileCount = Integer.parseInt(map.get("rideNo")) + 1;

                            //Log.d(TAG," fileCount "+ fileCount);

                            map.put("rideNo", Integer.toString(fileCount));
                            metaDataManager.writeMetadata(map);

                            File newFile = new File(Config.dataDirectory + "/" + fileCount + ".txt");

                            //Log.d(TAG," file created with name " + newFile.getName());

                            try {
                                FileWriter fileWriter = new FileWriter(newFile, true);
                                fileWriter.write("{reporter_id" + "||" +
                                        Config.userID + "&&" +
                                        "win_size" + "||0&&" +
                                        "vehicle_type" + "||" +
                                        Config.typeOfVehicle + "&&" +
                                        "pothole_event_data" + "||" +
                                        rideDetails.toString() + "}\n");
                                fileWriter.close();
                                //Log.d(TAG,"writing to the file "+newFile.getName());
                            } catch (IOException e) {
                                //Log.d(TAG," file writing exception " + newFile.getName());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }


        new SendRideDetails(version,current_filename).start();


    }

    @Override
    public void run() {
         //If data is not of moving vehicle, return
        if(!isDataOfMovingVehicle(data)) {
            return;
        }

        int winsize = calWindowSize(data);

        // Reoriented data
        ArrayList<AccelerometerData> reoriData = getReorientedData(data);

        // Smoothening data
        data = getsmoothData(reoriData);

        // Windowing and check for pothole event
        List<AccelerometerData> eventWin = null;
        List<AccelerometerData> potholeData = null;

        // Sliding window by 0.5 seconds
        int start_sec_index = 0;
        int first = 0;
        int last = 0;

        // Storing half second indices from data
        ArrayList<Integer> half_second_indices = new ArrayList<>();
        half_second_indices.add(0);
        for (int i = 0; i < data.size(); i++) {
            if( data.get(i).getTime().getTime() - data.get(start_sec_index).getTime().getTime() >= 500) {
                start_sec_index = i;
                half_second_indices.add(i);
            }
        }

        for (int i = 0; i+2 < half_second_indices.size(); i++) {

            eventWin = data.subList(half_second_indices.get(i),half_second_indices.get(i+2));
            if(isPothole(eventWin)) {
                first = 0;
                last = data.size();
                if (i-4 >= 0)
                    first = half_second_indices.get(i-4);
                if (i+6 < half_second_indices.size())
                    last = half_second_indices.get(i+6);
                // here we are taking sublist of reoriented data because we need actual data to analysis
                potholeData =  reoriData.subList(first,last);
                sendPotholeData(potholeData,winsize);

                i = i+5;
            }
        }
    }

    int calWindowSize(List<AccelerometerData> data) {
        Date start = data.get(0).getTime();
        int i = 0;
        //to calculate no of samples in 1 second
        for(; i < data.size(); i++) {
            if((data.get(i).getTime().getTime() - start.getTime())/1000  >= 1)
                break;
        }

        return i;
    }

    /** Write pothole data to file (in case of no internet connection) */
    public void writePotholeDataToFile(List<AccelerometerData> potholeData, int winsize) {
        //Log.d(TAG, "Writing automated data to file");

        if(filewriter == null) {
            String filename  = Config.dataDirectory +"/currentRideData.txt";
            try {
                filewriter =  new FileWriter(new File(filename));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            filewriter.write("{reporter_id"+ "||" +
                    Config.userID + "&&" +
                    "win_size"+ "||" +
                    winsize + "&&" +
                    "vehicle_type" + "||" +
                    Config.typeOfVehicle + "&&" +
                    "pothole_event_data"+ "||" +
                    getPotholeDataString(potholeData) + "}\n" );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Send pothole data to server (in case of internet connection) */
    public void sendPotholeData(List<AccelerometerData> potholeData, int winsize) {
        //Log.d(TAG, "Sending automated data to server");

        HashMap<String, String> hashmap = new HashMap<>();
        hashmap.put("reporter_id", Config.userID);
        hashmap.put("pothole_event_data", getPotholeDataString(potholeData));
        hashmap.put("vehicle_type",Character.toString(Config.typeOfVehicle));
        hashmap.put("win_size", Integer.toString(winsize));
        hashmap.put("CurrentTime", Long.toString(new Date().getTime()));
        hashmap.put("partial_distance", Double.toString(GPSLocation.distance/1000));


        RequestHandler rh = new RequestHandler();
        String  res = rh.sendPostRequest(Config.AddAutomaticDetectionData, hashmap);

        // If data is not sent to the server, then write it in file
        if(res.equals("Error"))
            writePotholeDataToFile(potholeData,winsize);
    }

    /** Converting accelerometer data to string(list) like format */
    public String getPotholeDataString(List<AccelerometerData> data) {

        if (data.size() == 0)
            return "EventDetails:-[]";

        StringBuilder sb = new StringBuilder("EventDetails:-[");
        String str = "";
        int i = 0;

        double lat = data.get(i).getLatitude();
        double lon = data.get(i).getLongitude();
        double x;
        double y;

        sb.append("Lat:" + lat + ";" +"Lon:" + lon + "; Speed:" + data.get(i).getSpeed() +";");


        long start_time = data.get(0).getTime().getTime();
        for(; i < data.size()-1; i++)
        {
            if(Double.compare(lat,data.get(i).getLatitude()) != 0 || Double.compare(lon, data.get(i).getLongitude()) != 0 )
            {
                lat = data.get(i).getLatitude();
                lon = data.get(i).getLongitude();
                sb.append("Lat:" + lat + ";Lon:" + lon + ";Speed:" + data.get(i).getSpeed() +";");
            }

            x = data.get(i).getX();
            y = data.get(i).getY();

            String sxy = new DecimalFormat("#.##").format(Math.sqrt(x*x + y*y));
            String sz = new DecimalFormat("#.##").format(data.get(i).getZ());

            if (data.get(i).getTime().getTime() - start_time >= 500) {
                sb.append("TIME:" + data.get(i).getTime().getTime() + ";");
                start_time = data.get(i).getTime().getTime();
            }

            str =  sxy + "," + sz + ";" ;
            sb.append(str);
        }

        x = data.get(i).getX();
        y = data.get(i).getY();
        String sxy = new DecimalFormat("#.##").format(Math.sqrt(x*x + y*y));
        String sz = new DecimalFormat("#.##").format(data.get(i).getZ());
        str =  sxy + "," + sz ;
        sb.append(str);
        sb.append("]");
        return sb.toString();
    }

    public boolean isPothole(List<AccelerometerData> eventwin) {

        //Calculate features of eventwindow
        double mx = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        double val = 0;
        double mean = 0;
        for(int i = 0; i < eventwin.size(); i++) {
            val = eventwin.get(i).getZ();
            mean = mean + val;
            if( val > mx)
                mx = val;
            else if(val < min)
                min = val;
        }
        double maxmin = mx - min;


        mean  = mean / eventwin.size();
        double var = 0;
        for(int i = 0; i < eventwin.size(); i++) {
            val = eventwin.get(i).getZ();
            var  = var + (val-mean)*(val-mean);
        }
        var  = var / eventwin.size();


        /********************** SVM prediction   */
        // if pothole predicted by modelthen increase potholecount for current ride
        double[][] xTest = new double[1][2];
        xTest[0][0] = maxmin;
        xTest[0][1] = var;

        double[] yPred = svmPredict(xTest,model);
        if(yPred[0] == 1) {
            //Log.d(TAG,"Svm predicted yes");
            potholeCount++;
        }
        else
        {
            //Log.d(TAG,"Svm predicted no");
        }


        // Check if the features correspond to a pothole
        return (maxmin > Config.thresh_MaxMin || var > Config.thresh_Var);
    }

    // Smoothening by averaging
    ArrayList<AccelerometerData> getsmoothData(ArrayList<AccelerometerData> data) {
        ArrayList<AccelerometerData> smoothData = new ArrayList<>();
        int i = 0;
        double valX = 0;
        double valY = 0;
        double valZ = 0;
        double prevX = 0;
        double prevY = 0;
        double prevZ = 0;

        long startTime = data.get(0).getTime().getTime();

        while (data.get(i).getTime().getTime() - startTime < 500){
            valX = valX + data.get(i).getX();
            valY = valY + data.get(i).getY();
            valZ = valZ + data.get(i).getZ();
            i++;
        }

        int j = 0;
        for (; i < data.size(); i++){
            while( data.get(i).getTime().getTime() - data.get(j).getTime().getTime() > 500 ){
                prevX += data.get(j).getX();
                prevY += data.get(j).getY();
                prevZ += data.get(j).getZ();
                j++;
            }

            valX = valX + data.get(i).getX() - prevX;
            valY = valY + data.get(i).getY() - prevY;
            valZ = valZ + data.get(i).getZ() - prevZ;

            smoothData.add(new AccelerometerData(valX/(i-j), valY/(i-j), valZ/(i-j), data.get(i).getTime(), data.get(i).getLatitude(), data.get(i).getLongitude(), data.get(i).getSpeed() ) );
            prevX = 0;
            prevY = 0;
            prevZ = 0;

        }
        return smoothData;
    }

    // Reorient data
    ArrayList<AccelerometerData> getReorientedData(ArrayList<AccelerometerData> data) {
        ArrayList<AccelerometerData> oriData = new ArrayList<>();
        // threshold to determine stable acceleration points
        double thresh = 0.5;
        long count  = 0;
        double medX = 0;
        double medY = 0;
        double medZ = 0;

        //orienting data
        for(int i = 0; i < data.size(); i++)
        {
            double X = data.get(i).getX();
            double Y = data.get(i).getY();
            double Z = data.get(i).getZ();

            double val = Math.sqrt(X*X + Y*Y + Z*Z);

            if(Math.abs( val - 9.8 ) < thresh)
            {
                medX += X;
                medY += Y;
                medZ += Z;
                count = count + 1;
            }
        }
        if(count > 0)
        {
            medX = medX/count;
            medY = medY/count;
            medZ = medZ/count;

            //################ Calculate the Angles based on Median#############
            //### Rotation across Y axis ###
            double yAngle = Math.asin(medX/Math.sqrt(medZ*medZ + medX*medX));

            //### Rotation across X axis ###
            double xAngle = Math.atan2(Math.sqrt(medX*medX + medZ*medZ),medY);

            //# Tweak used to handle data for different cooerdinate axes
            if(medZ > 0 ) {
                yAngle = -yAngle;
                xAngle = -xAngle;
            }

            double cosY = Math.cos(yAngle);
            double sinY = Math.sin(yAngle);
            double cosX = Math.cos(xAngle);
            double sinX = Math.sin(xAngle);


            for(int i = 0; i < data.size(); i++)
            {
                double X = data.get(i).getX();
                double Y = data.get(i).getY();
                double Z = data.get(i).getZ();

                double tmp = -X*sinY + Z*cosY;
                double newx = X*cosY + Z*sinY;
                double newz = Y*cosX - tmp*sinX;
                double newy = Y*sinX + tmp*cosX;

                oriData.add(new AccelerometerData(newx,newy,newz,data.get(i).getTime(), data.get(i).getLatitude(), data.get(i).getLongitude(),data.get(i).getSpeed()));
            }

        }
        return oriData;
    }

    /** Check if GPS speed is greater than 10 m/s */
    boolean isDataOfMovingVehicle(List<AccelerometerData> data) {
        int count = 0;
        int countSpeed = 0;

        for(int i = 0; i < data.size(); i++) {
            if (data.get(i).getLatitude() > 0) {
                count++;
                speedSum = speedSum + data.get(i).getSpeed();
                speedSumCount++;

                if (data.get(i).getSpeed() >= 10)
                    countSpeed++;
            }
        }

        if ((float)countSpeed/(float)count > 0.25f) {
            // if start time is null for current ride then updating it
            if (rideDetails == null) {
                //Log.d(TAG," writing ride details");
                rideDetails = new StringBuilder();
                rideDetails.append("RideDetails:-[StartTime:" + Config.rideStartTime.getTime() + ";");
            }
            return true;
        }
        return false;
    }

    static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return model;
        } else {
            return manufacturer + " " + model;
        }
    }

    // classification on the basis of model
    public double[] svmPredict(double[][] xtest, svm_model model) {
        double[] yPred = new double[xtest.length];

        for(int k = 0; k < xtest.length; k++){
            double[] fVector = xtest[k];
            svm_node[] nodes = new svm_node[fVector.length];
            for (int i = 0; i < fVector.length; i++)    {
                svm_node node = new svm_node();
                node.index = i;
                node.value = fVector[i];
                nodes[i] = node;
            }

            int totalClasses = 2;
            int[] labels = new int[totalClasses];

            svm.svm_get_labels(model, labels);

            double[] prob_estimates = new double[totalClasses];
            yPred[k] = svm.svm_predict_probability(model, nodes, prob_estimates);
        }
        return yPred;
    }
}