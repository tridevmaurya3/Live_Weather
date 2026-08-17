package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;

/**
 * Allocation-free process-local bridge between persisted scenery preference and the
 * shared OpenGL world renderer.
 *
 * S9 keeps two identities:
 * - requested: what the user selected (AUTO or a manual scene),
 * - resolved: the concrete scene currently rendered.
 *
 * This distinction lets Auto Scene resolve to a concrete day-part scene without making
 * legacy visual-option constructors accidentally overwrite AUTO with that resolved scene.
 * SharedPreferences is never read from the frame hot path.
 */
public final class SceneryRuntimeState {

    @NonNull
    private static volatile SceneryMode requested = SceneryMode.NATURAL_HILLS;

    @NonNull
    private static volatile SceneryMode resolved = SceneryMode.NATURAL_HILLS;

    private SceneryRuntimeState() {
    }

    /** Compatibility setter for a concrete/manual scene. */
    public static void set(@NonNull SceneryMode mode) {
        requested = mode;
        resolved = mode;
    }

    /** Updates requested selection and its concrete render resolution atomically enough for UI/GL use. */
    public static void setSelection(
            @NonNull SceneryMode requestedMode,
            @NonNull SceneryMode resolvedMode
    ) {
        requested = requestedMode;
        resolved = resolvedMode;
    }

    /** Concrete scene consumed by the OpenGL world renderer. */
    @NonNull
    public static SceneryMode get() {
        return resolved;
    }

    /** User-facing persisted selection semantics used by compatibility option constructors. */
    @NonNull
    public static SceneryMode getRequested() {
        return requested;
    }
}
