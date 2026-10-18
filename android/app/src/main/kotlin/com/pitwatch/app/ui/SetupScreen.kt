package com.pitwatch.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwatch.app.AppContainer
import com.pitwatch.app.BuildConfig
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.app.ui.scoreboard.AccentButton
import com.pitwatch.app.ui.scoreboard.condensed
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.UserConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.launch

/** Checks the entered key and team against TBA (key, team, Nexus key). */
typealias SetupCheck = suspend (String, String, String) -> SetupValidator.Outcome

private val checkWithTba: SetupCheck = { apiKey, team, nexusKey ->
    val http = HttpClient(OkHttp)
    try {
        SetupValidator.validate(apiKey, team, nexusKey) { TbaClient(it, http, BuildConfig.TBA_BASE_URL) }
    } finally {
        http.close()
    }
}

@Composable
fun SetupScreen(container: AppContainer, config: UserConfig, validate: SetupCheck = checkWithTba) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey.orEmpty()) }
    var team by rememberSaveable { mutableStateOf(config.teamNumber?.toString().orEmpty()) }
    var nexusKey by rememberSaveable { mutableStateOf(config.nexusApiKey.orEmpty()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    // Not saveable: the check runs in this screen's scope, which a recreated activity (rotation, theme) cancels —
    // a restored "busy" would leave the button disabled on "Checking…" for good.
    var busy by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        // Edge-to-edge: keep the form clear of the status and navigation bars.
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SET UP PITWATCH", style = condensed(24.sp, FontWeight.ExtraBold))
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(ApiKeyHelp.TBA) })
        OutlinedTextField(team, { team = it }, label = { Text("Team number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(ApiKeyHelp.NEXUS) })
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AccentButton(
            if (busy) "Checking…" else "Continue",
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    val outcome = validate(apiKey, team, nexusKey)
                    busy = false
                    when (outcome) {
                        is SetupValidator.Outcome.Invalid -> message = outcome.message
                        is SetupValidator.Outcome.Valid -> {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            // The write swaps this screen out (cancelling its scope), so finish on the app scope.
                            val appContext = context.applicationContext
                            container.scope.launch {
                                container.stores.config.updateData {
                                    it.copy(teamNumber = outcome.teamNumber, apiKey = outcome.apiKey, nexusApiKey = outcome.nexusApiKey)
                                }
                                RefreshWorker.refreshNow(appContext)
                            }
                        }
                    }
                }
            },
        )
    }
}
