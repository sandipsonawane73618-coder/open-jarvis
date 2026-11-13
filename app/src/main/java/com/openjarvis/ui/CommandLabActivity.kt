package com.openjarvis.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openjarvis.commandlab.CommandLabRoute
import com.openjarvis.commandlab.CommandLabSelfTest
import com.openjarvis.commandlab.CommandLabTestResult
import com.openjarvis.commandlab.CommandLogEntry
import com.openjarvis.commandlab.CommandLogStore
import com.openjarvis.ui.theme.OpenJarvisTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenJarvisTheme {
                CommandLabScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun CommandLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CommandLogStore(context) }
    var entries by remember { mutableStateOf(store.entries().reversed()) }
    var testResults by remember { mutableStateOf<List<CommandLabTestResult>>(emptyList()) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Command Lab", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { entries = store.entries().reversed() }) { Text("Refresh") }
        }
        Text(
            "Private on-device diagnostics • last 100 commands • sensitive text redacted",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, store.shareReport())
                }
                context.startActivity(Intent.createChooser(send, "Share redacted command report"))
            }, modifier = Modifier.weight(1f)) { Text("Share report") }
            OutlinedButton(onClick = { testResults = CommandLabSelfTest.run() }, modifier = Modifier.weight(1f)) {
                Text("Run 40 tests")
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            if (testResults.isNotEmpty()) {
                val passed = testResults.count { it.passed }
                val failed = testResults.size - passed
                Spacer(Modifier.height(12.dp))
                Text("Self-test summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$passed passed • $failed failed • ${testResults.size} total")
                CommandLabRoute.values().forEach { route ->
                    val routeResults = testResults.filter { it.actualRoute == route }
                    val routeFails = routeResults.count { !it.passed }
                    if (routeResults.isNotEmpty()) {
                        Text("${route.name.lowercase()}: ${routeResults.size} routed • $routeFails failed", style = MaterialTheme.typography.bodySmall)
                    }
                }
                testResults.forEach { result ->
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                (if (result.passed) "PASS" else "FAIL") + " • ${result.case.name}",
                                color = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(result.case.command, style = MaterialTheme.typography.bodyMedium)
                            Text("Route: ${result.actualRoute.name.lowercase()} • Intent: ${result.parsedIntent}", style = MaterialTheme.typography.bodySmall)
                            result.failReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Command history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${entries.size} / 100 saved locally", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entries.isEmpty()) {
                Text("No commands recorded yet. Use Jarvis voice or typed command input.", modifier = Modifier.padding(vertical = 18.dp))
            } else {
                entries.forEach { entry -> CommandLogCard(entry) }
            }
        }
    }
}

@Composable
private fun CommandLogCard(entry: CommandLogEntry) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val time = remember(entry.timeMillis) {
                SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()).format(Date(entry.timeMillis))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.route.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("$time • ${entry.source} • ${entry.language}", style = MaterialTheme.typography.labelSmall)
            }
            Text(entry.commandText, style = MaterialTheme.typography.bodyMedium)
            Text("STT confidence: ${entry.sttConfidence?.toString() ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
            entry.result?.let { Text("Result: $it", style = MaterialTheme.typography.bodySmall) }
            entry.failReason?.let { Text("Fail reason: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
