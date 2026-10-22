package io.github.torintheone.petri.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.sim.Achievement
import io.github.torintheone.petri.sim.Campaign
import io.github.torintheone.petri.sim.CellType
import io.github.torintheone.petri.sim.Goal
import io.github.torintheone.petri.sim.GoalProgress
import io.github.torintheone.petri.sim.Level
import io.github.torintheone.petri.sim.LevelOutcome
import io.github.torintheone.petri.sim.LevelStatus
import io.github.torintheone.petri.sim.LossReason
import io.github.torintheone.petri.sim.SubstrateSettings
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType
import kotlin.math.roundToInt

/**
 * «Кампания»: the stars earned so far, the level being played, and the chapters with their levels. A level opens
 * once the one before it is won; a tap on an open level shows its description. A switch at the top turns the tab
 * to the achievements.
 */
@Composable
fun CampaignScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    val earned = viewModel.stars.values.sum()
    var leaving by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 2.dp, top = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Eyebrow(stringResource(R.string.tab_campaign))
                Text(
                    text = stringResource(if (viewModel.showAchievements) R.string.achievements_title else R.string.campaign_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Lab.Text,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = Lab.Amber, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.campaign_stars, earned, Campaign.levels.size * MAX_STARS),
                    style = LabType.Reading,
                    color = Lab.Text,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        val achievements = viewModel.achievementProgress
        val achieved = Achievement.entries.count { (achievements[it] ?: 0) >= it.target }
        Segmented(
            options = listOf(false, true),
            selected = viewModel.showAchievements,
            label = { showAchievements ->
                if (showAchievements) {
                    stringResource(R.string.campaign_achievements, achieved, Achievement.entries.size)
                } else {
                    stringResource(R.string.campaign_levels)
                }
            },
            onSelect = {
                if (it != viewModel.showAchievements) viewModel.click()
                viewModel.showAchievements = it
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (viewModel.showAchievements) {
            Text(
                text = stringResource(R.string.achievements_intro),
                style = MaterialTheme.typography.bodySmall,
                color = Lab.TextDim,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            AchievementList(achievements)
        } else {
            Text(
                text = stringResource(R.string.campaign_intro),
                style = MaterialTheme.typography.bodySmall,
                color = Lab.TextDim,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            viewModel.activeLevel?.let { level ->
                CurrentLevel(
                    level = level,
                    status = viewModel.levelStatus,
                    onMicroscope = { viewModel.tab = Tab.MICROSCOPE },
                    onRestart = viewModel::restartLevel,
                    onLeave = { leaving = true },
                )
            }
            Campaign.chapters.forEachIndexed { chapter, levels ->
                val title = stringResource(R.string.campaign_chapter, chapter + 1, stringArrayResource(R.array.chapter_titles)[chapter])
                SectionPanel(title) {
                    for (level in levels) {
                        LevelRow(
                            level = level,
                            stars = viewModel.stars[level.id] ?: 0,
                            unlocked = viewModel.isUnlocked(level),
                            playing = viewModel.activeLevel == level,
                            onClick = { viewModel.briefing = level },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (leaving) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_leave_title),
            text = stringResource(R.string.dialog_leave_text),
            confirm = stringResource(R.string.level_leave),
            onConfirm = viewModel::leaveLevel,
            onDismiss = { leaving = false },
        )
    }
}

/** The description and the result of campaign levels, over whatever tab is open. */
@Composable
fun LevelDialogs(viewModel: PetriViewModel) {
    viewModel.briefing?.let { level ->
        LevelBriefing(
            level = level,
            playing = viewModel.activeLevel == level,
            onStart = { viewModel.startLevel(level) },
            onContinue = {
                viewModel.briefing = null
                viewModel.tab = Tab.MICROSCOPE
            },
            onDismiss = { viewModel.briefing = null },
        )
    }
    viewModel.levelResult?.let { result ->
        LevelResult(
            status = result,
            hasNext = viewModel.nextLevel != null,
            onNext = { viewModel.nextLevel?.let(viewModel::startLevel) },
            onRestart = viewModel::restartLevel,
            onCampaign = {
                viewModel.dismissResult()
                viewModel.tab = Tab.CAMPAIGN
            },
            onStay = viewModel::dismissResult,
        )
    }
}

/** The level being played: how far it is, and the way back to the microscope, a fresh start or out of it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentLevel(level: Level, status: LevelStatus?, onMicroscope: () -> Unit, onRestart: () -> Unit, onLeave: () -> Unit) {
    Panel(
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Lab.Accent.copy(alpha = ACCENT_EDGE), MaterialTheme.shapes.medium),
    ) {
        Eyebrow(stringResource(R.string.campaign_current), color = Lab.Accent)
        Text(
            text = stringResource(R.string.level_number, level.id) + " · " + levelTitle(level),
            style = MaterialTheme.typography.titleMedium,
            color = Lab.Text,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        if (status != null) GoalLines(level, status)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            LabButton(stringResource(R.string.level_to_microscope), onClick = onMicroscope, icon = R.drawable.ic_tab_microscope, kind = ButtonKind.PRIMARY)
            LabButton(stringResource(R.string.level_restart), onClick = onRestart, icon = R.drawable.ic_reset)
            LabButton(stringResource(R.string.level_leave), onClick = onLeave, icon = R.drawable.ic_exit, kind = ButtonKind.DANGER)
        }
    }
}

/** One level in its chapter: number, name, goal and stars, or a lock while it is closed. */
@Composable
private fun LevelRow(level: Level, stars: Int, unlocked: Boolean, playing: Boolean, onClick: () -> Unit) {
    val index = Campaign.levels.indexOf(level)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        val badgeColor = when {
            playing -> Lab.Accent
            stars > 0 -> Lab.Lime
            unlocked -> Lab.Text
            else -> Lab.TextFaint
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(BADGE)
                .clip(RoundedCornerShape(10.dp))
                .background(if (playing) Lab.AccentSoft else Lab.Raised)
                .border(1.dp, badgeColor.copy(alpha = if (unlocked) ACCENT_EDGE else 0.3f), RoundedCornerShape(10.dp)),
        ) {
            if (stars > 0 && !playing) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Lab.Lime, modifier = Modifier.size(20.dp))
            } else {
                Text(level.id, style = LabType.ReadingSmall, color = badgeColor)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = levelTitle(level),
                style = MaterialTheme.typography.titleSmall,
                color = if (unlocked) Lab.Text else Lab.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (unlocked) goalText(level.goals.first()) else stringResource(R.string.campaign_locked, Campaign.levels[index - 1].id),
                style = MaterialTheme.typography.bodySmall,
                color = Lab.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (unlocked) {
            Stars(stars, STAR_SMALL)
        } else {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Lab.TextFaint, modifier = Modifier.size(18.dp))
        }
    }
}

/** Three stars, the first [count] of them lit. */
@Composable
fun Stars(count: Int, size: Dp, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.stars_label, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.semantics { contentDescription = label },
    ) {
        for (k in 0 until MAX_STARS) {
            Icon(
                painter = painterResource(R.drawable.ic_star),
                contentDescription = null,
                tint = if (k < count) Lab.Amber else Lab.HairlineStrong,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * The goals of a level on the microscope: one line with a bar for each goal, then the time and the plantings left.
 * A tap opens the description, or the result once the level is decided.
 */
@Composable
fun LevelGoals(level: Level, status: LevelStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.level_goals_open)
    Panel(
        glass = true,
        onClick = onClick,
        contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
        modifier = modifier
            .width(IntrinsicSize.Max)
            .semantics { contentDescription = label },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_tab_campaign), contentDescription = null, tint = Lab.Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Eyebrow(
                text = level.id + " · " + levelTitle(level),
                color = Lab.Text,
                modifier = Modifier.weight(1f, fill = false),
            )
            when (status?.outcome) {
                LevelOutcome.WON -> {
                    Spacer(Modifier.width(8.dp))
                    Stars(status.stars, STAR_TINY)
                }
                LevelOutcome.LOST -> {
                    Spacer(Modifier.width(8.dp))
                    Eyebrow(stringResource(R.string.level_status_lost), color = Lab.Danger)
                }
                else -> Unit
            }
        }
        if (status != null) GoalLines(level, status, Modifier.padding(top = 6.dp))
    }
}

/** Every goal with its value and a bar, then the time against the limit and the plantings left. */
@Composable
private fun GoalLines(level: Level, status: LevelStatus, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (progress in status.goals) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goalName(progress.goal), style = MaterialTheme.typography.bodySmall, color = Lab.TextDim, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = goalValue(progress, status),
                        style = LabType.Mono,
                        color = if (progress.done) Lab.Lime else Lab.Text,
                        maxLines = 1,
                    )
                }
                ProgressBar(goalFraction(progress, status, level), progress.done, Modifier.padding(top = 3.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = null, tint = Lab.TextDim, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            val hours = formatNumber(status.hours, 1)
            Text(
                text = level.hours?.let { stringResource(R.string.level_time, hours, it.roundToInt()) } ?: stringResource(R.string.hours_value, hours),
                style = LabType.Mono,
                color = Lab.Text,
                maxLines = 1,
            )
            status.plantingsLeft?.let { left ->
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.level_plantings, left), style = MaterialTheme.typography.bodySmall, color = Lab.TextDim, maxLines = 1)
            }
        }
    }
}

