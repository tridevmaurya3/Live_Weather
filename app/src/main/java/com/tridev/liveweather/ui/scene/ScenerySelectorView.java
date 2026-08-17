package com.tridev.liveweather.ui.scene;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.local.SceneryFavoritesPreferences;
import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.domain.scene.SceneryMode;
import com.tridev.liveweather.domain.scene.SceneryRuntimeState;
import com.tridev.liveweather.domain.scene.SceneryVariantRuntimeState;

import java.util.List;

/**
 * Professional scenery library for the Wallpaper page.
 *
 * S12 preserves S11 one-tap visual scene selection and adds a non-destructive full-screen
 * detail preview. Long-pressing a scene card previews that candidate without applying it;
 * the dialog stages variation changes locally until Use scene is pressed.
 */
public final class ScenerySelectorView extends LinearLayout {

    private static final SceneryMode[] SELECTABLE_MODES = {
            SceneryMode.AUTO,
            SceneryMode.OPEN_SKY,
            SceneryMode.NATURAL_HILLS,
            SceneryMode.VILLAGE,
            SceneryMode.FARM_CROPS,
            SceneryMode.RIVER_LAKE,
            SceneryMode.FLOWERS_GREENERY,
            SceneryMode.URBAN_BUILDINGS
    };

    private static final SceneryMode[] PRESET_MODES = {
            SceneryMode.OPEN_SKY,
            SceneryMode.FLOWERS_GREENERY,
            SceneryMode.RIVER_LAKE,
            SceneryMode.URBAN_BUILDINGS
    };

    private static final int[] PRESET_VARIANTS = {0, 1, 2, 0};
    private static final int[] PRESET_LABELS = {
            R.string.wallpaper_scenery_preset_sky,
            R.string.wallpaper_scenery_preset_green,
            R.string.wallpaper_scenery_preset_water,
            R.string.wallpaper_scenery_preset_city
    };

    private final WallpaperPreferences preferences;
    private final SceneryFavoritesPreferences favoritesPreferences;
    private final TextView selectionSummary;
    private final TextView selectionDetail;
    private final TextView variationSummary;
    private final TextView favoriteAction;
    private final TextView favoritesHint;
    private final HorizontalScrollView favoritesScroller;
    private final LinearLayout favoritesRow;
    private final SceneryPreviewCardView[] sceneCards =
            new SceneryPreviewCardView[SELECTABLE_MODES.length];
    private final TextView[] variationChips =
            new TextView[SceneryVariantRuntimeState.VARIANT_COUNT];

    @NonNull
    private SceneryMode selectedMode;
    private int selectedVariant;

    public ScenerySelectorView(@NonNull Context context) {
        this(context, null);
    }

    public ScenerySelectorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScenerySelectorView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        preferences = new WallpaperPreferences(context);
        favoritesPreferences = new SceneryFavoritesPreferences(context);
        selectedMode = preferences.load().getSceneryMode();
        selectedVariant = SceneryVariantRuntimeState.get();

