package io.github.torintheone.petri.data

import android.content.Context
import io.github.torintheone.petri.sim.Achievement
import io.github.torintheone.petri.sim.AchievementBook
import io.github.torintheone.petri.sim.CellType

/** The progress of the achievements, kept in the app's preferences: one number per achievement. */
class AchievementStore(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): AchievementBook {
        val progress = Achievement.entries.associateWith { preferences.getInt(it.name, 0) }
        val types = preferences.getStringSet(KEY_PLANTED_TYPES, null).orEmpty()
            .mapNotNullTo(HashSet()) { name -> CellType.entries.firstOrNull { it.name == name } }
        return AchievementBook(progress, types)
    }

    fun save(book: AchievementBook) {
        val editor = preferences.edit()
        for ((achievement, value) in book.progressMap()) editor.putInt(achievement.name, value)
        editor.putStringSet(KEY_PLANTED_TYPES, book.plantedTypes.mapTo(HashSet()) { it.name })
        editor.apply()
    }

    private companion object {
        const val NAME = "achievements"
        const val KEY_PLANTED_TYPES = "planted_types"
    }
}
