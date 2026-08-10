package com.tridev.liveweather;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_DESTINATION = "selected_destination";

    private View pageContainer;
    private View pageHome;
    private View pageForecast;
    private View pageRadar;
    private View pageWallpaper;
    private View pageMore;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bindViews();
        applySystemInsets();
        setupBottomNavigation(savedInstanceState);
        setupQuickActions();
    }

    private void bindViews() {
        pageContainer = findViewById(R.id.pageContainer);
        pageHome = findViewById(R.id.pageHome);
        pageForecast = findViewById(R.id.pageForecast);
        pageRadar = findViewById(R.id.pageRadar);
        pageWallpaper = findViewById(R.id.pageWallpaper);
        pageMore = findViewById(R.id.pageMore);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void applySystemInsets() {
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            pageContainer.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    0
            );

            bottomNavigation.setPadding(
                    systemBars.left,
                    0,
                    systemBars.right,
                    systemBars.bottom
            );

            return insets;
        });
    }

    private void setupBottomNavigation(Bundle savedInstanceState) {
        bottomNavigation.setOnItemSelectedListener(item -> {
            renderDestination(item.getItemId());
            return true;
        });

        int destinationId = R.id.nav_home;
        if (savedInstanceState != null) {
            destinationId = savedInstanceState.getInt(
                    STATE_SELECTED_DESTINATION,
                    R.id.nav_home
            );
        }

        bottomNavigation.setSelectedItemId(destinationId);
        renderDestination(destinationId);
    }

    private void setupQuickActions() {
        View wallpaperAction = findViewById(R.id.homeWallpaperAction);
        if (wallpaperAction != null) {
            wallpaperAction.setOnClickListener(view ->
                    bottomNavigation.setSelectedItemId(R.id.nav_wallpaper)
            );
        }
    }

    private void renderDestination(int itemId) {
        pageHome.setVisibility(itemId == R.id.nav_home ? View.VISIBLE : View.GONE);
        pageForecast.setVisibility(itemId == R.id.nav_forecast ? View.VISIBLE : View.GONE);
        pageRadar.setVisibility(itemId == R.id.nav_radar ? View.VISIBLE : View.GONE);
        pageWallpaper.setVisibility(itemId == R.id.nav_wallpaper ? View.VISIBLE : View.GONE);
        pageMore.setVisibility(itemId == R.id.nav_more ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(
                STATE_SELECTED_DESTINATION,
                bottomNavigation.getSelectedItemId()
        );
        super.onSaveInstanceState(outState);
    }
}
