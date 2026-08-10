package com.atvriders.wsprtxrx.ui.map

import android.os.Parcelable
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Serializable

/**
 * Regression guard for the `rememberSaveable` force-close (audit B2).
 *
 * `SelectedStation` is held in `rememberSaveable`, so Compose's `SaveableStateRegistry`
 * runs it through `canBeSavedToBundle` on every `onSaveInstanceState` (rotate, Home,
 * Recents, split-screen, fold). Anything outside `{Parcelable, Serializable, String,
 * SparseArray, Binder, Size, SizeF}` throws `IllegalStateException` and kills the
 * process. It is the *container class* that is checked — primitive fields do not help.
 *
 * There is no `androidTest` source set and CI runs only `testReleaseUnitTest`, so this
 * has to be a plain JVM assertion on the type rather than an instrumentation test.
 */
class SelectedStationSaveableTest {

    @Test
    fun selectedStationCanBeStoredInABundle() {
        val cls = SelectedStation::class.java
        val saveable = Parcelable::class.java.isAssignableFrom(cls) ||
            Serializable::class.java.isAssignableFrom(cls)
        assertTrue(
            "SelectedStation is held in rememberSaveable and must implement Parcelable " +
                "or Serializable, otherwise saving instance state force-closes the app.",
            saveable,
        )
    }

    @Test
    fun parcelizeGeneratedACreator() {
        // @Parcelize emits a static CREATOR field; its absence would mean the plugin was
        // dropped from the build even though the interface is still declared.
        val creator = SelectedStation::class.java.getDeclaredField("CREATOR")
        assertTrue(
            "SelectedStation.CREATOR must be a Parcelable.Creator",
            android.os.Parcelable.Creator::class.java.isAssignableFrom(creator.type),
        )
    }
}
