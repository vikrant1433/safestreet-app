package in.ac.iitb.cse.carts.safestreet;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.StringTokenizer;

import in.ac.iitb.cse.carts.safestreet.utilities.Config;
import in.ac.iitb.cse.carts.safestreet.utilities.RequestHandler;

public class ReviewActivity extends AppCompatActivity {

    private static final String TAG = "ReviewActivity";
    int N = 3;      // No. of images loaded from server (server handles this)
    int cur = 0;    // Index of images loaded

    ImageView ivPothole;
    Button bYes, bNo, bNotSure;
    TextView queTextView;
    RequestHandler rh;
    Drawable[] dr = new Drawable[N];
    JSONArray reviews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        ivPothole = (ImageView) findViewById(R.id.ivPothole);
        bYes = (Button) findViewById(R.id.bYes);
        bNo = (Button) findViewById(R.id.bNo);
        bNotSure = (Button) findViewById(R.id.bNotSure);
        queTextView = (TextView) findViewById(R.id.queTextView);

        rh = new RequestHandler();

        // Get images to be reviewed from server in a separate thread
        new Thread() {
            public void run() {
                cur = 0;
                getComplaintsForReview();
            }
        }.start();

        bYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread t = new Thread() {
                    public void run() {
                        sendReviewResponse("y");
                    }
                };
                t.start();
            }

        });

        bNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread t = new Thread(){
                    public void run() {
                        sendReviewResponse("n");
                    }
                };
                t.start();
            }

        });
        bNotSure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread t = new Thread(){
                    public void run() {
                        sendReviewResponse("m");
                    }
                };
                t.start();
            }

        });
    }

    /** Load N pothole images from server */
    void getComplaintsForReview() {

        // Disable the buttons until the images are loaded
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bYes.setEnabled(false);
                bNo.setEnabled(false);
                bNotSure.setEnabled(false);
            }
        });

        String result = rh.sendGetRequest(Config.GetReview);
        if(result == null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ReviewActivity.this, "Internet must be connected.",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
            });
            return;
        }

        try {
            reviews = new JSONArray(result);
            N = reviews.length();
            //Log.d(TAG, "Images fetched from server for review: " + N);

            // No potholes to review for the user
            // Either because the user reviewed all potholes
            // Or because there are no complaints at server
            if (N == 0) {
                //Log.d(TAG, "No more complaints to review");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ReviewActivity.this, "There are no complaints to review.",
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
            else {
                int i;

                // Download each image serially, sent by the server
                for (i = 0; i < N; i++) {
                    JSONObject review = reviews.getJSONObject(i);
                    StringTokenizer st = new StringTokenizer(review.get("Image").toString(), "/");
                    String filename = "";
                    while (st.hasMoreTokens())
                        filename = st.nextToken();
                    dr[i] = LoadImageFromWeb(Config.ImageURL + filename);
                }

                // Load each image into the imageView for the reviewer
                if (i > 0) {
                    final JSONObject review = reviews.getJSONObject(0);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                String type = (String) review.get("Type");
                                if (type == "p") {
                                    queTextView.setText("Is it a Pothole?");
                                }
                                if (type == "b") {
                                    queTextView.setText("Is it a Bump?");
                                }
                            } catch (JSONException e) {
                                ////e.printStackTrace();
                            }
                            loadPotholeImage(dr[0]);
                        }
                    });
                    cur = 0;
                }

            }

            // Enable the buttons since the images are loaded
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bYes.setEnabled(true);
                    bNo.setEnabled(true);
                    bNotSure.setEnabled(true);
                }
            });
        } catch(JSONException e) {
            ////e.printStackTrace();
        }
    }

    /** Fetch image from URL and create drawable from it */
    public static Drawable LoadImageFromWeb(String url) {
        try {
            InputStream is = (InputStream) new URL(url).getContent();
            Drawable d = Drawable.createFromStream(is, "src name");
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** Send review response "y/n/m" and load next drawable in ImageView */
    public void sendReviewResponse(String res) {

        //Log.d(TAG, "Review by user " + res);

        // Send response as yes/no/not sure
        try {
            JSONObject review = reviews.getJSONObject(cur);
            HashMap<String,String> mp = new HashMap<>();
            mp.put("ComplaintId", review.get("id").toString());;
            mp.put("UserId", Config.userID);
            mp.put("Response", res);
            String result = rh.sendPostRequest(Config.SendReview, mp);
            if (result=="Error") {
                Toast.makeText(ReviewActivity.this, "No internet connection",
                        Toast.LENGTH_LONG).show();
            }
        } catch (JSONException e) {
            ////e.printStackTrace();
        }

        cur = (cur+1)%N;
        // N images are reviewed, download next N images
        if(cur == 0)
            getComplaintsForReview();
        // 'i'th image is reviewed, load 'i+1'th in the imageView
        else
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadPotholeImage(dr[cur]);
                }
            });
    }

    /** Load image into the image view */
    public void loadPotholeImage(Drawable d) {
        ivPothole.setImageDrawable(d);
    }
}