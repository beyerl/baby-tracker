package de.beyerl.babytracker.ui.sync

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.beyerl.babytracker.BabyTrackerApp
import de.beyerl.babytracker.sync.SyncPrefs
import de.beyerl.babytracker.sync.SyncService
import de.beyerl.babytracker.ui.analytics.ChartCard
import de.beyerl.babytracker.ui.analytics.SectionSpacer
import de.beyerl.babytracker.ui.analytics.SectionTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val lastSyncFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/**
 * "Synchronisierung": pair the two phones via QR code, see the sync status,
 * sync now, keep syncing in the background, or unpair.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sync = (context.applicationContext as BabyTrackerApp).sync
    val status by sync.status.collectAsState()

    // Key shown as QR code on this phone, while the pairing dialog is open.
    var shownKey by remember { mutableStateOf<ByteArray?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var confirmUnpair by remember { mutableStateOf(false) }
    var background by remember { mutableStateOf(sync.prefs.backgroundEnabled) }

    val scanner = rememberLauncherForScan { text ->
        val key = text?.let(SyncPrefs::keyFromPairingText)
        if (key == null) {
            if (text != null) scanError = "Das ist kein Kopplungscode der Baby-Tracker-App."
        } else {
            sync.pair(key)
            SyncService.update(context)
            sync.syncNow()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synchronisierung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (!status.paired) {
                SectionTitle("Handys koppeln")
                ChartCard {
                    Text(
                        "Die Einträge werden direkt zwischen zwei Handys im selben WLAN abgeglichen – " +
                            "ohne Server und ohne Cloud. Dafür werden die Handys einmal gekoppelt:",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. Auf dem einen Handy „Code anzeigen“ tippen.\n2. Auf dem anderen Handy „Code scannen“ tippen und den QR-Code scannen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { shownKey = sync.prefs.newKey() }, modifier = Modifier.weight(1f)) {
                            Text("Code anzeigen")
                        }
                        FilledTonalButton(onClick = { scanner.launch(scanOptions()) }, modifier = Modifier.weight(1f)) {
                            Text("Code scannen")
                        }
                    }
                }
            } else {
                SectionTitle("Status")
                ChartCard {
                    StatusLine("Gekoppelt", "ja")
                    StatusLine("Anderes Handy im WLAN", if (status.peerFound) "gefunden" else "nicht gefunden")
                    StatusLine(
                        "Letzte Synchronisierung",
                        if (status.lastSyncAt > 0) {
                            Instant.ofEpochMilli(status.lastSyncAt).atZone(ZoneId.systemDefault()).format(lastSyncFmt)
                        } else {
                            "noch nie"
                        },
                    )
                    status.lastChanged?.let { StatusLine("Dabei übernommen", if (it == 1) "1 Eintrag" else "$it Einträge") }
                    status.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { sync.syncNow() }, enabled = !status.syncing, modifier = Modifier.fillMaxWidth()) {
                        if (status.syncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text("Jetzt synchronisieren")
                    }
                }
                SectionSpacer()
                ChartCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Im Hintergrund bereit", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Das andere Handy kann auch synchronisieren, wenn die App hier geschlossen ist " +
                                    "(dauerhafte, stille Benachrichtigung).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Switch(checked = background, onCheckedChange = {
                            background = it
                            sync.prefs.backgroundEnabled = it
                            SyncService.update(context)
                        })
                    }
                }
                SectionSpacer()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { shownKey = sync.prefs.key }, modifier = Modifier.weight(1f)) {
                        Text("Code erneut anzeigen")
                    }
                    OutlinedButton(onClick = { confirmUnpair = true }, modifier = Modifier.weight(1f)) {
                        Text("Kopplung aufheben")
                    }
                }
            }
        }
    }

    shownKey?.let { key ->
        PairingCodeDialog(
            key = key,
            onDone = {
                shownKey = null
                if (!status.paired) {
                    sync.pair(key)
                    SyncService.update(context)
                }
            },
            onCancel = { shownKey = null },
            alreadyPaired = status.paired,
        )
    }

    scanError?.let {
        AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text("Kopplung fehlgeschlagen") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { scanError = null }) { Text("OK") } },
        )
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Kopplung aufheben?") },
            text = { Text("Die Einträge bleiben auf beiden Handys erhalten, werden aber nicht mehr abgeglichen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    sync.unpair()
                    SyncService.update(context)
                }) { Text("Aufheben") }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/**
 * Shows the pairing QR code. The key is stored only when the user confirms
 * with "Fertig" (after the other phone scanned it), so cancelling leaves this
 * phone unpaired.
 */
@Composable
private fun PairingCodeDialog(key: ByteArray, alreadyPaired: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    val qr = remember(key) { qrBitmap(SyncPrefs.pairingText(key), 720) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Kopplungscode") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Auf dem anderen Handy unter Synchronisierung „Code scannen“ tippen und diesen Code scannen.")
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(12.dp),
                ) {
                    Image(qr, contentDescription = "QR-Code zum Koppeln", modifier = Modifier.size(240.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Der Code ist der Schlüssel für die Verschlüsselung – nicht weitergeben.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(if (alreadyPaired) "Schließen" else "Fertig") } },
        dismissButton = if (alreadyPaired) null else ({ TextButton(onClick = onCancel) { Text("Abbrechen") } }),
    )
}

private fun qrBitmap(text: String, size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun scanOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt("Kopplungscode des anderen Handys scannen")
    .setBeepEnabled(false)
    .setOrientationLocked(false)

/** Launches the offline ZXing scanner; [onResult] gets the scanned text, or null if cancelled. */
@Composable
private fun rememberLauncherForScan(onResult: (String?) -> Unit) =
    androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()) { result -> onResult(result.contents) }
