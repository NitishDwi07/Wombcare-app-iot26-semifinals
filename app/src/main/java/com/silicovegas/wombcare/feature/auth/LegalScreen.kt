package com.silicovegas.wombcare.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.silicovegas.wombcare.core.legal.LegalDocs
import com.silicovegas.wombcare.core.ui.theme.Spacing

/**
 * Renders a legal document. The very lightweight "markdown" here only distinguishes
 * `## headings` and `**bold**` inline — enough for our two documents without pulling in a
 * markdown dependency for a hackathon build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(docKey: String, onBack: () -> Unit) {
    val doc = LegalDocs.byKey(docKey)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc?.title ?: "Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (doc == null) {
                Text("Document not found.", style = MaterialTheme.typography.bodyLarge)
            } else {
                doc.body.lines().forEach { line ->
                    when {
                        line.startsWith("## ") -> Text(
                            line.removePrefix("## "),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        line.isBlank() -> {}
                        else -> Text(
                            line.replace("**", ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    "Version ${doc.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
        }
    }
}
