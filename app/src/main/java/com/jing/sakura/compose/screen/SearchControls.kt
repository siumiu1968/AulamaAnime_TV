package com.jing.sakura.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.sakura.compose.common.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SearchCircleButton(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false, compact: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.size(if (compact) 34.dp else 44.dp)
        .onFocusChanged { focused = it.isFocused }
        .background(if (focused) AulamaTvColors.Cyan else Color(0xE621252E), CircleShape)
        .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .2f), CircleShape)
        .semantics { contentDescription = label; role = Role.Button; onClick { onClick(); true } }
        .customClick(onClick).focusable(), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(if (compact) 18.dp else 22.dp), tint = if (focused) Color(0xFF071016) else if(selected) AulamaTvColors.Pink else Color.White)
    }
}
