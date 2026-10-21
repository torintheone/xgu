package io.github.torintheone.petri.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** «Среда» (SPEC «Интерфейс»): the dish buttons on top, then the parameters of the medium in groups. */
@Composable
fun EnvironmentScreen(viewModel: PetriViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DishCard(viewModel)
        SettingsEditor(viewModel)
        Text(
            text = stringResource(R.string.version_label, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** «Сохранить чашку», «Загрузить», «Стерилизовать». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishCard(viewModel: PetriViewModel) {
    val stats by viewModel.stats.collectAsStateWithLifecycle(initialValue = SimStats.EMPTY)
    val dishes by viewModel.dishes.collectAsStateWithLifecycle()
    var saving by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var sterilizing by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.env_dish), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.env_dish_state,
                    formatNumber(stats.ageHours, 1),
                    stats.cellCount,
                    stats.foodCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { saving = true }) { Text(stringResource(R.string.env_save_dish)) }
                OutlinedButton(onClick = { loading = true }) { Text(stringResource(R.string.env_load_dish)) }
                OutlinedButton(onClick = { sterilizing = true }) { Text(stringResource(R.string.env_sterilize)) }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = DateUtils.formatDateTime(
                                            context,
                                            entry.modified,
                                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                                        ) + " · " + Formatter.formatShortFileSize(context, entry.bytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { deleting = entry }) {
                                    Icon(painterResource(R.drawable.ic_tool_delete), contentDescription = stringResource(R.string.genbank_delete))
                                }
                            }
                        }
                    }
                }
            }
        },
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

    Column(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.env_food))
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

        SectionTitle(stringResource(R.string.env_light))
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

        SectionTitle(stringResource(R.string.env_physics))
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

        SectionTitle(stringResource(R.string.env_chemistry))
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

        SectionTitle(stringResource(R.string.env_evolution))
        // The slider moves along √radiation, so the weak levels that matter most get most of its length.
        SliderRow(
            label = stringResource(R.string.env_radiation),
            value = sqrt(settings.radiation),
            range = 0f..1f,
            valueText = stringResource(
                R.string.radiation_value,
                decimal(settings.radiation, 3),
                formatNumber(SimConfig.mutationsPerHour(settings.radiation) * 100.0, 1),
            ),
            onChange = { v -> change { it.copy(radiation = v * v) } },
            levels = SNAP_LEVELS,
            help = help[HELP_RADIATION],
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

        SectionTitle(stringResource(R.string.env_rules))
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

        OutlinedButton(onClick = { resetting = true }, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.env_reset))
        }
    }

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
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ParameterName(stringResource(R.string.env_gene_pool), help)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            for (type in CellType.entries) {
                val chosen = type in pool
                FilterChip(
                    selected = chosen,
                    onClick = { onChange(if (chosen) pool - type else pool + type) },
                    label = { Text(typeNames[type.ordinal]) },
                )
            }
        }
    }
}

private fun roundTo(value: Float, step: Int): Int = (value / step).roundToInt() * step

private fun countLevels(range: IntRange): Int = (range.last - range.first) / COUNT_STEP + 1

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
