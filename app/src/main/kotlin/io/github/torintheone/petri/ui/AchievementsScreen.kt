package io.github.torintheone.petri.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.sim.Achievement
import io.github.torintheone.petri.sim.AchievementGroup
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType
import kotlinx.coroutines.delay

/** The achievements on three shelves: each with its medal, its name, what earns it and how far it is. */
@Composable
fun AchievementList(progress: Map<Achievement, Int>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (group in AchievementGroup.entries) {
            val members = Achievement.entries.filter { it.group == group }
            val earned = members.count { (progress[it] ?: 0) >= it.target }
            SectionPanel(
                title = stringResource(group.title),
                icon = group.icon,
                trailing = { Text("$earned / ${members.size}", style = LabType.Mono, color = Lab.TextDim) },
            ) {
                members.forEachIndexed { index, achievement ->
                    if (index > 0) Hairline()
                    AchievementRow(achievement, progress[achievement] ?: 0)
                }
            }
        }
    }
}

/** One achievement: the medal, lit once earned; while it counts up, a bar with the count. */
@Composable
private fun AchievementRow(achievement: Achievement, value: Int) {
    val earned = value >= achievement.target
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
    ) {
        Medal(earned, MEDAL)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(achievement.title),
                style = MaterialTheme.typography.titleSmall,
                color = if (earned) Lab.Text else Lab.TextDim,
            )
            Text(
                text = stringResource(achievement.description),
                style = MaterialTheme.typography.bodySmall,
                color = Lab.TextDim,
            )
            if (!earned && achievement.target > 1 && value > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    ProgressBar(value.toFloat() / achievement.target, done = false, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text("$value / ${achievement.target}", style = LabType.Mono, color = Lab.TextDim, maxLines = 1)
                }
            }
        }
        if (earned) {
            Spacer(Modifier.width(10.dp))
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Lab.Lime, modifier = Modifier.size(18.dp))
        }
    }
}

/** The round badge of an achievement: amber once earned, grey before. */
@Composable
private fun Medal(earned: Boolean, size: Dp) {
    val color = if (earned) Lab.Amber else Lab.TextFaint
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(if (earned) Lab.Amber.copy(alpha = MEDAL_FILL) else Lab.Raised, CircleShape)
            .border(1.dp, color.copy(alpha = MEDAL_EDGE), CircleShape),
    ) {
        Icon(painterResource(R.drawable.ic_medal), contentDescription = null, tint = color, modifier = Modifier.size(size * MEDAL_ICON_SHARE))
    }
}

/**
 * The achievement just earned, at the top of the screen for a few seconds with its chime; a tap opens the list.
 * It waits while the description or the result of a level is open.
 */
