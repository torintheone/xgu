package io.github.torintheone.petri.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.PetriViewModel
import io.github.torintheone.petri.R

/** The main tabs (SPEC «Интерфейс»). */
enum class Tab(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    MICROSCOPE(R.string.tab_microscope, R.drawable.ic_tab_microscope),
    GENOME(R.string.tab_genome, R.drawable.ic_tab_genome),
    ENVIRONMENT(R.string.tab_environment, R.drawable.ic_tab_environment),
    GENBANK(R.string.tab_genbank, R.drawable.ic_tab_genbank),
}

/** Tabs at the bottom in portrait, on the side in landscape. */
@Composable
fun PetriApp(viewModel: PetriViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.MICROSCOPE) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Tab.entries.forEach { item ->
                        NavigationRailItem(
                            selected = item == tab,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.title)) },
                        )
                    }
                }
                TabContent(tab, viewModel, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TabContent(tab, viewModel, Modifier.weight(1f).fillMaxWidth())
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.title)) },
                        )
                    }
                }
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
                .padding(bottom = if (tab == Tab.MICROSCOPE) MICROSCOPE_TOOLBAR_CLEARANCE else 8.dp),
        )
    }
}

/** Keeps notes on the microscope above its tool bar. */
private val MICROSCOPE_TOOLBAR_CLEARANCE = 96.dp
