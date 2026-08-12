package com.tridev.liveweather.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tridev.liveweather.data.local.SavedCityStore;
import com.tridev.liveweather.domain.CityLocation;

import java.util.ArrayList;
import java.util.List;

/** Launcher-host configuration screen shared by Current and Forecast widgets. */
public final class WidgetConfigActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final List<CityLocation> savedCities = new ArrayList<>();
    private RadioButton activeSource;
    private RadioButton fixedSource;
    private Spinner citySpinner;
    private RadioButton glassAppearance;
    private RadioButton transparentAppearance;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);
        appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        savedCities.addAll(new SavedCityStore(this).loadSavedCities());
        setTitle("Configure Live Weather widget");
        setContentView(buildContent());
        restoreExistingConfig();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Weather source", 20, true);
        root.addView(title);
        TextView sourceHelp = text(
                "Follow the app's active weather, or lock this widget to one of your saved cities.",
                14,
                false
        );
        sourceHelp.setAlpha(0.75f);
        root.addView(sourceHelp, marginTop(6));

        RadioGroup sourceGroup = new RadioGroup(this);
        sourceGroup.setOrientation(RadioGroup.VERTICAL);
        activeSource = new RadioButton(this);
        activeSource.setText("Follow active weather");
        activeSource.setId(View.generateViewId());
        fixedSource = new RadioButton(this);
        fixedSource.setText("Fixed saved city");
        fixedSource.setId(View.generateViewId());
        sourceGroup.addView(activeSource);
        sourceGroup.addView(fixedSource);
        root.addView(sourceGroup, marginTop(10));

        citySpinner = new Spinner(this);
        List<String> cityLabels = new ArrayList<>();
        for (CityLocation city : savedCities) cityLabels.add(city.getDisplayName());
        if (cityLabels.isEmpty()) cityLabels.add("No saved cities yet");
        citySpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                cityLabels
        ));
        citySpinner.setEnabled(false);
        root.addView(citySpinner, marginTop(8));

        if (savedCities.isEmpty()) {
            fixedSource.setEnabled(false);
            TextView empty = text(
                    "Save a city from More → City Manager before using a fixed-city widget.",
                    13,
                    false
            );
            empty.setAlpha(0.7f);
            root.addView(empty, marginTop(6));
        }

        sourceGroup.setOnCheckedChangeListener((group, checkedId) ->
                citySpinner.setEnabled(checkedId == fixedSource.getId() && !savedCities.isEmpty())
        );

        TextView appearanceTitle = text("Appearance", 20, true);
        root.addView(appearanceTitle, marginTop(24));

        RadioGroup appearanceGroup = new RadioGroup(this);
        appearanceGroup.setOrientation(RadioGroup.VERTICAL);
        glassAppearance = new RadioButton(this);
        glassAppearance.setText("Glass card");
        glassAppearance.setId(View.generateViewId());
        transparentAppearance = new RadioButton(this);
        transparentAppearance.setText("Transparent");
        transparentAppearance.setId(View.generateViewId());
        appearanceGroup.addView(glassAppearance);
        appearanceGroup.addView(transparentAppearance);
        root.addView(appearanceGroup, marginTop(8));

        Button add = new Button(this);
        add.setText("Add / Update widget");
        add.setAllCaps(false);
        add.setOnClickListener(v -> saveAndFinish());
        root.addView(add, marginTop(26));

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, marginTop(8));

        return scroll;
    }

    private void restoreExistingConfig() {
        WidgetPreferences.Config config = new WidgetPreferences(this).load(appWidgetId);
        if (config.getSourceMode() == WidgetPreferences.SourceMode.FIXED_CITY
                && !savedCities.isEmpty()) {
            fixedSource.setChecked(true);
            citySpinner.setEnabled(true);
            int index = findMatchingCity(config);
            if (index >= 0) citySpinner.setSelection(index);
        } else {
            activeSource.setChecked(true);
        }

        if (config.getAppearance() == WidgetPreferences.Appearance.TRANSPARENT) {
            transparentAppearance.setChecked(true);
        } else {
            glassAppearance.setChecked(true);
        }
    }

    private int findMatchingCity(WidgetPreferences.Config config) {
        for (int i = 0; i < savedCities.size(); i++) {
            CityLocation city = savedCities.get(i);
            if (Math.abs(city.getLatitude() - config.getLatitude()) <= 0.0001d
                    && Math.abs(city.getLongitude() - config.getLongitude()) <= 0.0001d) {
                return i;
            }
        }
        return -1;
    }

    private void saveAndFinish() {
        WidgetPreferences preferences = new WidgetPreferences(this);
        WidgetPreferences.Appearance appearance = transparentAppearance.isChecked()
                ? WidgetPreferences.Appearance.TRANSPARENT
                : WidgetPreferences.Appearance.GLASS;

        if (fixedSource.isChecked()) {
            if (savedCities.isEmpty()) {
                Toast.makeText(this, "No saved city is available.", Toast.LENGTH_LONG).show();
                return;
            }
            int position = Math.max(0, Math.min(citySpinner.getSelectedItemPosition(), savedCities.size() - 1));
            preferences.saveFixedCity(appWidgetId, savedCities.get(position), appearance);
        } else {
            preferences.saveActive(appWidgetId, appearance);
        }

        WeatherWidgetUpdater.updateOne(this, appWidgetId);
        WidgetRefreshWorker.enqueue(this, appWidgetId);
        WidgetRefreshScheduler.schedule(this);

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(dp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
