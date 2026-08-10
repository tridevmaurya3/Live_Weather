package com.tridev.liveweather;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_DESTINATION = "selected_destination";

    private TextView destinationEyebrow;
    private TextView destinationTitle;
    private TextView destinationSubtitle;
    private TextView destinationCardTitle;
    private TextView destinationCardBody;
    private TextView destinationStatus;
    private NestedScrollView contentScroll;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        applySystemInsets();
        bindViews();
        setupBottomNavigation(savedInstanceState);
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        destinationEyebrow = findViewById(R.id.destinationEyebrow);
        destinationTitle = findViewById(R.id.destinationTitle);
        destinationSubtitle = findViewById(R.id.destinationSubtitle);
        destinationCardTitle = findViewById(R.id.destinationCardTitle);
        destinationCardBody = findViewById(R.id.destinationCardBody);
        destinationStatus = findViewById(R.id.destinationStatus);
        contentScroll = findViewById(R.id.contentScroll);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void setupBottomNavigation(Bundle savedInstanceState) {
        bottomNavigation.setOnItemSelectedListener(item -> {
            renderDestination(item.getItemId());
            return true;
        });

        int destinationId = R.id.nav_home;
        if (savedInstanceState != null) {
            destinationId = savedInstanceState.getInt(STATE_SELECTED_DESTINATION, R.id.nav_home);
        }
        bottomNavigation.setSelectedItemId(destinationId);
        renderDestination(destinationId);
    }

    private void renderDestination(int itemId) {
        if (itemId == R.id.nav_forecast) {
            setDestination(
                    R.string.forecast_eyebrow,
                    R.string.forecast_title,
                    R.string.forecast_subtitle,
                    R.string.forecast_card_title,
                    R.string.forecast_card_body,
                    R.string.forecast_status
            );
        } else if (itemId == R.id.nav_radar) {
            setDestination(
                    R.string.radar_eyebrow,
                    R.string.radar_title,
                    R.string.radar_subtitle,
                    R.string.radar_card_title,
                    R.string.radar_card_body,
                    R.string.radar_status
            );
        } else if (itemId == R.id.nav_wallpaper) {
            setDestination(
                    R.string.wallpaper_eyebrow,
                    R.string.wallpaper_title,
                    R.string.wallpaper_subtitle,
                    R.string.wallpaper_card_title,
                    R.string.wallpaper_card_body,
                    R.string.wallpaper_status
            );
        } else if (itemId == R.id.nav_more) {
            setDestination(
                    R.string.more_eyebrow,
                    R.string.more_title,
                    R.string.more_subtitle,
                    R.string.more_card_title,
                    R.string.more_card_body,
                    R.string.more_status
            );
        } else {
            setDestination(
                    R.string.foundation_eyebrow,
                    R.string.foundation_title,
                    R.string.foundation_subtitle,
                    R.string.foundation_card_title,
                    R.string.foundation_card_body,
                    R.string.foundation_status
            );
        }

        contentScroll.scrollTo(0, 0);
    }

    private void setDestination(
            int eyebrowRes,
            int titleRes,
            int subtitleRes,
            int cardTitleRes,
            int cardBodyRes,
            int statusRes
    ) {
        destinationEyebrow.setText(eyebrowRes);
        destinationTitle.setText(titleRes);
        destinationSubtitle.setText(subtitleRes);
        destinationCardTitle.setText(cardTitleRes);
        destinationCardBody.setText(cardBodyRes);
        destinationStatus.setText(statusRes);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SELECTED_DESTINATION, bottomNavigation.getSelectedItemId());
        super.onSaveInstanceState(outState);
    }
}
