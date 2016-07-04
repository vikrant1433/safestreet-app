package in.ac.iitb.cse.carts.safestreet.utilities;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Service starts when device connects to internet
 * Service stops when either all data is sent or internet disconnects
 */
public class PendingDataSender extends Service {

    private static final String TAG = "PendingDataSender";
    RequestHandler rh;
    String pendingFile;
    int linesAlreadySent;
    MetaDataManager metaDataManager;
    HashMap<String,String> map;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Log.d(TAG, "Data sending service started");

        metaDataManager = new MetaDataManager(this);
        rh = new RequestHandler();
        Config.isDataSending = 'y';

        pendingFile = null;
        linesAlreadySent = 0;
    }

    @Override
    public int onStartCommand(Intent intent, final int flags, int startId) {

        class SendData extends AsyncTask<Void, Void, String> {

            @Override
            protected String doInBackground(Void... params) {

                File dir = new File(Config.dataDirectory);
                map = metaDataManager.getMetadata();

                pendingFile = map.get("currentPendingFile");
                if(pendingFile == null) {
                    //Log.d(TAG, "No pending file found, sending fresh file");
                    if (dir.listFiles() == null)
                        sendFiles(new File[0]);
                    else
                        sendFiles(dir.listFiles());
                }
                else {
                    //Log.d(TAG, "Pending file found to be sent");

                    if(map.get("linesAlreadySent") == null)
                        linesAlreadySent = 0;
                    else
                        linesAlreadySent = Integer.parseInt(map.get("linesAlreadySent"));

                    File file = new File(Config.dataDirectory + "/" + pendingFile);
                    if(sendFileData(file)) {
                        file.delete();
                        sendFiles(dir.listFiles());
                    }
                }

                // Stop the service
                stopSelf();
                return null;
            }
        }

        SendData senddata = new SendData();
        senddata.execute();
        return super.onStartCommand(intent, flags, startId);
    }

    /** Pick up a file from the directory and start sending
     *  Stop when internet disconnects, save the status in this case
     *  Or stop when all files are sent */
    private void sendFiles(File[] files) {
        File file;
        int i;
        for (i = 0; i < files.length; i++) {
            file = files[i];

            // Do not send the metadata file-backup.txt and
            // the file which is currently being written by automatic detection service
            if(file.getName().equals("currentRideData.txt"))
                continue;
            else {
                linesAlreadySent = 0;
                pendingFile = file.getName();

                boolean b = sendFileData(file);
                // Successful - delete the file and move to next file
                if(b)
                    file.delete();
                // Unsuccessful - Internet disconnected, stop
                else
                    return;
            }
        }

        // All files are sent
        if (i == files.length) {
            pendingFile = null;
            //Log.d(TAG, "All data files are sent");

            if (!map.get("distance").equals("0"))
                return;

            String userID = map.get("userID");
            String getAllRideDetails = Config.IP+"auto_pothole/allride_details/"+userID+"/";

            String response = rh.sendGetRequest(getAllRideDetails);

            try {
                JSONObject allRideDetails = new JSONObject(response);

                String time = allRideDetails.get("total_time").toString();
                String potholeCount = allRideDetails.get("pothole_count").toString();
                String total_distance = allRideDetails.get("total_distance").toString();

                map.put("distance", total_distance);
                map.put("time", time);
                map.put("potholes", potholeCount);
                //Log.d(TAG,rideDetailsMap.toString());
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }
    }

    /** Function for actually sending individual file to server
     *  How? Send line one by one, and update the lines sent and the pending file */
    private boolean sendFileData(File file) {
        //Log.d(TAG, "Sending file " + file.getName());

        // Update URL
        String url;
        if(file.getName().equals("manualReport.txt"))
            url = Config.AddComplaint;
        else
            url = Config.AddAutomaticDetectionData;

        // Open reader
        //FileReader fr = null;
        FileInputStream fis = null;
        try {
            //fr = new FileReader(file);
            fis =  new FileInputStream(file);
        } catch (FileNotFoundException e) {
            //e.printStackTrace();
            //Log.d(TAG, "FileNotFoundException");
            return false;
        }

        // Update line number from where to send the file
        try {
            int count = 0;
            char c;
            while (linesAlreadySent > count) {
                while ( (c = (char)fis.read()) != -1 && c != '}');
                count++;
            }
        } catch (IOException e) {
            //e.printStackTrace();
            //Log.d(TAG, "IOException");
            return false;
        }

        // Start sending
        boolean flag = true;
        try {
            char c = 'a';

            StringBuilder sb =  new StringBuilder();
            //while ( (c = (char)fr.read()) != -1)
            while (fis.available() > 0) {
                c = (char)fis.read();
                if(c  == '{') {
                    sb = new StringBuilder();
                }
                else {
                    if (c == '}') {

                        HashMap hashMap = getHashMap(sb.toString());
                        hashMap.put("CurrentTime", Long.toString(new Date().getTime()));
                        // In case of no internet do not increase the linesAlreadySent
                        String response = rh.sendPostRequest(url, hashMap);

                        // If response is Error then do not send next data
                        if (response.equals("Error")) {
                            //Log.d(TAG, "Error while sending file");
                            flag = false;
                            break;
                        } else {
                            linesAlreadySent++;
                        }
                    }
                    else {
                        sb.append(c);
                    }
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
            //Log.d(TAG, "IOException");
            return false;
        }

        try {
            //br.close();
            fis.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }
        return flag;
    }

    /** Read line from file, create tokens and convert it to map */
    HashMap<String, String> getHashMap(String str) {

        StringTokenizer st = new StringTokenizer(str, "&&");
        HashMap<String,String> map = new HashMap<>();

        while (st.hasMoreTokens()) {

            StringTokenizer tokenizer = new StringTokenizer(st.nextToken(), "||");

            String key = tokenizer.nextToken();
            String val = tokenizer.nextToken();

            map.put(key, val);
        }

        return  map;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //Log.d(TAG, "Data sending service stopped");

        // Update the metadata
        //map = metaDataManager.getMetadata();
        map.put("currentPendingFile",pendingFile);
        map.put("linesAlreadySent", Integer.toString(linesAlreadySent));
        metaDataManager.writeMetadata(map);
        Config.isDataSending = 'n';
    }
}
