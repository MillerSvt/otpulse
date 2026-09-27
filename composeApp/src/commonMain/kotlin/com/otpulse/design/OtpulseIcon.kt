package com.otpulse.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Semantic icon aliases keep product meaning independent from the selected icon set. */
object OtpulseIcons {
    val Navigation = Icons.Rounded.Menu
    val Add = Icons.Rounded.Add
    val Edit = Icons.Rounded.Edit
    val Delete = Icons.Rounded.Delete
    val Scan = Icons.Rounded.QrCodeScanner
    val Back = Icons.AutoMirrored.Rounded.ArrowBack
    val Overflow = Icons.Rounded.MoreVert
    val Bluetooth = Icons.Rounded.Bluetooth
    val Settings = Icons.Rounded.Settings
    val Confirmation = Icons.Rounded.Check
    val Success = Icons.Rounded.CheckCircle
    val Disconnected = Icons.Rounded.RadioButtonUnchecked
    val Warning = Icons.Rounded.Warning
    val Error = Icons.Rounded.Error

    val Computer = Icons.Rounded.Computer
    val ExpandMore = Icons.Rounded.ExpandMore
    val Close = Icons.Rounded.Close
    val Forward = Icons.AutoMirrored.Rounded.KeyboardArrowRight
}

object OtpulseIconSizes {
    val Small = 20.dp
    val Standard = 24.dp
    val Large = 40.dp
}

@Composable
fun OtpulseIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
