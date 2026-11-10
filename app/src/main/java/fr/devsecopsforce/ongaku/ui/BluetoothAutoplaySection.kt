package fr.devsecopsforce.ongaku.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.devsecopsforce.ongaku.data.PlaybackSettings

private data class PairedDevice(val address: String, val name: String)

private fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

private fun pairedDevices(context: Context): List<PairedDevice> = try {
    context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
        .map { device ->
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else device.name
            PairedDevice(device.address, name ?: device.address)
        }
        .sortedBy { it.name.lowercase() }
} catch (e: SecurityException) {
    emptyList()
}

@Composable
internal fun BluetoothAutoplaySection(settings: PlaybackSettings) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(settings.bluetoothAutoplay) }
    var selected by remember { mutableStateOf(settings.bluetoothDevices) }
    var permitted by remember { mutableStateOf(hasBluetoothPermission(context)) }
    var devices by remember { mutableStateOf(pairedDevices(context)) }

    fun refresh() {
        permitted = hasBluetoothPermission(context)
        devices = pairedDevices(context)
    }
    // Un appareil peut être associé dans les paramètres d'Android : on relit la liste au retour.
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }
    val requestPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionRequest.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Lecture automatique en Bluetooth", Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                settings.bluetoothAutoplay = it
                if (it && !permitted) requestPermission()
            },
        )
    }
    if (!enabled) return

    Spacer(Modifier.height(4.dp))
    if (!permitted) {
        Hint("Il manque l'autorisation Android « Appareils à proximité ». Elle ne donne aucun accès à tes fichiers.")
        OutlinedButton(onClick = requestPermission) { Text("Autoriser") }
        return
    }
    if (devices.isEmpty()) {
        Hint("Aucun appareil associé. Active le Bluetooth et associe ton appareil dans les paramètres d'Android.")
        return
    }
    Hint("La lecture reprend quand l'un de ces appareils se connecte.")
    devices.forEach { device ->
        val checked = device.address in selected
        val toggle = {
            selected = if (checked) selected - device.address else selected + device.address
            settings.bluetoothDevices = selected
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = toggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { toggle() })
            Text(device.name)
        }
    }
}
