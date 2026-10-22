package io.github.torintheone.petri.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.torintheone.petri.R
import io.github.torintheone.petri.ui.theme.Lab
import io.github.torintheone.petri.ui.theme.LabType

/** A panel of the instrument: a dark fill with a hairline edge. [glass] lets the dish show through faintly. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = if (glass) Lab.Glass else Lab.Panel
    val border = BorderStroke(1.dp, Lab.Hairline)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, contentColor = Lab.Text, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, contentColor = Lab.Text, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** A small upper-case caption over a reading or a group. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Lab.TextDim) {
    Text(
        text = text.uppercase(),
        style = LabType.Eyebrow,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A reading: a caption, optionally with the colour [dot] of its line on a graph, over the value. */
@Composable
fun Readout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    valueColor: Color = Lab.Text,
    labelColor: Color = Lab.TextDim,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (dot != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(dot, CircleShape),
                )
            }
            Eyebrow(label, color = labelColor)
        }
        Text(text = value, style = LabType.Reading, color = valueColor, maxLines = 1)
    }
}

/** A round icon button; [active] marks the chosen one of a group (a tool, a mode of time). */
@Composable
fun LabIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color = Lab.TextDim,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) Lab.AccentSoft else Color.Transparent)
            .border(1.dp, if (active) Lab.Accent.copy(alpha = ACTIVE_EDGE_ALPHA) else Color.Transparent, CircleShape)
            .semantics { selected = active }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = when {
                !enabled -> Lab.TextFaint
                active -> Lab.Accent
                else -> tint
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

/** How a [LabButton] looks: the main action, an ordinary one, one that is switched on, or one that destroys. */
enum class ButtonKind { PRIMARY, SECONDARY, ACTIVE, DANGER }

/** A button of the instrument with an optional icon. */
@Composable
fun LabButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    kind: ButtonKind = ButtonKind.SECONDARY,
    enabled: Boolean = true,
) {
    val fill = when (kind) {
        ButtonKind.PRIMARY -> Lab.Accent
        ButtonKind.ACTIVE -> Lab.AccentSoft
        ButtonKind.SECONDARY, ButtonKind.DANGER -> Lab.Raised
    }
    val content = when (kind) {
        ButtonKind.PRIMARY -> Lab.OnAccent
        ButtonKind.ACTIVE -> Lab.Accent
        ButtonKind.SECONDARY -> Lab.Text
        ButtonKind.DANGER -> Lab.Danger
    }
    val edge = when (kind) {
        ButtonKind.PRIMARY -> Lab.Accent
        ButtonKind.ACTIVE -> Lab.Accent.copy(alpha = ACTIVE_EDGE_ALPHA)
        ButtonKind.SECONDARY -> Lab.HairlineStrong
        ButtonKind.DANGER -> Lab.Danger.copy(alpha = ACTIVE_EDGE_ALPHA)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = if (enabled) fill else Lab.Raised,
        contentColor = if (enabled) content else Lab.TextFaint,
        border = BorderStroke(1.dp, if (enabled) edge else Lab.Hairline),
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * An on/off switch drawn like the rest of the instrument. Without [onCheckedChange] it only shows the state,
 * for rows that are switched as a whole.
 */
@Composable
fun LabSwitch(checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: ((Boolean) -> Unit)? = null) {
    val offset by animateDpAsState(if (checked) SWITCH_TRAVEL else 0.dp, label = "switch")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(shape)
            .background(if (checked) Lab.Accent.copy(alpha = SWITCH_ON_FILL) else Lab.Raised)
            .border(1.dp, if (checked) Lab.Accent.copy(alpha = ACTIVE_EDGE_ALPHA) else Lab.HairlineStrong, shape)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .background(if (checked) Lab.Accent else Lab.TextDim, CircleShape),
        )
    }
}

/** A row of equal choices, one of them chosen. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(Lab.Raised)
            .border(1.dp, Lab.HairlineStrong, shape)
            .padding(3.dp),
    ) {
        for (option in options) {
            val chosen = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (chosen) Lab.AccentSoft else Color.Transparent)
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(option) }),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chosen) Lab.Accent else Lab.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A group of parameters in its own panel. With [onToggle] the title row opens and closes the group;
 * [trailing] goes at the end of the title row.
 */
@Composable
fun SectionPanel(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Lab.Panel,
        contentColor = Lab.Text,
        border = BorderStroke(1.dp, Lab.Hairline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                    .padding(start = 14.dp, end = 10.dp),
            ) {
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = Lab.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Eyebrow(title, color = Lab.Text, modifier = Modifier.weight(1f))
                trailing?.invoke()
                if (onToggle != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = stringResource(if (expanded) R.string.section_collapse else R.string.section_expand),
                        tint = Lab.TextDim,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                            .rotate(if (expanded) 180f else 0f),
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    content = content,
                )
            }
        }
    }
}

/** A thin line between rows of a panel. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Lab.Hairline),
    )
}

private const val ACTIVE_EDGE_ALPHA = 0.6f
private const val SWITCH_ON_FILL = 0.3f
private val SWITCH_TRAVEL = 18.dp
