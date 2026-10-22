package io.github.torintheone.petri.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.torintheone.petri.BuildConfig
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.data.DishEntry
import io.github.torintheone.petri.sim.CellType
import io.github.torintheone.petri.sim.SimConfig
import io.github.torintheone.petri.sim.SimStats
import io.github.torintheone.petri.sim.SubstrateSettings
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** «Среда» (SPEC «Интерфейс»): the dish and its files on top, then the parameters of the medium in groups. */
@Composable
fun EnvironmentScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Eyebrow(stringResource(R.string.tab_environment), Modifier.padding(start = 2.dp, top = 4.dp))
        val level = viewModel.activeLevel
        if (level != null) {
            // A level fixes its medium: no settings to change, no other dish to load.
            Panel(
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Lab.Accent.copy(alpha = CHIP_EDGE_ALPHA), MaterialTheme.shapes.medium),
            ) {
                Text(
                    text = stringResource(R.string.level_banner, level.id, levelTitle(level)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Lab.Text,
                )
            }
        }
        DishCard(viewModel)
        if (level == null) SettingsEditor(viewModel)
        SoundSettings(viewModel)
        About(Modifier.padding(top = 6.dp))
    }
}

/** Background music: on or off and how loud. */
@Composable
private fun SoundSettings(viewModel: PetriViewModel) {
    EnvSection(R.string.env_sound, R.drawable.ic_music) {
        SwitchRow(stringResource(R.string.env_music), viewModel.musicOn, viewModel::changeMusicOn)
        SliderRow(
            label = stringResource(R.string.env_music_volume),
            value = viewModel.musicVolume,
            range = 0f..1f,
            valueText = stringResource(R.string.percent_value, (viewModel.musicVolume * 100f).roundToInt()),
            onChange = viewModel::changeMusicVolume,
            levels = MUSIC_VOLUME_LEVELS,
            enabled = viewModel.musicOn,
        )
    }
}

/** The version and «О приложении» (SPEC «Этап 7»). */
@Composable
private fun About(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val version = stringResource(R.string.version_label, stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = version,
            style = LabType.Mono,
            color = Lab.TextFaint,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp),
        )
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.about_title), color = Lab.Accent) }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_ok)) } },
            title = { Text(version) },
            text = { Text(stringResource(R.string.about_text)) },
            containerColor = Lab.Raised,
            textContentColor = Lab.TextDim,
        )
    }
}

