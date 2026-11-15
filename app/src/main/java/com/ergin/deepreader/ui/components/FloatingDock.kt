package com.ergin.deepreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ergin.deepreader.ui.theme.DockActiveIconBg
import com.ergin.deepreader.ui.theme.FloatingDockPrimary
import com.ergin.deepreader.ui.theme.TextSubtlePlaceholder

enum class DockScreen {
    LIBRARY,
    READER,
    SETTINGS
}

@Composable
fun FloatingDock(
    selectedScreen: DockScreen,
    onScreenSelected: (DockScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(elevation = 12.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(FloatingDockPrimary)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockTabItem(
                text = "Библиотека",
                icon = Icons.Outlined.AutoStories,
                isSelected = selectedScreen == DockScreen.LIBRARY,
                onClick = { onScreenSelected(DockScreen.LIBRARY) }
            )
            DockTabItem(
                text = "Чтение",
                icon = Icons.Outlined.MenuBook,
                isSelected = selectedScreen == DockScreen.READER,
                onClick = { onScreenSelected(DockScreen.READER) }
            )
            DockTabItem(
                text = "Настройки",
                icon = Icons.Outlined.Settings,
                isSelected = selectedScreen == DockScreen.SETTINGS,
                onClick = { onScreenSelected(DockScreen.SETTINGS) }
            )
        }
    }
}

@Composable
private fun DockTabItem(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) DockActiveIconBg else Color.Transparent,
        label = "dockTabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else TextSubtlePlaceholder,
        label = "dockTabContent"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        if (isSelected) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.sp,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
