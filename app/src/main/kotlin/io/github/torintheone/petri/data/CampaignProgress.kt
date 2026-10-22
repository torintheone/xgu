package io.github.torintheone.petri.data

import android.content.Context

/**
 * The campaign on this phone: the stars earned in each level, and the level being played, so that it survives
 * the app being closed. Small and quick, so it lives in SharedPreferences and may be used on the main thread.
 */
class CampaignProgress(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Stars earned in level [id]: 0 until it is won, then 1–3. */
    fun stars(id: String): Int = preferences.getInt(KEY_STARS + id, 0)

    /** Keeps the better result of level [id]. */
    fun recordWin(id: String, stars: Int) {
        if (stars > stars(id)) preferences.edit().putInt(KEY_STARS + id, stars).apply()
    }

    /** The level being played, or null in the free dish. */
    var activeLevel: String?
        get() = preferences.getString(KEY_ACTIVE, null)
        set(value) {
            val editor = preferences.edit()
            if (value == null) editor.remove(KEY_ACTIVE) else editor.putString(KEY_ACTIVE, value)
            editor.apply()
        }

    /** Plantings already used in [activeLevel]. */
    var plantingsUsed: Int
        get() = preferences.getInt(KEY_PLANTINGS, 0)
        set(value) = preferences.edit().putInt(KEY_PLANTINGS, value).apply()

    private companion object {
        const val NAME = "campaign"
        const val KEY_STARS = "stars_"
        const val KEY_ACTIVE = "active_level"
        const val KEY_PLANTINGS = "plantings_used"
    }
}