        selectionSummary = new TextView(context);
        selectionSummary.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        selectionSummary.setTextColor(ContextCompat.getColor(context, R.color.weather_aqua));
        selectionSummary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        addView(selectionSummary, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        selectionDetail = new TextView(context);
        selectionDetail.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        selectionDetail.setTextColor(ContextCompat.getColor(context, R.color.weather_text_secondary));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(3);
        addView(selectionDetail, detailParams);

        TextView libraryTitle = createSectionLabel(context, R.string.wallpaper_scenery_library_title);
        LinearLayout.LayoutParams libraryTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        libraryTitleParams.topMargin = dp(12);
        addView(libraryTitle, libraryTitleParams);

        TextView libraryHint = new TextView(context);
        libraryHint.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        libraryHint.setTextColor(ContextCompat.getColor(context, R.color.weather_text_tertiary));
        libraryHint.setText(R.string.wallpaper_scenery_library_hint);
        LinearLayout.LayoutParams libraryHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        libraryHintParams.topMargin = dp(3);
        addView(libraryHint, libraryHintParams);

        addView(buildSceneScroller(context), new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        variationSummary = createSectionLabel(context, R.string.wallpaper_scenery_variation_format);
        LinearLayout.LayoutParams variationSummaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        variationSummaryParams.topMargin = dp(10);
        addView(variationSummary, variationSummaryParams);

        addView(buildVariationRow(context), new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView fullPreviewAction = createActionChip(
                context,
                getResources().getString(R.string.wallpaper_scenery_full_preview)
        );
        fullPreviewAction.setContentDescription(
                getResources().getString(R.string.wallpaper_scenery_full_preview_accessibility)
        );
        fullPreviewAction.setOnClickListener(view -> {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showScenePreview(selectedMode);
        });
        LinearLayout.LayoutParams fullPreviewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)
        );
        fullPreviewParams.topMargin = dp(8);
        addView(fullPreviewAction, fullPreviewParams);

        TextView quickTitle = createSectionLabel(context, R.string.wallpaper_scenery_quick_presets);
        LinearLayout.LayoutParams quickTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        quickTitleParams.topMargin = dp(12);
        addView(quickTitle, quickTitleParams);

        addView(buildQuickPresetScroller(context), new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout favoritesHeader = new LinearLayout(context);
        favoritesHeader.setOrientation(HORIZONTAL);
        favoritesHeader.setGravity(Gravity.CENTER_VERTICAL);
        favoritesHeader.setPadding(0, dp(12), 0, 0);

        TextView favoritesTitle = createSectionLabel(context, R.string.wallpaper_scenery_favorites_title);
        favoritesHeader.addView(favoritesTitle, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
        ));

        favoriteAction = new TextView(context);
        favoriteAction.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        favoriteAction.setGravity(Gravity.CENTER);
        favoriteAction.setSingleLine(true);
        favoriteAction.setMinHeight(dp(48));
        favoriteAction.setMinWidth(dp(116));
        favoriteAction.setPadding(dp(12), 0, dp(12), 0);
        favoriteAction.setClickable(true);
        favoriteAction.setFocusable(true);
        favoriteAction.setBackgroundResource(R.drawable.bg_weather_chip);
        favoriteAction.setOnClickListener(view -> toggleCurrentFavorite());
        favoritesHeader.addView(favoriteAction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)
        ));
        addView(favoritesHeader, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        favoritesHint = new TextView(context);
        favoritesHint.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        favoritesHint.setTextColor(ContextCompat.getColor(context, R.color.weather_text_secondary));
        favoritesHint.setText(R.string.wallpaper_scenery_favorites_empty);
        addView(favoritesHint, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        favoritesScroller = new HorizontalScrollView(context);
        favoritesScroller.setHorizontalScrollBarEnabled(false);
        favoritesScroller.setVerticalScrollBarEnabled(false);
        favoritesScroller.setFillViewport(false);
        favoritesScroller.setClipToPadding(false);
        favoritesScroller.setOverScrollMode(OVER_SCROLL_NEVER);

        favoritesRow = new LinearLayout(context);
        favoritesRow.setOrientation(HORIZONTAL);
        favoritesRow.setGravity(Gravity.CENTER_VERTICAL);
        favoritesRow.setPadding(0, dp(6), 0, 0);
        favoritesScroller.addView(favoritesRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addView(favoritesScroller, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        renderSelection();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshFromPreferences();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            refreshFromPreferences();
        }
    }

    @NonNull
    private HorizontalScrollView buildSceneScroller(@NonNull Context context) {
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(OVER_SCROLL_NEVER);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(0, dp(8), 0, 0);

        for (int index = 0; index < SELECTABLE_MODES.length; index++) {
            SceneryMode mode = SELECTABLE_MODES[index];
            SceneryPreviewCardView card = createSceneCard(context, mode);
            sceneCards[index] = card;

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(136),
                    dp(152)
            );
            if (index < SELECTABLE_MODES.length - 1) params.setMarginEnd(dp(10));
            row.addView(card, params);
        }

        scroller.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroller;
    }

    @NonNull
    private LinearLayout buildVariationRow(@NonNull Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);

        for (int variant = 0; variant < SceneryVariantRuntimeState.VARIANT_COUNT; variant++) {
            TextView chip = createVariationChip(context, variant);
            variationChips[variant] = chip;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
            if (variant < SceneryVariantRuntimeState.VARIANT_COUNT - 1) params.setMarginEnd(dp(8));
            row.addView(chip, params);
        }
        return row;
    }

    @NonNull
    private HorizontalScrollView buildQuickPresetScroller(@NonNull Context context) {
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(OVER_SCROLL_NEVER);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);

        for (int index = 0; index < PRESET_MODES.length; index++) {
            final int presetIndex = index;
            TextView chip = createActionChip(context, getResources().getString(PRESET_LABELS[index]));
            chip.setContentDescription(getResources().getString(
                    R.string.wallpaper_scenery_preset_accessibility,
                    getResources().getString(PRESET_LABELS[index])
            ));
            chip.setOnClickListener(view -> applyPreset(presetIndex));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48)
            );
            if (index < PRESET_MODES.length - 1) params.setMarginEnd(dp(8));
            row.addView(chip, params);
        }

        scroller.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroller;
    }

    @NonNull
    private SceneryPreviewCardView createSceneCard(
            @NonNull Context context,
            @NonNull SceneryMode mode
    ) {
        SceneryPreviewCardView card = new SceneryPreviewCardView(context);
        card.setScene(mode, getResources().getString(labelRes(mode)));
        card.setVariant(selectedVariant);
        card.setAutoResolvedMode(SceneryRuntimeState.get());
        card.setContentDescription(getResources().getString(
                R.string.wallpaper_scenery_preview_accessibility,
                getResources().getString(labelRes(mode)),
                selectedVariant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));
        card.setOnClickListener(view -> selectMode(mode));
        card.setOnLongClickListener(view -> {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showScenePreview(mode);
            return true;
        });
        return card;
    }

    @NonNull
    private TextView createVariationChip(@NonNull Context context, int variant) {
        TextView chip = createActionChip(context, String.valueOf(variant + 1));
        chip.setMinWidth(dp(48));
        chip.setPadding(0, 0, 0, 0);
        chip.setContentDescription(getResources().getString(
                R.string.wallpaper_scenery_variation_select_accessibility,
                variant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));
        chip.setOnClickListener(view -> selectVariation(variant));
        return chip;
    }

    @NonNull
    private TextView createActionChip(@NonNull Context context, @NonNull String text) {
        TextView chip = new TextView(context);
        chip.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setMinHeight(dp(48));
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setBackgroundResource(R.drawable.bg_weather_chip);
        chip.setTextColor(ContextCompat.getColor(context, R.color.weather_text_primary));
        return chip;
    }

    @NonNull
    private TextView createSectionLabel(@NonNull Context context, int textRes) {
        TextView label = new TextView(context);
        label.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        label.setTextColor(ContextCompat.getColor(context, R.color.weather_text_secondary));
        if (textRes == R.string.wallpaper_scenery_variation_format) {
            label.setText("");
        } else {
            label.setText(textRes);
        }
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private void refreshFromPreferences() {
        selectedMode = preferences.load().getSceneryMode();
        selectedVariant = SceneryVariantRuntimeState.get();
        renderSelection();
    }

    private void selectMode(@NonNull SceneryMode mode) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        if (selectedMode == mode) {
            if (mode == SceneryMode.AUTO) preferences.refreshRuntimeScenery();
            renderSelection();
            return;
        }

        WallpaperPreferences.Options current = preferences.load();
        WallpaperPreferences.Options updated = current.withSceneryMode(mode);
        preferences.save(updated);
        selectedMode = mode;
        renderSelection();

        announceForAccessibility(getResources().getString(
                R.string.wallpaper_scenery_changed_accessibility,
                getResources().getString(labelRes(mode))
        ));
    }

    private void selectVariation(int variant) {
        if (selectedVariant == variant) return;

        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        selectedVariant = SceneryVariantRuntimeState.setAndPersist(getContext(), variant);
        if (selectedMode == SceneryMode.AUTO) preferences.refreshRuntimeScenery();
        renderSelection();

        announceForAccessibility(getResources().getString(
                R.string.wallpaper_scenery_variation_changed_accessibility,
                selectedVariant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));
    }

    private void showScenePreview(@NonNull SceneryMode mode) {
        SceneryPreviewDialog.show(
                getContext(),
                mode,
                selectedVariant,
                SceneryRuntimeState.get(),
                getResources().getString(labelRes(mode)),
                (useMode, useVariant) -> {
                    applySceneAndVariant(useMode, useVariant);
                    announceForAccessibility(getResources().getString(
                            R.string.wallpaper_scenery_changed_accessibility,
                            getResources().getString(labelRes(useMode))
                    ));
                }
        );
    }

    private void applyPreset(int presetIndex) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        applySceneAndVariant(PRESET_MODES[presetIndex], PRESET_VARIANTS[presetIndex]);
    }

    private void applyFavorite(@NonNull SceneryFavoritesPreferences.Favorite favorite) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        applySceneAndVariant(favorite.getMode(), favorite.getVariant());
    }

    private void applySceneAndVariant(@NonNull SceneryMode mode, int variant) {
        selectedVariant = SceneryVariantRuntimeState.setAndPersist(getContext(), variant);
        WallpaperPreferences.Options current = preferences.load();
        WallpaperPreferences.Options updated = current.withSceneryMode(mode);
        preferences.save(updated);
        selectedMode = mode;
        renderSelection();
    }

    private void toggleCurrentFavorite() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        SceneryMode favoriteMode = currentConcreteMode();
        boolean saved = favoritesPreferences.toggle(favoriteMode, selectedVariant);
        renderFavorites();
        announceForAccessibility(getResources().getString(
                saved
                        ? R.string.wallpaper_scenery_favorite_saved_accessibility
                        : R.string.wallpaper_scenery_favorite_removed_accessibility
        ));
    }

    private void renderSelection() {
        SceneryMode resolved = SceneryRuntimeState.get();
        if (selectedMode == SceneryMode.AUTO) {
            selectionSummary.setText(getResources().getString(
                    R.string.wallpaper_scenery_auto_selected_format,
                    getResources().getString(labelRes(resolved))
            ));
            selectionDetail.setText(R.string.wallpaper_scenery_auto_detail);
        } else {
            selectionSummary.setText(getResources().getString(
                    R.string.wallpaper_scenery_selected_format,
                    getResources().getString(labelRes(selectedMode))
            ));
            selectionDetail.setText(R.string.wallpaper_scenery_manual_detail);
        }

        for (int index = 0; index < SELECTABLE_MODES.length; index++) {
            SceneryPreviewCardView card = sceneCards[index];
            SceneryMode mode = SELECTABLE_MODES[index];
            card.setVariant(selectedVariant);
            card.setAutoResolvedMode(resolved);
            card.setSelected(mode == selectedMode);
            card.setActivated(mode == selectedMode);
            card.setContentDescription(getResources().getString(
                    R.string.wallpaper_scenery_preview_accessibility,
                    getResources().getString(labelRes(mode)),
                    selectedVariant + 1,
                    SceneryVariantRuntimeState.VARIANT_COUNT
            ));
        }

        renderVariation();
        renderFavorites();
    }

    private void renderVariation() {
        variationSummary.setText(getResources().getString(
                R.string.wallpaper_scenery_variation_format,
                selectedVariant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));

        for (int variant = 0; variant < variationChips.length; variant++) {
            applyChipState(variationChips[variant], variant == selectedVariant);
        }
    }

    private void renderFavorites() {
        SceneryMode concrete = currentConcreteMode();
        boolean currentSaved = favoritesPreferences.contains(concrete, selectedVariant);
        favoriteAction.setText(
                currentSaved
                        ? R.string.wallpaper_scenery_favorite_saved
                        : R.string.wallpaper_scenery_favorite_save
        );
        applyChipState(favoriteAction, currentSaved);

        List<SceneryFavoritesPreferences.Favorite> favorites = favoritesPreferences.load();
        favoritesRow.removeAllViews();
        if (favorites.isEmpty()) {
            favoritesHint.setVisibility(VISIBLE);
            favoritesScroller.setVisibility(GONE);
            return;
        }

        favoritesHint.setVisibility(GONE);
        favoritesScroller.setVisibility(VISIBLE);
        for (int index = 0; index < favorites.size(); index++) {
            SceneryFavoritesPreferences.Favorite favorite = favorites.get(index);
            TextView chip = createActionChip(
                    getContext(),
                    getResources().getString(
                            R.string.wallpaper_scenery_favorite_format,
                            getResources().getString(labelRes(favorite.getMode())),
                            favorite.getVariant() + 1
                    )
            );
            chip.setContentDescription(
                    getResources().getString(
                            R.string.wallpaper_scenery_favorite_accessibility,
                            getResources().getString(labelRes(favorite.getMode())),
                            favorite.getVariant() + 1
                    )
                            + ". "
                            + getResources().getString(
                            R.string.wallpaper_scenery_favorite_remove_accessibility,
                            getResources().getString(labelRes(favorite.getMode())),
                            favorite.getVariant() + 1
                    )
            );
            chip.setOnClickListener(view -> applyFavorite(favorite));
            chip.setOnLongClickListener(view -> {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                favoritesPreferences.remove(favorite);
                renderFavorites();
                announceForAccessibility(getResources().getString(
                        R.string.wallpaper_scenery_favorite_removed_accessibility
                ));
                return true;
            });

            boolean selected = selectedMode != SceneryMode.AUTO
                    && selectedMode == favorite.getMode()
                    && selectedVariant == favorite.getVariant();
            applyChipState(chip, selected);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48)
            );
            if (index < favorites.size() - 1) params.setMarginEnd(dp(8));
            favoritesRow.addView(chip, params);
        }
    }

    @NonNull
    private SceneryMode currentConcreteMode() {
        return selectedMode == SceneryMode.AUTO ? SceneryRuntimeState.get() : selectedMode;
    }

    private void applyChipState(@NonNull TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setActivated(selected);
        chip.setBackgroundResource(
                selected ? R.drawable.bg_weather_chip_selected : R.drawable.bg_weather_chip
        );
        chip.setTextColor(ContextCompat.getColor(
                getContext(),
                selected ? R.color.weather_aqua : R.color.weather_text_primary
        ));
    }

    private int labelRes(@NonNull SceneryMode mode) {
        switch (mode) {
            case AUTO:
                return R.string.wallpaper_scenery_auto;
            case OPEN_SKY:
                return R.string.wallpaper_scenery_open_sky;
            case VILLAGE:
                return R.string.wallpaper_scenery_village;
            case FARM_CROPS:
                return R.string.wallpaper_scenery_farm;
            case RIVER_LAKE:
                return R.string.wallpaper_scenery_river;
            case FLOWERS_GREENERY:
                return R.string.wallpaper_scenery_flowers;
            case URBAN_BUILDINGS:
                return R.string.wallpaper_scenery_urban;
            case NATURAL_HILLS:
            default:
                return R.string.wallpaper_scenery_natural_hills;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
