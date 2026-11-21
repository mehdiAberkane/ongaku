// Demande d'une permission, et le cas où Android ne l'affiche plus : la page des paramètres de
// l'application devient alors le seul moyen de l'accorder.
package fr.devsecopsforce.ongaku.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

internal object PermissionRules {
    /**
     * Après deux refus, Android ne montre plus la demande et répond « refusé » aussitôt ; il ne
     * propose plus non plus d'explication ([showRationale] faux). Avant toute demande,
     * [showRationale] est faux aussi : seul un refus déjà reçu ([asked]) distingue les deux cas.
     */
    fun blocked(asked: Boolean, granted: Boolean, showRationale: Boolean): Boolean =
        asked && !granted && !showRationale
}

/**
 * [granted] : permission accordée. [blocked] : Android n'affiche plus la demande. [request] demande
 * la permission, ou ouvre les paramètres de l'application si elle est bloquée.
 */
internal class PermissionAccess(val granted: Boolean, val blocked: Boolean, val request: () -> Unit)

/** [permission] null : rien à demander sur cette version d'Android. */
@Composable
internal fun rememberPermissionAccess(
    permission: String?,
    onResult: (granted: Boolean) -> Unit = {},
): PermissionAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun isGranted() = permission == null ||
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun showRationale() = permission != null && activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    var granted by remember { mutableStateOf(isGranted()) }
    // Sauvegardé : la recréation de l'activité ne doit pas faire oublier le refus.
    var asked by rememberSaveable { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    fun update() {
        granted = isGranted()
        blocked = PermissionRules.blocked(asked, granted, showRationale())
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        update()
        onResult(granted)
    }
    // Retour des paramètres d'Android : la permission a pu y être accordée.
    LifecycleResumeEffect(permission) {
        val before = granted
        update()
        if (granted != before) onResult(granted)
        onPauseOrDispose { }
    }

    return PermissionAccess(granted, blocked) {
        when {
            permission == null || granted -> Unit
            blocked -> openAppSettings(activity ?: context)
            else -> launcher.launch(permission)
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Paramètres absents (appareil très modifié) : l'explication affichée reste valable.
    }
}