/** A thin bar filled to [fraction]; green once [done]. */
@Composable
internal fun ProgressBar(fraction: Float, done: Boolean, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(3.dp),
    ) {
        drawRect(Lab.Hairline, size = size)
        drawRect(if (done) Lab.Lime else Lab.Accent, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

/**
 * Everything about a level before it starts: the story, the goals, the conditions and a hint that opens on request.
 * For the level being played it offers to go on or to start again.
 */
@Composable
fun LevelBriefing(level: Level, playing: Boolean, onStart: () -> Unit, onContinue: () -> Unit, onDismiss: () -> Unit) {
    val index = Campaign.levels.indexOf(level)
    var hintShown by rememberSaveable(level.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (playing) {
                TextButton(onClick = onContinue) { Text(stringResource(R.string.level_continue)) }
            } else {
                TextButton(onClick = onStart) { Text(stringResource(R.string.level_start)) }
            }
        },
        dismissButton = {
            Row {
                if (playing) TextButton(onClick = onStart) { Text(stringResource(R.string.level_restart)) }
                TextButton(onClick = onDismiss) { Text(stringResource(if (playing) R.string.level_close else R.string.dialog_cancel)) }
            }
        },
        title = {
            Column {
                Eyebrow(stringResource(R.string.level_number, level.id), color = Lab.Accent)
                Text(levelTitle(level))
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringArrayResource(R.array.level_stories)[index], color = Lab.Text)
                BriefingSection(stringResource(R.string.level_goal), level.goals.map { goalText(it) }, Lab.Amber)
                BriefingSection(stringResource(R.string.level_conditions), conditionLines(level), Lab.Accent)
                if (hintShown) {
                    BriefingSection(stringResource(R.string.level_hint), listOf(stringArrayResource(R.array.level_hints)[index]), Lab.Lime)
                } else {
                    TextButton(onClick = { hintShown = true }, modifier = Modifier.padding(top = 6.dp)) {
                        Text(stringResource(R.string.level_show_hint))
                    }
                }
            }
        },
        containerColor = Lab.Raised,
        textContentColor = Lab.TextDim,
    )
}

