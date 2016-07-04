package in.ac.iitb.cse.carts.safestreet.menu;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import in.ac.iitb.cse.carts.safestreet.R;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }
}
