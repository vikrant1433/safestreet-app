package in.ac.iitb.cse.carts.safestreet;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;

public class Intro extends AppIntro {

    private static final String TAG = "IntroActivity";

    @Override
    public void init(@Nullable Bundle savedInstanceState) {
        String title1 = "Welcome to SafeStreet";
        String description1 = "An app by IIT Bombay aiming at smooth and safe roads";

        String title2 = "Report";
        String description2 = "Click a photo of a pothole/bump, and submit";

        String title3 = "Review";
        String description3 = "Check other people's submitted photo, click No if it is spam, else Yes";

        String title4 = "Automatic detection";
        String description4 = "Click 'start ride' in the widget when you are riding on a bike/car, and click 'stop ride' once done.";

        addSlide(AppIntroFragment.newInstance(title1, description1, R.drawable.intro_smoothroad, Color.parseColor("#C9ABED")));
        addSlide(AppIntroFragment.newInstance(title2, description2, R.drawable.intro_report, Color.parseColor("#BA99E1")));
        addSlide(AppIntroFragment.newInstance(title3, description3, R.drawable.intro_review, Color.parseColor("#A57FD4")));
        addSlide(AppIntroFragment.newInstance(title4, description4, R.drawable.intro_automaticdetection, Color.parseColor("#9265C9")));
    }

    @Override
    public void onSkipPressed() {
        finish();
    }

    @Override
    public void onNextPressed() {

    }

    @Override
    public void onDonePressed() {
        finish();
    }

    @Override
    public void onSlideChanged() {

    }

}
