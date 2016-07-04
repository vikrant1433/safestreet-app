package in.ac.iitb.cse.carts.safestreet.menu;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import in.ac.iitb.cse.carts.safestreet.R;
import in.ac.iitb.cse.carts.safestreet.utilities.Config;
import in.ac.iitb.cse.carts.safestreet.utilities.MetaDataManager;
import in.ac.iitb.cse.carts.safestreet.utilities.PendingDataSender;

public class SettingsActivity extends AppCompatActivity implements View.OnClickListener {
    TextView tvAccountDetails, tvSizeOfData, tvVersion;
    MetaDataManager metaDataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.bSignOut).setOnClickListener(this);
        findViewById(R.id.bDeleteAccount).setOnClickListener(this);
        findViewById(R.id.bStopDataSender).setOnClickListener(this);
        findViewById(R.id.bDeleteData).setOnClickListener(this);

        tvAccountDetails = (TextView) findViewById(R.id.tvAccountDetails);
        metaDataManager = new MetaDataManager(this);
        String email = metaDataManager.getMetadata().get("userEmail");
        if (tvAccountDetails != null) {
            tvAccountDetails.setText(email);
        }

        tvSizeOfData = (TextView) findViewById(R.id.tvSizeOfData);
        String noOfFiles = "Number of pending files: " + (new File(Config.dataDirectory).listFiles().length);
        String sizeOfFiles = "Total size: " + folderSize(new File(Config.dataDirectory)) + "kB";
        if (tvSizeOfData != null) {
            tvSizeOfData.setText(noOfFiles + "\n" + sizeOfFiles);
        }

        tvVersion = (TextView) findViewById(R.id.tvVersion);
        try {
            tvVersion.setText(this.getPackageManager()
                    .getPackageInfo(this.getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            //e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    @Override
    public void onClick(View v) {
        AlertDialog.Builder builder;

        switch (v.getId()) {
            case R.id.bSignOut:
                builder = new AlertDialog.Builder(this);
                builder.setMessage("Are you sure you want to sign out?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                clearSharedPref();
                                Config.isSignedOut=true;

                                Toast.makeText(SettingsActivity.this,
                                        "Signed out", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                finish();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                dialog.dismiss();
                            }
                        });
                builder.show();
                break;
            case R.id.bDeleteAccount:
                builder = new AlertDialog.Builder(this);
                builder.setTitle("Delete account?")
                        .setMessage("Are you sure you want to delete the account?\n" +
                        "Warning: All data files will be deleted")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                deleteRecursive(new File(Config.dataDirectory));
                                clearSharedPref();
                                Config.isSignedOut=true;

                                Toast.makeText(SettingsActivity.this,
                                        "Account deleted", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                finish();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                dialog.dismiss();
                            }
                        });
                builder.show();
                break;
            case R.id.bStopDataSender:
                Intent intent = new Intent(this, PendingDataSender.class);
                stopService(intent);
                Toast.makeText(this, "Data sending will resume next " +
                        "time internet is connected", Toast.LENGTH_LONG).show();
                break;
            case R.id.bDeleteData:
                builder = new AlertDialog.Builder(this);
                builder.setMessage("Are you sure you want to delete all the data?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                deleteRecursive(new File(Config.dataDirectory));
                                Toast.makeText(SettingsActivity.this,
                                        "Data cleared", Toast.LENGTH_SHORT).show();
                                if (tvSizeOfData != null) {
                                    tvSizeOfData.setText("0kB Data");
                                }
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
                break;
        }
    }

    /** Delete directory or file */
    void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                child.delete();
            }
        }

        fileOrDirectory.delete();
    }

    /** Get the size of all files in a directory */
    long folderSize(File directory) {
        long length = 0;
        for (File file : directory.listFiles()) {
            length += file.length();
        }
        return length/1024;
    }

    void clearSharedPref() {
        metaDataManager.clearSharedPref();
    }
}