@Composable
fun AchievementBanner(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    val waiting = viewModel.levelResult != null || viewModel.briefing != null
    val showing = viewModel.achievementNews.firstOrNull()?.takeIf { !waiting }
    LaunchedEffect(showing) {
        if (showing == null) return@LaunchedEffect
        viewModel.onAchievementShown()
        delay(BANNER_MILLIS)
        viewModel.dismissAchievement()
    }
    AnimatedContent(
        targetState = showing,
        transitionSpec = {
            (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) using
                SizeTransform(clip = false)
        },
        contentAlignment = Alignment.TopCenter,
        label = "achievement",
        modifier = modifier,
    ) { achievement ->
        if (achievement != null) {
            Panel(
                onClick = viewModel::openAchievements,
                contentPadding = PaddingValues(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                modifier = Modifier
                    .widthIn(max = BANNER_WIDTH)
                    .border(1.dp, Lab.Amber.copy(alpha = MEDAL_EDGE), MaterialTheme.shapes.medium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Medal(earned = true, size = BANNER_MEDAL)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Eyebrow(stringResource(R.string.achievement_earned), color = Lab.Amber)
                        Text(
                            text = stringResource(achievement.title),
                            style = MaterialTheme.typography.titleSmall,
                            color = Lab.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(achievement.description),
                            style = MaterialTheme.typography.bodySmall,
                            color = Lab.TextDim,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@get:StringRes
private val AchievementGroup.title: Int
    get() = when (this) {
        AchievementGroup.CAMPAIGN -> R.string.achievements_campaign
        AchievementGroup.DISH -> R.string.achievements_dish
        AchievementGroup.DISCOVERY -> R.string.achievements_discovery
    }

@get:DrawableRes
private val AchievementGroup.icon: Int
    get() = when (this) {
        AchievementGroup.CAMPAIGN -> R.drawable.ic_tab_campaign
        AchievementGroup.DISH -> R.drawable.ic_tab_microscope
        AchievementGroup.DISCOVERY -> R.drawable.ic_tool_diagnostics
    }

@get:StringRes
val Achievement.title: Int
    get() = when (this) {
        Achievement.FIRST_LEVEL -> R.string.ach_first_level
        Achievement.THREE_STARS -> R.string.ach_three_stars
        Achievement.ONE_PLANTING -> R.string.ach_one_planting
        Achievement.CHAPTER -> R.string.ach_chapter
        Achievement.CAMPAIGN -> R.string.ach_campaign
        Achievement.ALL_STARS -> R.string.ach_all_stars
        Achievement.CELLS_100 -> R.string.ach_cells_100
        Achievement.CELLS_1000 -> R.string.ach_cells_1000
        Achievement.ORGANISM -> R.string.ach_organism
        Achievement.CELL_TYPES -> R.string.ach_cell_types
        Achievement.SPECIES -> R.string.ach_species
        Achievement.OLD_DISH -> R.string.ach_old_dish
        Achievement.GENOME_SAVED -> R.string.ach_genome_saved
        Achievement.TWEEZERS -> R.string.ach_tweezers
        Achievement.DIAGNOSTICS -> R.string.ach_diagnostics
        Achievement.ALL_TYPES -> R.string.ach_all_types
        Achievement.VIRUS -> R.string.ach_virus
        Achievement.FUSION -> R.string.ach_fusion
        Achievement.MUTANT -> R.string.ach_mutant
    }

@get:StringRes
val Achievement.description: Int
    get() = when (this) {
        Achievement.FIRST_LEVEL -> R.string.ach_first_level_text
        Achievement.THREE_STARS -> R.string.ach_three_stars_text
        Achievement.ONE_PLANTING -> R.string.ach_one_planting_text
        Achievement.CHAPTER -> R.string.ach_chapter_text
        Achievement.CAMPAIGN -> R.string.ach_campaign_text
        Achievement.ALL_STARS -> R.string.ach_all_stars_text
        Achievement.CELLS_100 -> R.string.ach_cells_100_text
        Achievement.CELLS_1000 -> R.string.ach_cells_1000_text
        Achievement.ORGANISM -> R.string.ach_organism_text
        Achievement.CELL_TYPES -> R.string.ach_cell_types_text
        Achievement.SPECIES -> R.string.ach_species_text
        Achievement.OLD_DISH -> R.string.ach_old_dish_text
        Achievement.GENOME_SAVED -> R.string.ach_genome_saved_text
        Achievement.TWEEZERS -> R.string.ach_tweezers_text
        Achievement.DIAGNOSTICS -> R.string.ach_diagnostics_text
        Achievement.ALL_TYPES -> R.string.ach_all_types_text
        Achievement.VIRUS -> R.string.ach_virus_text
        Achievement.FUSION -> R.string.ach_fusion_text
        Achievement.MUTANT -> R.string.ach_mutant_text
    }

/** How long an earned achievement stays at the top of the screen. */
private const val BANNER_MILLIS = 3_400L
private const val MEDAL_FILL = 0.14f
private const val MEDAL_EDGE = 0.55f
private const val MEDAL_ICON_SHARE = 0.6f
private val MEDAL = 40.dp
private val BANNER_MEDAL = 44.dp
private val BANNER_WIDTH = 420.dp
