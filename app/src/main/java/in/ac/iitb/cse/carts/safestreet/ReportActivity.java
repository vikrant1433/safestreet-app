package in.ac.iitb.cse.carts.safestreet;

import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import in.ac.iitb.cse.carts.safestreet.automatedDetection.AccelerometerHandler;
import in.ac.iitb.cse.carts.safestreet.utilities.Config;
import in.ac.iitb.cse.carts.safestreet.utilities.GPSLocation;
import in.ac.iitb.cse.carts.safestreet.utilities.RequestHandler;

public class ReportActivity extends AppCompatActivity {

    private static final String TAG = "ReportActivity";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CHECK_SETTINGS = 1000;

    Uri fileUri;
    Bitmap imageBitmap;
    ImageView ivCamera;
    TextView tvLocation, tvRatingPotholeQue;
    RatingBar ratingPothole;
    EditText etComments;
    Button bCamera, bSubmit;
    String rating, comments;

    GPSLocation location;
    Handler mHandler;
    AccelerometerHandler ah;
    int stabilityAttempt;
    int stableTimeElapsed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        // Checking camera availability
        if (!ifDeviceSupportCamera()) {
            Toast.makeText(this, "Sorry! Your device doesn't support camera",
                    Toast.LENGTH_LONG).show();
            // will close the app if the device doesn't have camera
            finish();
        }

        // Create and connect GPS object to get the location
        location = new GPSLocation(this);
        if (checkPlayServices()) {
            location.buildGoogleApiClient();
        }

        ivCamera = (ImageView) findViewById(R.id.ivCamera);
        tvLocation = (TextView) findViewById(R.id.tvLocation);
        tvRatingPotholeQue = (TextView) findViewById(R.id.tvRatingBarQue);
        ratingPothole = (RatingBar) findViewById(R.id.ratingPothole);
        etComments = (EditText) findViewById(R.id.etComments);
        bCamera = (Button) findViewById(R.id.bCamera);
        bSubmit = (Button) findViewById(R.id.bSubmit);

        // Enable after image is captured
        bSubmit.setEnabled(false);
        // Enable after checking that the user is not in a moving vehicle
        bCamera.setEnabled(false);

        bCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // create Intent to take a picture and return control to the calling application
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                fileUri = getOutputMediaFileUri();                              // create a file to save the image
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);   // set the image file name

                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            }
        });

        bSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Log.d(TAG, "Manual complaint submitted");
                rating = String.valueOf(ratingPothole.getRating());
                comments = etComments.getText().toString();

                sendPotholeComplaint();
                Toast.makeText(ReportActivity.this, "Thanks for submitting.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        // Check if the user is not clicking photo while riding on a vehicle
        startStabilityCheck();
    }

    @Override
    protected void onStart() {
        super.onStart();
        location.mGoogleApiClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (location.mGoogleApiClient.isConnected()) {
            location.stopLocationUpdates();
            location.mGoogleApiClient.disconnect();
        }
    }

    /** Return after capturing an image */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Camera result
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            try {
                //Log.d(TAG, "Image successfully captured");

                // Compress the image and get its bitmap
                File potholeImage = compressImage(fileUri.toString());

                File actualImage = new File(fileUri.toString());
                actualImage.delete();

                fileUri = Uri.fromFile(potholeImage);
                imageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), fileUri);

                tvLocation.setText(location.displayLocation());
                location.stopLocationUpdates();

                // Show thumbnail
                ivCamera.setImageBitmap(imageBitmap);
                tvLocation.setVisibility(View.VISIBLE);
                tvRatingPotholeQue.setVisibility(View.VISIBLE);
                ratingPothole.setVisibility(View.VISIBLE);
                etComments.setVisibility(View.VISIBLE);
                bCamera.setText("Click again!");
                bSubmit.setEnabled(true);

                galleryAddPic(fileUri);

            } catch (Exception e) {
                //e.printStackTrace();
            }
        }
        // GPS dialog box result
        else if (requestCode == REQUEST_CHECK_SETTINGS) {
            switch (resultCode) {
                case RESULT_OK:
                    //Log.d(TAG, "User turned on the GPS");
                    //startStabilityCheck();
                    break;
                case RESULT_CANCELED:
                    Toast.makeText(this, "Report requires GPS to be ON", Toast.LENGTH_LONG).show();
                    finish();
                    break;
                default:
                    break;
            }
        }
        else if (resultCode == RESULT_CANCELED) {
            // user cancelled Image capture
            Toast.makeText(this,
                    "User cancelled image capture", Toast.LENGTH_SHORT).show();
        } else {
            // failed to capture image
            Toast.makeText(this,
                    "Sorry! Failed to capture image", Toast.LENGTH_SHORT).show();
        }
    }


    /******************
     ***  UTILITIES ***
     *****************/

    /** Start accelerometer, collect the data for 1 sec,
     *  and then analyze it further for stability.
     *  This check is for not allowing people to click
     *  while driving */
    public void startStabilityCheck() {

        ah = new AccelerometerHandler(this , null);
        mHandler = new Handler();
        stabilityAttempt = 0;
        ah.startService();
        mHandler.postDelayed(StabilityChecker, 1000);
    }

    /** Collect data from accelerometer and check if it is stationary */
    Runnable StabilityChecker = new Runnable() {
        @Override
        public void run() {
            stableTimeElapsed++;
            if (stableTimeElapsed <= 5) {
                bCamera.setText("Hold the phone stationary for " + Integer.toString(5 - stableTimeElapsed) + " Seconds");

                if (ah.isUserMoving()) {
                    stableTimeElapsed = 0;
                    stabilityAttempt++;

                    // If 3 stability attempts fail, then go back to home screen
                    if (stabilityAttempt > 3) {
                        stabilityAttempt = 0;
                        Toast.makeText(ReportActivity.this,
                                "Keep the phone stationary\nCAUTION: Do not click while driving",
                                Toast.LENGTH_LONG).show();
                        mHandler.removeCallbacks(StabilityChecker);
                        ah.stopService();

                        stableTimeElapsed = 0;
                        finish();
                        return;
                    }
                }
                mHandler.postDelayed(StabilityChecker, 1000);
            }
            // If stable time elapsed is more than 5 seconds then stop collecting the
            // accelerometer data and enable the camera button
            else {
                ah.stopService();
                stabilityAttempt = 0;
                stableTimeElapsed = 0;
                bCamera.setText("Click Photo");
                bCamera.setEnabled(true);
            }
        }
    };

    /** Send pothole/bump complaint to server */
    private void sendPotholeComplaint() {

        class UploadData extends AsyncTask<Void, Void, Void> {

            RequestHandler rh = new RequestHandler();

            @Override
            protected Void doInBackground(Void... params) {
                // Convert image to base64
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);

                // Server is accepting image in format -> data:image/jpeg;base64 base64ImageString
                String encodedImage = "data:image/jpeg;base64," +
                        Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);

                // Creating map of complaint
                HashMap<String, String> data = new HashMap<>();
                data.put("Image", encodedImage);
                data.put("Type", "p");
                data.put("Severity", rating);
                if(comments == null || comments.equals(""))
                    comments = "No comments";
                data.put("Info", comments);
                data.put("ReporterId", Config.userID);
                data.put("Lat", Double.toString(location.getmLastLocation().getLatitude()));
                data.put("Long", Double.toString(location.getmLastLocation().getLongitude()));
                data.put("City", "mum");

                String result = rh.sendPostRequest(Config.AddComplaint, data);
                if(result.equals("Error")) {
                    //Log.d(TAG, "No internet, manual report saved to file");
                    writeDataToFile(data);
                }
                return null;
            }
        }

        UploadData ui = new UploadData();
        ui.execute();
    }

    /** Save the manual report in a file to be sent later to server */
    void writeDataToFile(HashMap<String,String> data) {
        File file = new File(Config.dataDirectory +"/manualReport.txt");
        try {
            FileWriter fw = new FileWriter(file, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("\n" + getDataString(data));
            bw.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }

    /** Map object to string, for convenience in writing file */
    String getDataString(HashMap<String,String> map) {
        StringBuilder result = new StringBuilder();
        result.append("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (first)
                first = false;
            else
                result.append("&&");

            result.append(entry.getKey());
            result.append("||");
            result.append(entry.getValue());
        }

        result.append("}");
        return result.toString();
    }

    /** Store the file uri */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("file_uri", fileUri);
    }

    /** Restore the fileUri again */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        fileUri = savedInstanceState.getParcelable("file_uri");
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
                        // All location settings are satisfied. The client can initialize location
                        // requests here.

                        //startStabilityCheck();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(ReportActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        //Log.d(TAG, "GPS cannot be started");
                        Toast.makeText(ReportActivity.this,
                                "Unable to start GPS", Toast.LENGTH_LONG).show();
                        finish();
                        break;
                }
            }
        });
    }

    /** Checking device has camera hardware or not */
    private boolean ifDeviceSupportCamera() {
        if (getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri() {
        return Uri.fromFile(getOutputMediaFile());
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile() {

        // Check if SDCard is mounted
        // Boolean isSDPresent = Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Pothole");

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_" + timeStamp + ".jpg");
        return mediaFile;
    }

    /** Add photos to the gallery */
    private void galleryAddPic(Uri fileUri) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(fileUri);
        this.sendBroadcast(mediaScanIntent);
    }

    /*********************
     * IMAGE COMPRESSION *
     *********************/

    public File compressImage(String imageUri) {

        String filePath = getRealPathFromURI(imageUri);
        Bitmap scaledBitmap = null;

        BitmapFactory.Options options = new BitmapFactory.Options();

        //      by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
        //      you try the use the bitmap here, you will get null.
        options.inJustDecodeBounds = true;
        Bitmap bmp = BitmapFactory.decodeFile(filePath, options);

        int actualHeight = options.outHeight;
        int actualWidth = options.outWidth;

        // max Height and width values of the compressed image is taken as below

        float maxHeight = 500.0f;
        float maxWidth = 500.0f;
        float imgRatio = actualWidth / actualHeight;
        float maxRatio = maxWidth / maxHeight;

        // width and height values are set maintaining the aspect ratio of the image

        if (actualHeight > maxHeight || actualWidth > maxWidth) {
            if (imgRatio < maxRatio) {
                imgRatio = maxHeight / actualHeight;
                actualWidth = (int) (imgRatio * actualWidth);
                actualHeight = (int) maxHeight;
            } else if (imgRatio > maxRatio) {
                imgRatio = maxWidth / actualWidth;
                actualHeight = (int) (imgRatio * actualHeight);
                actualWidth = (int) maxWidth;
            } else {
                actualHeight = (int) maxHeight;
                actualWidth = (int) maxWidth;

            }
        }

        //      setting inSampleSize value allows to load a scaled down version of the original image
        options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

        //      inJustDecodeBounds set to false to load the actual bitmap
        options.inJustDecodeBounds = false;

        //      this options allow android to claim the bitmap memory if it runs low on memory
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inTempStorage = new byte[16 * 1024];

        try {
            //          load the bitmap from its path
            bmp = BitmapFactory.decodeFile(filePath, options);
        } catch (OutOfMemoryError exception) {
            exception.printStackTrace();
        }

        try {
            scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError exception) {
            exception.printStackTrace();
        }

        float ratioX = actualWidth / (float) options.outWidth;
        float ratioY = actualHeight / (float) options.outHeight;
        float middleX = actualWidth / 2.0f;
        float middleY = actualHeight / 2.0f;

        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

        Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

        //      check the rotation of the image and display it properly
        ExifInterface exif;
        try {
            exif = new ExifInterface(filePath);

            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, 0);
            Matrix matrix = new Matrix();
            if (orientation == 6) {
                matrix.postRotate(90);
            } else if (orientation == 3) {
                matrix.postRotate(180);
            } else if (orientation == 8) {
                matrix.postRotate(270);
            }
            scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
                    scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
                    true);
        } catch (IOException e) {
            //e.printStackTrace();
        }

        FileOutputStream out = null;
        File file = getOutputMediaFile();
        String filename = file.toString();
        try {
            out = new FileOutputStream(filename);

            // write the compressed bitmap at the destination specified by filename.
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);

        } catch (FileNotFoundException e) {
            //e.printStackTrace();
        }

        return file;
    }

    private String getRealPathFromURI(String contentURI) {
        Uri contentUri = Uri.parse(contentURI);
        Cursor cursor = getContentResolver().query(contentUri, null, null, null, null);
        if (cursor == null) {
            return contentUri.getPath();
        } else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            return cursor.getString(index);
        }
    }

    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }
}
