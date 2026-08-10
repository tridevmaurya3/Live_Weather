package com.tridev.liveweather.ui.city;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.tridev.liveweather.R;
import com.tridev.liveweather.domain.CityLocation;
import com.tridev.liveweather.domain.CityUiState;

import java.util.List;

public final class CityScreenRenderer {

    private final Activity activity;
    private final EditText searchInput;
    private final TextView searchButton;
    private final TextView useCurrentButton;
    private final TextView activeLocation;
    private final TextView status;
    private final LinearLayout searchResults;
    private final LinearLayout savedCities;

    private Callbacks callbacks;

    public CityScreenRenderer(@NonNull Activity activity) {
        this.activity = activity;
        searchInput = activity.findViewById(R.id.citySearchInput);
        searchButton = activity.findViewById(R.id.citySearchButton);
        useCurrentButton = activity.findViewById(R.id.cityUseCurrentButton);
        activeLocation = activity.findViewById(R.id.cityActiveLocation);
        status = activity.findViewById(R.id.citySearchStatus);
        searchResults = activity.findViewById(R.id.citySearchResults);
        savedCities = activity.findViewById(R.id.citySavedList);
    }

    public void setCallbacks(@NonNull Callbacks callbacks) {
        this.callbacks = callbacks;

        searchButton.setOnClickListener(view -> submitSearch());
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch();
                return true;
            }
            return false;
        });
        useCurrentButton.setOnClickListener(view -> callbacks.onUseCurrentLocation());
    }

    public void render(@NonNull CityUiState state) {
        CityLocation selected = state.getSelectedCity();
        activeLocation.setText(
                selected == null
                        ? activity.getString(R.string.city_active_current)
                        : selected.getDisplayName()
        );

        if (state.isLoading()) {
            status.setText(R.string.city_searching);
        } else if (state.getMessage() != null) {
            status.setText(state.getMessage());
        } else {
            status.setText(R.string.city_search_hint_status);
        }

        renderSearchResults(state.getSearchResults(), state.getSavedCities(), selected);
        renderSavedCities(state.getSavedCities(), selected);
    }

    private void submitSearch() {
        if (callbacks == null) {
            return;
        }
        callbacks.onSearch(searchInput.getText().toString());
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(
                Context.INPUT_METHOD_SERVICE
        );
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    private void renderSearchResults(
            @NonNull List<CityLocation> results,
            @NonNull List<CityLocation> saved,
            CityLocation selected
    ) {
        searchResults.removeAllViews();
        if (results.isEmpty()) {
            addEmptyMessage(searchResults, R.string.city_no_search_results);
            return;
        }

        for (CityLocation city : results) {
            boolean isSaved = contains(saved, city);
            boolean isSelected = selected != null && selected.sameIdentity(city);
            searchResults.addView(createCityCard(city, isSaved, isSelected, false));
        }
    }

    private void renderSavedCities(
            @NonNull List<CityLocation> saved,
            CityLocation selected
    ) {
        savedCities.removeAllViews();
        if (saved.isEmpty()) {
            addEmptyMessage(savedCities, R.string.city_no_saved_cities);
            return;
        }

        for (CityLocation city : saved) {
            boolean isSelected = selected != null && selected.sameIdentity(city);
            savedCities.addView(createCityCard(city, true, isSelected, true));
        }
    }

    private LinearLayout createCityCard(
            @NonNull CityLocation city,
            boolean isSaved,
            boolean isSelected,
            boolean savedSection
    ) {
        LinearLayout card = new LinearLayout(activity);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(8);
        card.setLayoutParams(cardParams);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundResource(R.drawable.bg_weather_card_compact);

        TextView title = new TextView(activity);
        title.setText(city.getDisplayName());
        title.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        title.setTextSize(15f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView meta = new TextView(activity);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = dp(4);
        meta.setLayoutParams(metaParams);
        String timezone = city.getTimezone() == null ? "" : " · " + city.getTimezone();
        meta.setText(city.getCoordinateLabel() + timezone);
        meta.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_tertiary));
        meta.setTextSize(12f);
        card.addView(meta);

        LinearLayout actions = new LinearLayout(activity);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionsParams.topMargin = dp(10);
        actions.setLayoutParams(actionsParams);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        card.addView(actions);

        TextView use = createActionButton(
                isSelected ? activity.getString(R.string.city_active) : activity.getString(R.string.city_use),
                true
        );
        use.setEnabled(!isSelected);
        use.setAlpha(isSelected ? 0.65f : 1f);
        use.setOnClickListener(view -> {
            if (callbacks != null && !isSelected) {
                callbacks.onUseCity(city);
            }
        });
        actions.addView(use);

        if (savedSection) {
            TextView remove = createActionButton(activity.getString(R.string.city_remove), false);
            remove.setOnClickListener(view -> {
                if (callbacks != null) {
                    callbacks.onRemoveCity(city);
                }
            });
            actions.addView(remove);
        } else {
            TextView save = createActionButton(
                    isSaved ? activity.getString(R.string.city_saved) : activity.getString(R.string.city_save),
                    false
            );
            save.setEnabled(!isSaved);
            save.setAlpha(isSaved ? 0.6f : 1f);
            save.setOnClickListener(view -> {
                if (callbacks != null && !isSaved) {
                    callbacks.onSaveCity(city);
                }
            });
            actions.addView(save);
        }

        return card;
    }

    private TextView createActionButton(@NonNull String label, boolean primary) {
        TextView button = new TextView(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(38)
        );
        params.leftMargin = dp(8);
        button.setLayoutParams(params);
        button.setMinWidth(dp(72));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setGravity(Gravity.CENTER);
        button.setText(label);
        button.setTextSize(12f);
        button.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        button.setBackgroundResource(
                primary ? R.drawable.bg_weather_button_primary : R.drawable.bg_weather_chip
        );
        return button;
    }

    private void addEmptyMessage(@NonNull LinearLayout container, int textRes) {
        TextView empty = new TextView(activity);
        empty.setPadding(0, dp(10), 0, dp(6));
        empty.setText(textRes);
        empty.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_tertiary));
        empty.setTextSize(13f);
        container.addView(empty);
    }

    private boolean contains(@NonNull List<CityLocation> cities, @NonNull CityLocation city) {
        for (CityLocation existing : cities) {
            if (existing != null && existing.sameIdentity(city)) {
                return true;
            }
        }
        return false;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public interface Callbacks {
        void onSearch(@NonNull String query);

        void onUseCity(@NonNull CityLocation city);

        void onSaveCity(@NonNull CityLocation city);

        void onRemoveCity(@NonNull CityLocation city);

        void onUseCurrentLocation();
    }
}
