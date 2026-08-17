package com.tridev.liveweather.ui.scene;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.tridev.liveweather.R;
import com.tridev.liveweather.domain.scene.SceneryMode;
import com.tridev.liveweather.domain.scene.SceneryVariantRuntimeState;

/**
 * Full-screen, UI-only scenery preview used by the Wallpaper scene library.
 *
 * The dialog stages scene/variation choices locally and applies nothing until the user
 * presses Use scene. It does not read or mutate weather truth, astronomy, precipitation,
 * clouds, alerts, cache or network state.
 */
public final class SceneryPreviewDialog {

    public interface OnUseListener {
        void onUse(@NonNull SceneryMode mode, int variant);
    }

    private SceneryPreviewDialog() {
    }

    public static void show(
            @NonNull Context context,
            @NonNull SceneryMode mode,
            int initialVariant,
            @NonNull SceneryMode autoResolvedMode,
            @NonNull String sceneLabel,
            @NonNull OnUseListener onUseListener
    ) {
        final int[] stagedVariant = {
                Math.max(0, Math.min(SceneryVariantRuntimeState.VARIANT_COUNT - 1, initialVariant))
        };
        final SceneryMode resolved = autoResolvedMode == SceneryMode.AUTO
                ? SceneryMode.NATURAL_HILLS
                : autoResolvedMode;
        final TextView[] useActionHolder = new TextView[1];

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);

