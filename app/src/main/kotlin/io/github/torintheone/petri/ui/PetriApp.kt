package io.github.torintheone.petri.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R
import io.github.torintheone.petri.ui.theme.Lab

/** The main tabs (SPEC «Интерфейс»). */
enum class Tab(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    MICROSCOPE(R.string.tab_microscope, R.drawable.ic_tab_microscope),
    GENOME(R.string.tab_genome, R.drawable.ic_tab_genome),
    ENVIRONMENT(R.string.tab_environment, R.drawable.ic_tab_environment),
    GENBANK(R.string.tab_genbank, R.drawable.ic_tab_genbank),
}

/**
 * Tabs at the bottom in portrait, on the side in landscape. The bar takes the system insets on its side,
 * the screens take the rest (status bar, cut-outs).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetriApp(viewModel: PetriViewModel) {
    val select: (Tab) -> Unit = { viewModel.tab = it }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Lab.Background),
    ) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(viewModel.tab, select)
                TabContent(
                    tab = viewModel.tab,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TabContent(
                    tab = viewModel.tab,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(WindowInsets.navigationBars),
                )
                NavigationBar(viewModel.tab, select)
            }
        }
    }
}

/** The tab's screen, with short notes (saved, loaded, errors) popping up at its bottom. */
@Composable
private fun TabContent(tab: Tab, viewModel: PetriViewModel, modifier: Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = if (message.argument != null) resources.getString(message.text, message.argument) else resources.getString(message.text)
            snackbar.showSnackbar(text)
        }
    }
    Box(modifier) {
        when (tab) {
            Tab.MICROSCOPE -> MicroscopeScreen(viewModel, Modifier.fillMaxSize())
            Tab.GENOME -> GenomeScreen(viewModel, Modifier.fillMaxSize())
            Tab.ENVIRONMENT -> EnvironmentScreen(viewModel, Modifier.fillMaxSize())
            Tab.GENBANK -> GenbankScreen(viewModel, Modifier.fillMaxSize())
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = if (tab == Tab.MICROSCOPE) MICROSCOPE_DOCK_CLEARANCE else 8.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = MaterialTheme.shapes.medium,
                containerColor = Lab.Raised,
                contentColor = Lab.Text,
                actionColor = Lab.Accent,
                modifier = Modifier.border(1.dp, Lab.HairlineStrong, MaterialTheme.shapes.medium),
            )
        }
    }
}

@Composable
private fun NavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Lab.Panel),
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .height(BAR_HEIGHT),
        ) {
            for (item in Tab.entries) {
                NavigationItem(
                    tab = item,
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    rail = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun NavigationRail(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxHeight()
            .background(Lab.Panel),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                .width(RAIL_WIDTH),
        ) {
            for (item in Tab.entries) {
                NavigationItem(
                    tab = item,
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    rail = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RAIL_ITEM_HEIGHT),
                )
            }
        }
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Lab.Hairline),
        )
    }
}

/** A tab: icon and name; the chosen one is cyan and marked by a short line at the edge of the bar. */
@Composable
private fun NavigationItem(tab: Tab, selected: Boolean, onClick: () -> Unit, rail: Boolean, modifier: Modifier) {
    val color = if (selected) Lab.Accent else Lab.TextDim
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (!selected) return@drawBehind
                val thickness = 2.dp.toPx()
                if (rail) {
                    drawRect(Lab.Accent, topLeft = Offset(0f, size.height * 0.25f), size = Size(thickness, size.height * 0.5f))
                } else {
                    drawRect(Lab.Accent, topLeft = Offset(size.width * 0.3f, 0f), size = Size(size.width * 0.4f, thickness))
                }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(
                text = stringResource(tab.title),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Keeps notes on the microscope above its tool dock. */
private val MICROSCOPE_DOCK_CLEARANCE = 120.dp
private val BAR_HEIGHT = 60.dp
private val RAIL_WIDTH = 80.dp
private val RAIL_ITEM_HEIGHT = 68.dp
