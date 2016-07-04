package in.ac.iitb.cse.carts.safestreet;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;

import in.ac.iitb.cse.carts.safestreet.utilities.Config;
import in.ac.iitb.cse.carts.safestreet.utilities.MetaDataManager;
import in.ac.iitb.cse.carts.safestreet.utilities.RequestHandler;

public class SignInActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private static final String TAG = "SignInActivity";
    private static final int RC_SIGN_IN = 9001;

    GoogleApiClient mGoogleApiClient;
    GoogleSignInAccount acct;
    TextView tvWelcome;
    EditText etPhoneNo;
    ProgressBar progressBar;
    String phoneNo;
    MetaDataManager metaDataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Change the splash screen
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        // Initialize shared preferences and backup file
        metaDataManager = new MetaDataManager(this);

        // Start the introduction screen during sign up
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(SignInActivity.this, Intro.class);
                startActivity(i);
            }
        });

        // If user has signed in before, go to the home screen
        String userID = metaDataManager.getUserID();
        if (userID==null) {
            //Log.d(TAG, "userID not found in metadata");
            t.start();

            // Configure sign-in to request the user's ID, email address, and basic
            // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();

            // Build a GoogleApiClient with access to the Google Sign-In API and the
            // options specified by gso.
            mGoogleApiClient = new GoogleApiClient.Builder(SignInActivity.this)
                    .enableAutoManage(SignInActivity.this /* FragmentActivity */,
                            SignInActivity.this /* OnConnectionFailedListener */)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                    .build();

            SignInButton signInButton = (SignInButton) findViewById(R.id.sign_in_button);
            signInButton.setSize(SignInButton.SIZE_STANDARD);
            signInButton.setScopes(gso.getScopeArray());
        }
        else {
            //Log.d(TAG, "userID found in metadata: " + userID);
            Config.setUserID(userID);

            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
            finish();
        }

        // Views
        tvWelcome = (TextView) findViewById(R.id.tvWelcome);
        etPhoneNo = (EditText) findViewById(R.id.etPhoneNo);

        // Button listeners
        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.disconnect_button).setOnClickListener(this);
        findViewById(R.id.sign_up_button).setOnClickListener(this);

        // Progress Bar
        progressBar = (ProgressBar) findViewById(R.id.progressBarSignIn);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Google automatic sign in
        // This code is not working, but the app can work without this
        /*OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);

        if (opr.isDone()) {
            //Log.d(TAG, "Cached sign in found");
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            GoogleSignInResult result = opr.get();
            handleSignInResult(result);
        }
        else {
            //Log.d(TAG, "Cross device sign in found");
            // If the user has not previously signed in on this device or the sign-in has expired,
            // this asynchronous branch will attempt to sign in the user silently.  Cross-device
            // single sign-on will occur in this branch.
            opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(GoogleSignInResult googleSignInResult) {
                    handleSignInResult(googleSignInResult);
                }
            });
        }*/
    }

    @Override
    protected void onPause() {
        super.onPause();
        // User has signed in but not yet signed up
        if (acct!=null && Config.userID==null)
            signOut();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }

    /** Called after authorizing google sign in */
    private void handleSignInResult(GoogleSignInResult result) {

        /*  Check if the user is registered on the server before
            If yes, get his userID
            else, sign him up with his mobile number
            */
        class SignInAutomatic extends AsyncTask<GoogleSignInAccount, Void, String> {

            RequestHandler rh = new RequestHandler();
            String email = acct.getEmail();

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected String doInBackground(GoogleSignInAccount... params) {
                // Send email to server, get userID in response
                HashMap<String,String> data = new HashMap<>();
                data.put("Email", email);
                String result = rh.sendPostRequest(Config.GetUserId, data);
                return result;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                progressBar.setVisibility(View.GONE);

                try {
                    // No internet
                    if (s=="Error") {
                        Toast.makeText(SignInActivity.this, "Please check your " +
                                "internet connection", Toast.LENGTH_LONG).show();
                        return;
                    }
                    // userID not found, user must sign up
                    else if (s=="NULL") {
                        //Log.d(TAG, "Response from server: userID not found");
                        tvWelcome.setText("Hello " + acct.getDisplayName());
                        updateUI(true, true);
                        return;
                    }

                    // userID found on server i.e. account exists
                    JSONObject userDetails = new JSONObject(s);
                    Config.setUserID(userDetails.get("id").toString());
                    initializeMetaData(Config.userID, email);
                    //Log.d(TAG, "Response from server: userID " + Config.userID);

                    // Go to home screen
                    Intent intent = new Intent(SignInActivity.this, HomeActivity.class);
                    startActivity(intent);
                    finish();
                } catch (JSONException e) {
                    //e.printStackTrace();
                }
            }
        }

        if (!result.isSuccess())
            updateUI(false, false);
        else {
            acct = result.getSignInAccount();
            SignInAutomatic user = new SignInAutomatic();
            user.execute(acct);
        }
    }

    /**
     * Create account for user on the server
     * Fields to be sent - Name, Email, Phone no, City
     * Response received - userID
     */
    private void signUp() {
        class SignUp extends AsyncTask<GoogleSignInAccount, Void, String> {

            RequestHandler rh = new RequestHandler();
            String email = acct.getEmail();

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected String doInBackground(GoogleSignInAccount... params) {
                HashMap<String,String> data = new HashMap<>();
                data.put("Name", params[0].getDisplayName());
                data.put("Phone", phoneNo);
                data.put("Email", params[0].getEmail());
                data.put("City","mum");

                String result = rh.sendPostRequest(Config.AddUser, data);
                return result;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                progressBar.setVisibility(View.GONE);

                try {
                    // Internet not connecting
                    if (s=="Error") {
                        Toast.makeText(SignInActivity.this, "Please check " +
                                "your internet connection", Toast.LENGTH_LONG).show();
                        signOut();
                        return;
                    }

                    // Sign up successful
                    JSONObject userDetails = new JSONObject(s);
                    Config.setUserID(userDetails.get("id").toString());
                    initializeMetaData(Config.userID, email);
                    //Log.d(TAG, "Sign up successful. userID" + Config.userID);

                    // Go to home screen
                    Intent intent = new Intent(SignInActivity.this, HomeActivity.class);
                    startActivity(intent);
                    finish();
                } catch (JSONException e) {
                    //e.printStackTrace();
                }
            }
        }

        SignUp newUser = new SignUp();
        newUser.execute(acct);
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        updateUI(false, false);
                        tvWelcome.setText("Welcome");
                    }
                });
    }

    private void revokeAccess() {
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        updateUI(false, false);
                        tvWelcome.setText("Welcome");
                    }
                });
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        //Log.d(TAG, "Google API cannot be connected. Error: " + connectionResult.getErrorMessage());
    }

    /**
     * If not signed in, show only sign in button,
     * If signed in, show sign up, sign out and disconnect button
     */
    private void updateUI(boolean signedIn, boolean firstTime) {
        if (signedIn) {
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.llSignOut).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.llSignOut).setVisibility(View.GONE);
        }

        if (firstTime)
            findViewById(R.id.llSignUp).setVisibility(View.VISIBLE);
        else
            findViewById(R.id.llSignUp).setVisibility(View.GONE);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                if (Config.isConnected(this))
                    signIn();
                else
                    Toast.makeText(this, "Connect internet to sign in", Toast.LENGTH_LONG).show();
                break;
            case R.id.sign_out_button:
                signOut();
                break;
            case R.id.disconnect_button:
                revokeAccess();
                break;
            case R.id.sign_up_button:
                phoneNo = etPhoneNo.getText().toString();
                if (phoneNo.length()==10)
                    signUp();
                else
                    Toast.makeText(this, "Enter a 10-digit mobile number",
                            Toast.LENGTH_LONG).show();
                break;
            default:
                break;
        }
    }

    /** Create backup file, create shared preferences, update email and userID */
    private void initializeMetaData(String userID, String email) {

        File dir = new File(Config.dataDirectory);
        dir.mkdir();

        HashMap<String, String> map = new HashMap<>();
        map.put("userID", userID);
        map.put("userEmail", email);
        map.put("rideNo", null);
        map.put("currentPendingFile", null);
        map.put("linesAlreadySent", null);
        map.put("distance","0");
        map.put("time","0");
        map.put("potholes","0");

        metaDataManager.writeMetadata(map);
    }
}