        NestedScrollView scroller = new NestedScrollView(context);
        scroller.setFillViewport(true);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipToPadding(false);
        scroller.setBackgroundColor(ContextCompat.getColor(context, R.color.weather_background_deep));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 24));
        scroller.addView(root, new NestedScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout headingBlock = new LinearLayout(context);
        headingBlock.setOrientation(LinearLayout.VERTICAL);

        TextView eyebrow = new TextView(context);
        eyebrow.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        eyebrow.setText(R.string.wallpaper_scenery_preview_title);
        eyebrow.setTextColor(ContextCompat.getColor(context, R.color.weather_aqua));
        eyebrow.setLetterSpacing(0.08f);
        headingBlock.addView(eyebrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(context);
        title.setTextAppearance(R.style.TextAppearance_LiveWeather_Title);
        title.setText(sceneLabel);
        title.setTextColor(ContextCompat.getColor(context, R.color.weather_text_primary));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(context, 3);
        headingBlock.addView(title, titleParams);

        header.addView(headingBlock, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView topClose = createActionText(context, context.getString(R.string.wallpaper_scenery_preview_close));
        topClose.setContentDescription(context.getString(R.string.wallpaper_scenery_preview_close));
        topClose.setOnClickListener(view -> dialog.dismiss());
        header.addView(topClose, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 48)
        ));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        SceneryPreviewCardView preview = new SceneryPreviewCardView(context);
        preview.setScene(mode, sceneLabel);
        preview.setAutoResolvedMode(resolved);
        preview.setVariant(stagedVariant[0]);
        preview.setSelected(true);
        preview.setClickable(false);
        preview.setFocusable(false);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 320)
        );
        previewParams.topMargin = dp(context, 16);
        root.addView(preview, previewParams);

        TextView status = new TextView(context);
        status.setTextAppearance(R.style.TextAppearance_LiveWeather_BodyLarge);
        status.setTextColor(ContextCompat.getColor(context, R.color.weather_aqua));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(context, 14);
        root.addView(status, statusParams);

        TextView detail = new TextView(context);
        detail.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        detail.setTextColor(ContextCompat.getColor(context, R.color.weather_text_secondary));
        detail.setText(sceneDetailRes(mode));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(context, 5);
        root.addView(detail, detailParams);

        TextView variationTitle = new TextView(context);
        variationTitle.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        variationTitle.setTextColor(ContextCompat.getColor(context, R.color.weather_text_secondary));
        variationTitle.setText(R.string.wallpaper_scenery_preview_variation_title);
        LinearLayout.LayoutParams variationTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        variationTitleParams.topMargin = dp(context, 18);
        root.addView(variationTitle, variationTitleParams);

        LinearLayout variationRow = new LinearLayout(context);
        variationRow.setOrientation(LinearLayout.HORIZONTAL);
        variationRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams variationRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        variationRowParams.topMargin = dp(context, 6);
        root.addView(variationRow, variationRowParams);

        TextView[] variationChips = new TextView[SceneryVariantRuntimeState.VARIANT_COUNT];
        for (int variant = 0; variant < variationChips.length; variant++) {
            final int value = variant;
            TextView chip = createActionText(context, String.valueOf(variant + 1));
            chip.setMinWidth(dp(context, 48));
            chip.setPadding(0, 0, 0, 0);
            chip.setContentDescription(context.getString(
                    R.string.wallpaper_scenery_variation_select_accessibility,
                    variant + 1,
                    SceneryVariantRuntimeState.VARIANT_COUNT
            ));
            variationChips[variant] = chip;

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(context, 48),
                    dp(context, 48)
            );
            if (variant < variationChips.length - 1) params.setMarginEnd(dp(context, 8));
            variationRow.addView(chip, params);

            chip.setOnClickListener(view -> {
                stagedVariant[0] = value;
                preview.setVariant(value);
                updateVariationStates(context, variationChips, value);
                updateStatus(context, status, mode, resolved, value);
                if (useActionHolder[0] != null) {
                    useActionHolder[0].setContentDescription(context.getString(
                            R.string.wallpaper_scenery_preview_use_accessibility,
                            sceneLabel,
                            value + 1
                    ));
                }
            });
        }
        updateVariationStates(context, variationChips, stagedVariant[0]);
        updateStatus(context, status, mode, resolved, stagedVariant[0]);

        TextView truthNote = new TextView(context);
        truthNote.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        truthNote.setTextColor(ContextCompat.getColor(context, R.color.weather_text_tertiary));
        truthNote.setText(R.string.wallpaper_scenery_preview_truth_note);
        LinearLayout.LayoutParams truthParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        truthParams.topMargin = dp(context, 16);
        root.addView(truthNote, truthParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.topMargin = dp(context, 20);
        root.addView(actions, actionsParams);

        TextView cancel = createActionText(context, context.getString(R.string.wallpaper_scenery_preview_cancel));
        cancel.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0,
                dp(context, 52),
                1f
        );
        cancelParams.setMarginEnd(dp(context, 10));
        actions.addView(cancel, cancelParams);

        TextView useScene = new TextView(context);
        useActionHolder[0] = useScene;
        useScene.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        useScene.setText(mode == SceneryMode.AUTO
                ? R.string.wallpaper_scenery_preview_use_auto
                : R.string.wallpaper_scenery_preview_use);
        useScene.setTextColor(ContextCompat.getColor(context, R.color.white));
        useScene.setGravity(Gravity.CENTER);
        useScene.setSingleLine(true);
        useScene.setMinHeight(dp(context, 52));
        useScene.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        useScene.setBackgroundResource(R.drawable.bg_weather_button_primary);
        useScene.setClickable(true);
        useScene.setFocusable(true);
        useScene.setContentDescription(context.getString(
                R.string.wallpaper_scenery_preview_use_accessibility,
                sceneLabel,
                stagedVariant[0] + 1
        ));
        useScene.setOnClickListener(view -> {
            onUseListener.onUse(mode, stagedVariant[0]);
            dialog.dismiss();
        });
        actions.addView(useScene, new LinearLayout.LayoutParams(
                0,
                dp(context, 52),
                1f
        ));

        dialog.setContentView(scroller);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            int background = ContextCompat.getColor(context, R.color.weather_background_deep);
            window.setBackgroundDrawable(new ColorDrawable(background));
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
            window.setStatusBarColor(background);
            window.setNavigationBarColor(background);
        }
    }

    private static void updateStatus(
            @NonNull Context context,
            @NonNull TextView status,
            @NonNull SceneryMode mode,
            @NonNull SceneryMode resolved,
            int variant
    ) {
        if (mode == SceneryMode.AUTO) {
            status.setText(context.getString(
                    R.string.wallpaper_scenery_preview_auto_status_format,
                    label(context, resolved),
                    variant + 1
            ));
        } else {
            status.setText(context.getString(
                    R.string.wallpaper_scenery_preview_manual_status_format,
                    variant + 1
            ));
        }
    }

    private static void updateVariationStates(
            @NonNull Context context,
            @NonNull TextView[] chips,
            int selected
    ) {
        for (int index = 0; index < chips.length; index++) {
            boolean active = index == selected;
            TextView chip = chips[index];
            chip.setSelected(active);
            chip.setActivated(active);
            chip.setBackgroundResource(
                    active ? R.drawable.bg_weather_chip_selected : R.drawable.bg_weather_chip
            );
            chip.setTextColor(ContextCompat.getColor(
                    context,
                    active ? R.color.weather_aqua : R.color.weather_text_primary
            ));
        }
    }

    @NonNull
    private static TextView createActionText(@NonNull Context context, @NonNull String text) {
        TextView view = new TextView(context);
        view.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(context, R.color.weather_text_primary));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setMinHeight(dp(context, 48));
        view.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        view.setBackgroundResource(R.drawable.bg_weather_chip);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private static int sceneDetailRes(@NonNull SceneryMode mode) {
        switch (mode) {
            case AUTO:
                return R.string.wallpaper_scenery_preview_auto_detail;
            case OPEN_SKY:
                return R.string.wallpaper_scenery_preview_open_sky_detail;
            case VILLAGE:
                return R.string.wallpaper_scenery_preview_village_detail;
            case FARM_CROPS:
                return R.string.wallpaper_scenery_preview_farm_detail;
            case RIVER_LAKE:
                return R.string.wallpaper_scenery_preview_river_detail;
            case FLOWERS_GREENERY:
                return R.string.wallpaper_scenery_preview_flowers_detail;
            case URBAN_BUILDINGS:
                return R.string.wallpaper_scenery_preview_urban_detail;
            case NATURAL_HILLS:
            default:
                return R.string.wallpaper_scenery_preview_hills_detail;
        }
    }

    @NonNull
    private static String label(@NonNull Context context, @NonNull SceneryMode mode) {
        switch (mode) {
            case AUTO:
                return context.getString(R.string.wallpaper_scenery_auto);
            case OPEN_SKY:
                return context.getString(R.string.wallpaper_scenery_open_sky);
            case VILLAGE:
                return context.getString(R.string.wallpaper_scenery_village);
            case FARM_CROPS:
                return context.getString(R.string.wallpaper_scenery_farm);
            case RIVER_LAKE:
                return context.getString(R.string.wallpaper_scenery_river);
            case FLOWERS_GREENERY:
                return context.getString(R.string.wallpaper_scenery_flowers);
            case URBAN_BUILDINGS:
                return context.getString(R.string.wallpaper_scenery_urban);
            case NATURAL_HILLS:
            default:
                return context.getString(R.string.wallpaper_scenery_natural_hills);
        }
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
