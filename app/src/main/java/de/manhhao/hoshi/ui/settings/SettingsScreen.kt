package de.manhhao.hoshi.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.manhhao.hoshi.HoshiDicts
import java.io.File

private fun rebuildLookup(lookupObject: Long, directory: File) {
    val dicts =
        directory.listFiles()?.filter { it.isDirectory }?.map { it.absolutePath }?.toTypedArray()
            ?: emptyArray()
    HoshiDicts.rebuildQuery(lookupObject, dicts, dicts, dicts)
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var isImporting by remember { mutableStateOf(false) }
    var importResultText by remember { mutableStateOf("") }
    var lookupText by remember { mutableStateOf("") }
    var lookupResultText by remember { mutableStateOf("") }

    val directory = remember { File(context.filesDir, "Dictionaries").apply { mkdirs() } }

    LaunchedEffect(Unit) {
        rebuildLookup(HoshiDicts.lookupObject, directory)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        importResultText = "importing..."
        Thread {
            try {
                val zip = File(context.cacheDir, "temp.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    zip.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("failed to copy dictionary")

                val r = HoshiDicts.importDictionary(zip.absolutePath, directory.absolutePath)
                zip.delete()

                rebuildLookup(HoshiDicts.lookupObject, directory)

                isImporting = false
                importResultText = "success=${r.success} terms=${r.termCount} meta=${r.metaCount}"
            } catch (e: Exception) {
                isImporting = false
                importResultText = "import failed: ${e.message}"
            }
        }.start()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings")
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { picker.launch(arrayOf("application/zip")) },
            enabled = !isImporting
        ) {
            Text("Import dictionary")
        }
        Text(importResultText)

        OutlinedTextField(
            value = lookupText,
            onValueChange = { lookupText = it },
            label = { Text("Lookup") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (lookupText.isBlank()) {
                    lookupResultText = ""
                    return@KeyboardActions
                }
                val results = HoshiDicts.lookup(HoshiDicts.lookupObject, lookupText, 1)
                lookupResultText = if (results.isEmpty()) {
                    ""
                } else {
                    results.joinToString("\n\n") { lr ->
                        val t = lr.term
                        buildString {
                            appendLine(t.expression)
                            appendLine(t.reading)
                            if (t.glossaries.isNotEmpty()) {
                                append(t.glossaries[0].glossary)
                            }
                        }.trimEnd()
                    }
                }
            })
        )
        Text(lookupResultText)
    }
}
