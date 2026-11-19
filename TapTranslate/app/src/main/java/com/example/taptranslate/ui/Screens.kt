package com.example.taptranslate.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taptranslate.MainViewModel
import com.example.taptranslate.Meanings
import com.example.taptranslate.PackState
import com.example.taptranslate.Selection
import com.example.taptranslate.Tx
import com.example.taptranslate.baseForm
import java.io.File

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val text = vm.message
        if (text != null) {
            snackbar.showSnackbar(text)
            vm.messageShown()
        }
    }

    // Survives rotation / process death while the camera app is open.
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto
        if (saved && uri != null) vm.loadImage(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadImage(uri)
    }

    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // clear older captures
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingPhoto = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            vm.showMessage("Couldn't open the camera (${e.message ?: "no camera app found"}).")
        }
    }

    BackHandler(enabled = vm.image != null) {
        if (vm.selection != null) vm.onTap(null) else vm.closeImage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val image = vm.image
        if (image == null) {
            HomeScreen(
                pack = vm.pack,
                reading = vm.reading,
                onCamera = ::launchCamera,
                onGallery = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRetryPack = vm::preparePack,
                modifier = Modifier.padding(padding),
            )
        } else {
            ViewerScreen(vm = vm, image = image, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun HomeScreen(
    pack: PackState,
    reading: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRetryPack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tap & Translate", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Photograph Turkish text, then tap any word to see it in English.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onCamera, enabled = !reading, modifier = Modifier.fillMaxWidth()) {
            Text("Take a photo")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onGallery, enabled = !reading, modifier = Modifier.fillMaxWidth()) {
            Text("Choose from gallery")
        }
        Spacer(Modifier.height(24.dp))
        if (reading) {
            StatusRow("Reading text…")
        } else {
            when (pack) {
                PackState.Checking -> StatusRow("Getting the Turkish → English language pack…")
                PackState.Ready -> Text(
                    "Turkish → English pack ready. Everything runs on your phone.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                PackState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Couldn't download the language pack. It needs internet once.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetryPack) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ViewerScreen(vm: MainViewModel, image: Bitmap, modifier: Modifier = Modifier) {
    val imageBitmap = remember(image) { image.asImageBitmap() }
    var boxHeight by remember { mutableIntStateOf(0) }
    // Put the card on the opposite half of the screen from the tapped word, so it never covers it.
    var cardOnTop by remember { mutableStateOf(false) }
    val selection = vm.selection

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxHeight = it.height },
    ) {
        TappableImage(
            bitmap = imageBitmap,
            words = vm.words,
            selected = selection?.word,
            onTap = { word, position ->
                cardOnTop = position.y > boxHeight / 2f
                vm.onTap(word)
            },
            modifier = Modifier.fillMaxSize(),
        )

        FilledTonalButton(
            onClick = vm::closeImage,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Text("← New photo")
        }

        if (selection == null) {
            Surface(
                shape = RoundedCornerShape(50),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Text(
                    "Tap a word · pinch to zoom",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            TranslationCard(
                selection = selection,
                onParagraph = vm::translateParagraph,
                onLookup = vm::lookupMeanings,
                onClose = { vm.onTap(null) },
                modifier = Modifier
                    .align(if (cardOnTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(top = if (cardOnTop) 56.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun TranslationCard(
    selection: Selection,
    onParagraph: () -> Unit,
    onLookup: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val word = selection.word
    val showLine = word.lineText.trim() != word.clean
    val canShowParagraph = word.blockText.trim() != word.lineText.trim()

    Card(
        modifier = modifier
            .padding(12.dp)
            .fillMaxWidth()
            .heightIn(max = 420.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        word.clean,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TxText(selection.wordTx, MaterialTheme.typography.headlineSmall, MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onClose) { Text("Close") }
            }

            MeaningsSection(
                meanings = selection.meanings,
                tapped = word.clean,
                onLookup = onLookup,
            )

            if (showLine) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "In context",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(word.lineText, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                TxText(selection.lineTx, MaterialTheme.typography.bodyLarge)
            }

            if (canShowParagraph) {
                val paragraphTx = selection.paragraphTx
                if (paragraphTx == null) {
                    TextButton(onClick = onParagraph) { Text("Translate whole paragraph") }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Whole paragraph",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TxText(paragraphTx, MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** How many senses to show per part of speech before folding the rest behind "Show more". */
private const val SENSES_SHOWN = 5

@Composable
private fun MeaningsSection(meanings: Meanings, tapped: String, onLookup: (String) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(
        "Meanings",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (meanings) {
        is Meanings.Loading -> CircularProgressIndicator(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .size(20.dp),
            strokeWidth = 2.dp,
        )
        is Meanings.Found -> FoundMeanings(meanings, tapped, onLookup)
        is Meanings.NotFound -> Text(
            "No dictionary entry for “${meanings.term}”. Turkish endings can hide the base word, " +
                "so the translation above is your best guide.",
            style = MaterialTheme.typography.bodyMedium,
        )
        is Meanings.Unavailable -> Text(
            meanings.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun FoundMeanings(found: Meanings.Found, tapped: String, onLookup: (String) -> Unit) {
    var expanded by remember(found) { mutableStateOf(false) }
    val hidden = found.groups.sumOf { (it.senses.size - SENSES_SHOWN).coerceAtLeast(0) }
    val baseForm = found.baseForm()

    // Shown when the entry we found isn't literally the tapped word (e.g. "Ev" -> "ev").
    if (found.term != tapped) {
        Text(
            "Dictionary entry: ${found.term}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    for (group in found.groups) {
        Spacer(Modifier.height(6.dp))
        Text(
            group.partOfSpeech.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        val senses = if (expanded) group.senses else group.senses.take(SENSES_SHOWN)
        senses.forEachIndexed { index, sense ->
            Text("${index + 1}. $sense", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (hidden > 0) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show fewer" else "Show $hidden more")
        }
    }
    if (baseForm != null) {
        FilledTonalButton(onClick = { onLookup(baseForm) }) {
            Text("Look up “$baseForm”")
        }
    }
    Text(
        "Meanings from Wiktionary (CC BY-SA)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TxText(tx: Tx, style: TextStyle, color: Color = Color.Unspecified) {
    when (tx) {
        is Tx.Loading -> CircularProgressIndicator(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .size(20.dp),
            strokeWidth = 2.dp,
        )
        is Tx.Done -> Text(tx.text, style = style, color = color)
        is Tx.Failed -> Text(
            tx.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