@Composable
private fun BriefingSection(title: String, lines: List<String>, mark: Color) {
    Eyebrow(title, Modifier.padding(top = 14.dp, bottom = 4.dp))
    for (line in lines) {
        Row(Modifier.padding(vertical = 2.dp)) {
            Box(
                Modifier
                    .padding(top = 7.dp, end = 10.dp)
                    .size(5.dp)
                    .background(mark, RoundedCornerShape(50)),
            )
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Lab.Text)
        }
    }
}

/** How a finished level went: stars and time for a win, the reason for a loss, and where to go next. */
@Composable
fun LevelResult(
    status: LevelStatus,
    hasNext: Boolean,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onCampaign: () -> Unit,
    onStay: () -> Unit,
) {
    val won = status.outcome == LevelOutcome.WON
    AlertDialog(
        onDismissRequest = onStay,
        confirmButton = {
            when {
                !won -> TextButton(onClick = onRestart) { Text(stringResource(R.string.level_restart)) }
                hasNext -> TextButton(onClick = onNext) { Text(stringResource(R.string.level_next)) }
                else -> TextButton(onClick = onCampaign) { Text(stringResource(R.string.level_to_campaign)) }
            }
        },
        dismissButton = {
            Row {
                if (!won || hasNext) TextButton(onClick = onCampaign) { Text(stringResource(R.string.level_to_campaign)) }
                TextButton(onClick = onStay) { Text(stringResource(R.string.level_stay)) }
            }
        },
        title = { Text(stringResource(if (won) R.string.level_won_title else R.string.level_lost_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (won) {
                    Stars(status.stars, STAR_BIG)
                    Text(stringResource(R.string.level_won_text, formatNumber(status.hours, 1)), color = Lab.Text)
                    if (!hasNext) Text(stringResource(R.string.level_won_last))
                } else {
                    val reason = if (status.loss == LossReason.NO_CELLS) R.string.level_lost_cells else R.string.level_lost_time
                    Text(stringResource(reason), color = Lab.Text)
                    Text(stringResource(R.string.level_lost_advice))
                }
            }
        },
        containerColor = Lab.Raised,
        textContentColor = Lab.TextDim,
    )
}

/** The level's name from the app's texts. */
@Composable
fun levelTitle(level: Level): String = stringArrayResource(R.array.level_titles)[Campaign.levels.indexOf(level)]

/** A goal as a sentence, for the description. */
@Composable
private fun goalText(goal: Goal): String = when (goal) {
    is Goal.Population -> stringResource(R.string.goal_population, goal.cells)
    is Goal.TypeCount -> stringResource(R.string.goal_type, goal.cells, stringArrayResource(R.array.cell_types)[goal.type.ordinal])
    is Goal.Organism -> stringResource(R.string.goal_organism, goal.cells)
    is Goal.Biomass -> stringResource(R.string.goal_biomass, decimal(goal.mass, 0))
    is Goal.Reach -> stringResource(R.string.goal_reach)
    is Goal.Majority -> stringResource(R.string.goal_majority, (goal.share * PERCENT).roundToInt(), goal.cells)
    is Goal.FewRivals -> stringResource(R.string.goal_rivals, goal.cells)
    is Goal.Species -> stringResource(R.string.goal_species, goal.count)
    is Goal.Survive -> stringResource(R.string.goal_survive, goal.hours.roundToInt(), goal.cells)
}

/** A goal's short name, for the readings on the microscope. */
@Composable
private fun goalName(goal: Goal): String = when (goal) {
    is Goal.Population -> stringResource(R.string.goal_population_short)
    is Goal.TypeCount -> stringArrayResource(R.array.cell_types)[goal.type.ordinal]
    is Goal.Organism -> stringResource(R.string.goal_organism_short)
    is Goal.Biomass -> stringResource(R.string.goal_biomass_short)
    is Goal.Reach -> stringResource(R.string.goal_reach_short)
    is Goal.Majority -> stringResource(R.string.goal_majority_short)
    is Goal.FewRivals -> stringResource(R.string.goal_rivals_short)
    is Goal.Species -> stringResource(R.string.goal_species_short)
    is Goal.Survive -> stringResource(R.string.goal_survive_short)
}

/** Where a goal stands, as the readings show it: «23 / 60», «340 мкм», «45 % / 60 %». */
@Composable
private fun goalValue(progress: GoalProgress, status: LevelStatus): String {
    val value = progress.value
    val target = progress.target
    return when (val goal = progress.goal) {
        is Goal.Reach -> when {
            progress.done -> stringResource(R.string.goal_reach_done)
            value.isInfinite() -> stringResource(R.string.goal_reach_none)
            else -> stringResource(R.string.um_value, value.roundToInt())
        }
        is Goal.Biomass -> "${decimal(value, 1)} / ${decimal(target, 0)}"
        is Goal.Majority -> "${(value * PERCENT).roundToInt()} % / ${(target * PERCENT).roundToInt()} %"
        is Goal.FewRivals -> "${value.roundToInt()} → ${target.roundToInt()}"
        is Goal.Survive -> "${status.playerCells} / ${goal.cells}"
        else -> "${value.roundToInt()} / ${target.roundToInt()}"
    }
}

/** How full a goal's bar is, 0–1. */
private fun goalFraction(progress: GoalProgress, status: LevelStatus, level: Level): Float {
    if (progress.done) return 1f
    val value = progress.value
    val target = progress.target
    return when (val goal = progress.goal) {
        is Goal.Reach -> if (value.isInfinite()) 0f else 1f - value / (2f * level.settings.dishRadiusUm)
        is Goal.FewRivals -> if (value <= 0f) 1f else target / value
        is Goal.Survive -> minOf(value / target, status.playerCells.toFloat() / goal.cells)
        else -> if (target > 0f) value / target else 0f
    }.coerceIn(0f, 1f)
}

/** What makes a level's dish and rules special, line by line. */
@Composable
private fun conditionLines(level: Level): List<String> {
    val s = level.settings
    val usual = SubstrateSettings()
    val typeNames = stringArrayResource(R.array.cell_types)
    val lines = mutableListOf<String>()
    level.hours?.let { lines += stringResource(R.string.cond_time, it.roundToInt()) }
    level.plantings?.let { lines += stringResource(R.string.cond_plantings, it) }
    if (level.plantZone != null) lines += stringResource(R.string.cond_zone)
    if (level.allowedTypes.size < CellType.entries.size) {
        lines += stringResource(R.string.cond_types, level.allowedTypes.joinToString { typeNames[it.ordinal].lowercase() })
    }
    if (s.dishDiameterMm != usual.dishDiameterMm) lines += stringResource(R.string.cond_dish, decimal(s.dishDiameterMm, 1))
    if (s.maxCells < usual.maxCells) lines += stringResource(R.string.cond_max_cells, s.maxCells)
    if (level.food >= FOOD_STOCK) lines += stringResource(R.string.cond_food_stock)
    when {
        s.foodRate <= 0f -> lines += stringResource(R.string.cond_no_food)
        s.foodRate < usual.foodRate -> lines += stringResource(R.string.cond_little_food)
        s.foodRate > usual.foodRate -> lines += stringResource(R.string.cond_much_food)
    }
    if (s.foodCoat > 0f) lines += stringResource(R.string.cond_coat)
    when {
        s.lightBrightness <= 0f -> lines += stringResource(R.string.cond_no_light)
        s.lightWidth >= 1f -> lines += stringResource(R.string.cond_even_light)
        s.lightBrightness < DIM_LIGHT -> lines += stringResource(R.string.cond_dim_light)
        s.lightWidth < usual.lightWidth -> lines += stringResource(R.string.cond_narrow_light)
        s.lightBrightness > usual.lightBrightness -> lines += stringResource(R.string.cond_bright_light)
    }
    if (s.gravity > 0f) lines += stringResource(R.string.cond_gravity)
    if (s.gravity > 0f && s.viscosity < usual.viscosity) lines += stringResource(R.string.cond_thin_medium)
    if (s.radiation > 0f) lines += stringResource(R.string.cond_radiation, (s.radiation * PERCENT).roundToInt())
    if (s.deadlyWalls) lines += stringResource(R.string.cond_deadly_walls)
    if (level.placements.isNotEmpty()) lines += stringResource(R.string.cond_rivals)
    if (!level.feeding && !level.tweezers && !level.removal) lines += stringResource(R.string.cond_tools)
    return lines
}

private const val MAX_STARS = 3
private const val PERCENT = 100f
private const val ACCENT_EDGE = 0.6f

/** Below this brightness the description calls the light dim. */
private const val DIM_LIGHT = 0.4f

/** From this much food at the start (a share of the limit) the description calls it a stock. */
private const val FOOD_STOCK = 0.8f

private val BADGE = 40.dp
private val STAR_TINY = 11.dp
private val STAR_SMALL = 14.dp
private val STAR_BIG = 30.dp
