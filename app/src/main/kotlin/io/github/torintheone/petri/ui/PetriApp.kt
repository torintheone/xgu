package io.github.torintheone.petri.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

@Composable
private fun TabContent(tab: Tab, viewModel: PetriViewModel, modifier: Modifier) {
    when (tab) {
        Tab.MICROSCOPE -> MicroscopeScreen(viewModel, modifier)
        Tab.GENOME -> PlaceholderScreen(R.string.placeholder_genome, modifier)
        Tab.ENVIRONMENT -> PlaceholderScreen(R.string.placeholder_environment, modifier)
        Tab.GENBANK -> PlaceholderScreen(R.string.placeholder_genbank, modifier)
    }
}