/** «Сохранить чашку», «Загрузить», «Стерилизовать» under the readings of the dish. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishCard(viewModel: PetriViewModel) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(initialValue = SimStats.EMPTY)
    val dishes by viewModel.dishes.collectAsStateWithLifecycle()
    var saving by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var sterilizing by rememberSaveable { mutableStateOf(false) }

    Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_tab_microscope), contentDescription = null, tint = Lab.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Eyebrow(stringResource(R.string.env_dish), color = Lab.Text)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
        ) {
            Readout(stringResource(R.string.stat_age), stringResource(R.string.hours_value, formatNumber(stats.ageHours, 1)))
            Readout(stringResource(R.string.stat_cells), stats.cellCount.toString(), dot = Lab.Accent)
            Readout(stringResource(R.string.stat_food), stats.foodCount.toString(), dot = Lab.Lime)
            Readout(stringResource(R.string.stat_species), stats.speciesCount.toString(), dot = Lab.Violet)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton(stringResource(R.string.env_save_dish), onClick = { saving = true }, icon = R.drawable.ic_save, kind = ButtonKind.PRIMARY)
            if (viewModel.activeLevel == null) {
                LabButton(stringResource(R.string.env_load_dish), onClick = { loading = true }, icon = R.drawable.ic_open)
                LabButton(stringResource(R.string.env_sterilize), onClick = { sterilizing = true }, icon = R.drawable.ic_sterilize, kind = ButtonKind.DANGER)
            }
        }
    }

    if (saving) {
        NameDialog(
            title = stringResource(R.string.dialog_save_dish),
            initial = stringResource(R.string.dish_default_name, formatNumber(stats.ageHours, 0)),
            note = stringResource(R.string.dialog_overwrite_dish_note),
            onConfirm = { name ->
                saving = false
                viewModel.saveDish(name)
            },
            onDismiss = { saving = false },
        )
    }
    if (loading) {
        LoadDishDialog(
            dishes = dishes,
            onLoad = { entry ->
                loading = false
                viewModel.loadDish(entry)
            },
            onDelete = viewModel::deleteDish,
            onDismiss = { loading = false },
        )
    }
    if (sterilizing) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_sterilize_title),
            text = stringResource(R.string.dialog_sterilize_text),
            confirm = stringResource(R.string.env_sterilize),
            onConfirm = viewModel::sterilize,
            onDismiss = { sterilizing = false },
        )
    }
}

@Composable
private fun LoadDishDialog(dishes: List<DishEntry>, onLoad: (DishEntry) -> Unit, onDelete: (DishEntry) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<DishEntry?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(stringResource(R.string.dialog_load_dish)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.dialog_load_dish_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = Lab.TextDim,
                )
                if (dishes.isEmpty()) {
                    Text(stringResource(R.string.dishes_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(dishes, key = { it.name }) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLoad(entry) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall, color = Lab.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = DateUtils.formatDateTime(
                                            context,
                                            entry.modified,
                                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                                        ) + " · " + Formatter.formatShortFileSize(context, entry.bytes),
                                        style = LabType.Mono,
                                        color = Lab.TextDim,
                                    )
                                }
                                LabIconButton(
                                    icon = R.drawable.ic_tool_delete,
                                    contentDescription = stringResource(R.string.genbank_delete),
                                    onClick = { deleting = entry },
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Lab.Raised,
    )
    deleting?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_title, entry.name),
            text = stringResource(R.string.dialog_delete_dish_text),
            confirm = stringResource(R.string.genbank_delete),
            onConfirm = { onDelete(entry) },
            onDismiss = { deleting = null },
        )
    }
}

/** Every parameter of [io.github.torintheone.petri.sim.SubstrateSettings], grouped as in SPEC «Среда». */
@Composable
private fun SettingsEditor(viewModel: PetriViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val help = stringArrayResource(R.array.help_environment)
    val change = viewModel::changeSettings
    var resetting by rememberSaveable { mutableStateOf(false) }

    EnvSection(R.string.env_food, R.drawable.ic_section_food) {
        SliderRow(
            label = stringResource(R.string.env_food_rate),
            value = settings.foodRate,
            range = 0f..1f,
            valueText = stringResource(R.string.per_hour_value, (settings.foodRate * SimConfig.FOOD_RATE_MAX_PER_HOUR).roundToInt()),
            onChange = { v -> change { it.copy(foodRate = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_FOOD_RATE],
        )
        SliderRow(
            label = stringResource(R.string.env_food_size),
            value = settings.foodSize,
            range = 0f..1f,
            valueText = stringResource(
                R.string.mass_value,
                decimal(SimConfig.FOOD_MASS_MIN + settings.foodSize * (SimConfig.FOOD_MASS_MAX - SimConfig.FOOD_MASS_MIN)),
            ),
            onChange = { v -> change { it.copy(foodSize = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_FOOD_SIZE],
        )
        SliderRow(
            label = stringResource(R.string.env_food_clumping),
            value = settings.foodClumping,
            range = 0f..1f,
            valueText = stringResource(R.string.clump_value, 1 + (settings.foodClumping * SimConfig.FOOD_CLUMP_MAX).toInt()),
            onChange = { v -> change { it.copy(foodClumping = v) } },
            levels = SimConfig.FOOD_CLUMP_MAX + 1,
            help = help[HELP_FOOD_CLUMPING],
        )
        SliderRow(
            label = stringResource(R.string.env_food_clump_size),
            value = settings.foodClumpSize,
            range = 0f..1f,
            valueText = stringResource(R.string.um_value, (settings.foodClumpSize * SimConfig.FOOD_CLUMP_RADIUS_MAX_UM).roundToInt()),
            onChange = { v -> change { it.copy(foodClumpSize = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_FOOD_CLUMP_SIZE],
        )
        SliderRow(
            label = stringResource(R.string.env_food_coat),
            value = settings.foodCoat,
            range = 0f..1f,
            valueText = stringResource(R.string.percent_value, (settings.foodCoat * 100f).roundToInt()),
            onChange = { v -> change { it.copy(foodCoat = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_FOOD_COAT],
        )
        SwitchRow(stringResource(R.string.env_mobile_food), settings.mobileFood, { v -> change { it.copy(mobileFood = v) } }, help[HELP_MOBILE_FOOD])
        SliderRow(
            label = stringResource(R.string.env_max_food),
            value = settings.maxFood.toFloat(),
            range = SubstrateSettings.MAX_FOOD.first.toFloat()..SubstrateSettings.MAX_FOOD.last.toFloat(),
            valueText = settings.maxFood.toString(),
            onChange = { v -> change { it.copy(maxFood = roundTo(v, COUNT_STEP)) } },
            levels = countLevels(SubstrateSettings.MAX_FOOD),
            help = help[HELP_MAX_FOOD],
        )
        SwitchRow(
            stringResource(R.string.env_corpses_food),
            settings.corpsesBecomeFood,
            { v -> change { it.copy(corpsesBecomeFood = v) } },
            help[HELP_CORPSES],
        )
    }
    EnvSection(R.string.env_light, R.drawable.ic_section_light) {
        SliderRow(
            label = stringResource(R.string.env_light_brightness),
            value = settings.lightBrightness,
            range = 0f..1f,
            valueText = decimal(settings.lightBrightness),
            onChange = { v -> change { it.copy(lightBrightness = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_LIGHT_BRIGHTNESS],
        )
        SliderRow(
            label = stringResource(R.string.env_light_width),
            value = settings.lightWidth,
            range = 0f..1f,
            valueText = if (settings.lightWidth >= 1f) stringResource(R.string.light_even) else decimal(settings.lightWidth),
            onChange = { v -> change { it.copy(lightWidth = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_LIGHT_WIDTH],
        )
        SliderRow(
            label = stringResource(R.string.env_light_rotation),
            value = settings.lightRotation,
            range = 0f..1f,
            valueText = if (settings.lightRotation <= 0f) {
                stringResource(R.string.light_still)
            } else {
                stringResource(R.string.light_turn_value, formatNumber(1.0 / (settings.lightRotation * SimConfig.LIGHT_MAX_TURNS_PER_HOUR), 0))
            },
            onChange = { v -> change { it.copy(lightRotation = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_LIGHT_ROTATION],
        )
    }
    EnvSection(R.string.env_physics, R.drawable.ic_section_physics) {
        SliderRow(
            label = stringResource(R.string.env_viscosity),
            value = settings.viscosity,
            range = SubstrateSettings.VISCOSITY,
            valueText = settings.viscosity.roundToInt().toString(),
            onChange = { v -> change { it.copy(viscosity = v) } },
            levels = VISCOSITY_LEVELS,
            help = help[HELP_VISCOSITY],
        )
        SliderRow(
            label = stringResource(R.string.env_dynamic_friction),
            value = settings.dynamicFriction,
            range = 0f..1f,
            valueText = decimal(settings.dynamicFriction),
            onChange = { v -> change { it.copy(dynamicFriction = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_DYNAMIC_FRICTION],
        )
        SliderRow(
            label = stringResource(R.string.env_static_friction),
            value = settings.staticFriction,
            range = 0f..1f,
            valueText = decimal(settings.staticFriction),
            onChange = { v -> change { it.copy(staticFriction = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_STATIC_FRICTION],
        )
        SliderRow(
            label = stringResource(R.string.env_gravity),
            value = settings.gravity,
            range = 0f..1f,
            valueText = decimal(settings.gravity),
            onChange = { v -> change { it.copy(gravity = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_GRAVITY],
        )
        SliderRow(
            label = stringResource(R.string.env_medium_density),
            value = settings.mediumDensity,
            range = 0f..1f,
            valueText = decimal(settings.mediumDensity),
            onChange = { v -> change { it.copy(mediumDensity = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_MEDIUM_DENSITY],
        )
        SliderRow(
            label = stringResource(R.string.env_density_gradient),
            value = settings.densityGradient,
            range = 0f..1f,
            valueText = decimal(settings.densityGradient),
            onChange = { v -> change { it.copy(densityGradient = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_DENSITY_GRADIENT],
        )
    }
    EnvSection(R.string.env_chemistry, R.drawable.ic_section_chemistry) {
        SliderRow(
            label = stringResource(R.string.env_salinity),
            value = settings.salinity,
            range = 0f..1f,
            valueText = stringResource(R.string.cost_factor_value, decimal(SimConfig.salinityFactor(settings.salinity), 1)),
            onChange = { v -> change { it.copy(salinity = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_SALINITY],
        )
        SliderRow(
            label = stringResource(R.string.env_nitrates),
            value = settings.nitrates,
            range = 0f..1f,
            valueText = decimal(settings.nitrates),
            onChange = { v -> change { it.copy(nitrates = v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_NITRATES],
        )
    }
    EnvSection(R.string.env_evolution, R.drawable.ic_section_evolution) {
        // The slider moves along √radiation, so the weak levels that matter most get most of its length.
        SliderRow(
            label = stringResource(R.string.env_radiation),
            value = sqrt(settings.radiation),
            range = 0f..1f,
            valueText = decimal(settings.radiation, 3),
            onChange = { v -> change { it.copy(radiation = v * v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_RADIATION],
            note = stringResource(R.string.radiation_note, formatNumber(SimConfig.mutationsPerHour(settings.radiation) * 100.0, 1)),
        )
        SwitchRow(
            stringResource(R.string.env_point_mutations_only),
            settings.pointMutationsOnly,
            { v -> change { it.copy(pointMutationsOnly = v) } },
            help[HELP_POINT_ONLY],
        )
        SwitchRow(
            stringResource(R.string.env_random_infection),
            settings.randomInfection,
            { v -> change { it.copy(randomInfection = v) } },
            help[HELP_INFECTION],
        )
        GenePoolEditor(settings.genePool, help[HELP_GENE_POOL]) { pool -> change { it.copy(genePool = pool) } }
    }
    EnvSection(R.string.env_rules, R.drawable.ic_section_rules) {
        SliderRow(
            label = stringResource(R.string.env_dish_diameter),
            value = settings.dishDiameterMm,
            range = SubstrateSettings.DISH_DIAMETER_MM,
            valueText = stringResource(R.string.mm_value, decimal(settings.dishDiameterMm)),
            onChange = { v -> change { it.copy(dishDiameterMm = v) } },
            levels = DISH_LEVELS,
            help = help[HELP_DISH_DIAMETER],
        )
        SliderRow(
            label = stringResource(R.string.env_max_cells),
            value = settings.maxCells.toFloat(),
            range = SubstrateSettings.MAX_CELLS.first.toFloat()..SubstrateSettings.MAX_CELLS.last.toFloat(),
            valueText = settings.maxCells.toString(),
            onChange = { v -> change { it.copy(maxCells = roundTo(v, COUNT_STEP)) } },
            levels = countLevels(SubstrateSettings.MAX_CELLS),
            help = help[HELP_MAX_CELLS],
        )
        SwitchRow(stringResource(R.string.env_aging), settings.aging, { v -> change { it.copy(aging = v) } }, help[HELP_AGING])
        SwitchRow(stringResource(R.string.env_deadly_walls), settings.deadlyWalls, { v -> change { it.copy(deadlyWalls = v) } }, help[HELP_DEADLY_WALLS])
        SwitchRow(
            stringResource(R.string.env_blocked_flagellum_kills),
            settings.blockedFlagellumKills,
            { v -> change { it.copy(blockedFlagellumKills = v) } },
            help[HELP_BLOCKED_FLAGELLUM],
        )
    }
    LabButton(
        text = stringResource(R.string.env_reset),
        onClick = { resetting = true },
        icon = R.drawable.ic_reset,
        modifier = Modifier.padding(top = 4.dp),
    )

    if (resetting) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_reset_title),
            text = stringResource(R.string.dialog_reset_text),
            confirm = stringResource(R.string.env_reset),
            onConfirm = viewModel::resetSettings,
            onDismiss = { resetting = false },
        )
    }
}

/** «Генофонд»: which cell types mutations and random newcomers may use. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenePoolEditor(pool: Set<CellType>, help: String, onChange: (Set<CellType>) -> Unit) {
    val typeNames = stringArrayResource(R.array.cell_types)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        ParameterName(stringResource(R.string.env_gene_pool), help)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            for (type in CellType.entries) {
                val chosen = type in pool
                val shape = MaterialTheme.shapes.small
                Text(
                    text = typeNames[type.ordinal],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chosen) Lab.Accent else Lab.TextDim,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (chosen) Lab.AccentSoft else Lab.Raised)
                        .border(1.dp, if (chosen) Lab.Accent.copy(alpha = CHIP_EDGE_ALPHA) else Lab.Hairline, shape)
                        .toggleable(value = chosen, role = Role.Checkbox, onValueChange = { onChange(if (chosen) pool - type else pool + type) })
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** A group of the medium's parameters that opens and closes; all start open. */
@Composable
private fun EnvSection(@StringRes title: Int, @DrawableRes icon: Int, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    SectionPanel(
        title = stringResource(title),
        icon = icon,
        expanded = expanded,
        onToggle = { expanded = !expanded },
        content = content,
    )
}

private fun roundTo(value: Float, step: Int): Int = (value / step).roundToInt() * step

private fun countLevels(range: IntRange): Int = (range.last - range.first) / COUNT_STEP + 1

private const val CHIP_EDGE_ALPHA = 0.6f

/** Music volume in steps of 5 %. */
private const val MUSIC_VOLUME_LEVELS = 21

/** Counts (cells, food) move in hundreds. */
private const val COUNT_STEP = 100

/** Viscosity 0–200 in steps of 10. */
private const val VISCOSITY_LEVELS = 21

/** Dish diameter 0.5–3 mm in steps of 0.25 mm. */
private const val DISH_LEVELS = 11

// Positions in R.array.help_environment.
private const val HELP_FOOD_RATE = 0
private const val HELP_FOOD_SIZE = 1
private const val HELP_FOOD_CLUMPING = 2
private const val HELP_FOOD_CLUMP_SIZE = 3
private const val HELP_FOOD_COAT = 4
private const val HELP_MOBILE_FOOD = 5
private const val HELP_MAX_FOOD = 6
private const val HELP_CORPSES = 7
private const val HELP_LIGHT_BRIGHTNESS = 8
private const val HELP_LIGHT_WIDTH = 9
private const val HELP_LIGHT_ROTATION = 10
private const val HELP_VISCOSITY = 11
private const val HELP_DYNAMIC_FRICTION = 12
private const val HELP_STATIC_FRICTION = 13
private const val HELP_GRAVITY = 14
private const val HELP_MEDIUM_DENSITY = 15
private const val HELP_DENSITY_GRADIENT = 16
private const val HELP_SALINITY = 17
private const val HELP_NITRATES = 18
private const val HELP_DISH_DIAMETER = 19
private const val HELP_MAX_CELLS = 20
private const val HELP_AGING = 21
private const val HELP_DEADLY_WALLS = 22
private const val HELP_BLOCKED_FLAGELLUM = 23
private const val HELP_RADIATION = 24
private const val HELP_POINT_ONLY = 25
private const val HELP_INFECTION = 26
private const val HELP_GENE_POOL = 27
