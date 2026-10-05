package com.pitwatch.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pitwatch.app.AppContainer
import com.pitwatch.app.BuildConfig
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.UserConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(container: AppContainer, config: UserConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey.orEmpty()) }
    var team by rememberSaveable { mutableStateOf(config.teamNumber?.toString().orEmpty()) }
    var nexusKey by rememberSaveable { mutableStateOf(config.nexusApiKey.orEmpty()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Set up PitWatch", style = MaterialTheme.typography.headlineSmall)
        Text("Get a read API key from your account page on thebluealliance.com.")
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true)
        OutlinedTextField(team, { team = it }, label = { Text("Team number") }, singleLine = true)
        OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key (optional)") }, singleLine = true)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    val http = HttpClient(OkHttp)
                    val outcome = SetupValidator.validate(apiKey, team, nexusKey) { TbaClient(it, http, BuildConfig.TBA_BASE_URL) }
                    http.close()
                    busy = false
                    when (outcome) {
                        is SetupValidator.Outcome.Invalid -> message = outcome.message
                        is SetupValidator.Outcome.Valid -> {
                            container.stores.config.updateData {
                                it.copy(teamNumber = outcome.teamNumber, apiKey = outcome.apiKey, nexusApiKey = outcome.nexusApiKey)
                            }
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            RefreshWorker.ensureScheduled(context)
                        }
                    }
                }
            },
        ) { Text(if (busy) "Checking…" else "Continue") }
    }
}
