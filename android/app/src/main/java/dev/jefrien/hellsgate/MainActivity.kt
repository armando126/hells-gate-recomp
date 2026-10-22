package dev.jefrien.hellsgate

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.net.Uri
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

class MainActivity : ComponentActivity() {
    private data class CopyStats(val bytes: Long, val files: Long)

    private val gameFolderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) importGameFolder(uri)
    }

    private var updateGameImportStatus: ((String, Float?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        val hasImportedGame = File(filesDir, "game/default.xex").isFile
        setContent {
            var gameImportStatus by remember {
                mutableStateOf(
                    if (hasImportedGame) "Datos del juego listos"
                    else "Datos del juego: no seleccionados",
                )
            }
            var gameImportProgress by remember { mutableStateOf<Float?>(null) }
            updateGameImportStatus = { message, progress ->
                gameImportStatus = message
                gameImportProgress = progress
            }
            HellsgateTheme {
                HellsgateApp(
                    onStartGame = {
                        startActivity(Intent(this@MainActivity, GameActivity::class.java))
                    },
                    onChooseGameFolder = { gameFolderPicker.launch(null) },
                    gameImportStatus = gameImportStatus,
                    gameImportProgress = gameImportProgress,
                )
            }
        }
    }

    private fun importGameFolder(treeUri: Uri) {
        updateGameImportStatus?.invoke("Preparando importación…", null)
        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers do not offer persistable permissions; import now.
        }

        val destination = File(filesDir, "game")
        Thread {
            val result = runCatching {
                val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                val total = scanTree(treeUri, rootId)
                updateGameImportStatus?.let { callback ->
                    runOnUiThread {
                        callback(
                            "Copiando 0/${total.files} archivos (0 MB de ${total.bytes / 1_048_576} MB)…",
                            if (total.bytes > 0) 0f else 1f,
                        )
                    }
                }
                var copiedBytes = 0L
                var copiedFiles = 0L
                var lastUpdate = 0L
                val report: (Boolean) -> Unit = { force ->
                    val now = System.nanoTime()
                    if (force || now - lastUpdate > 100_000_000L) {
                        lastUpdate = now
                        val progress = if (total.bytes > 0) {
                            (copiedBytes.toDouble() / total.bytes).toFloat().coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                        updateGameImportStatus?.let { callback ->
                            runOnUiThread {
                                callback(
                                    "Copiando $copiedFiles/${total.files} archivos " +
                                        "(${copiedBytes / 1_048_576} MB de ${total.bytes / 1_048_576} MB)…",
                                    progress,
                                )
                            }
                        }
                    }
                }
                destination.mkdirs()
                copyTree(
                    treeUri,
                    rootId,
                    destination,
                    onBytes = { count -> copiedBytes += count; report(false) },
                    onFile = { copiedFiles++; report(true) },
                )
                updateGameImportStatus?.let { callback ->
                    runOnUiThread { callback("Validando default.xex…", 1f) }
                }
                File(destination, "default.xex").isFile
            }.getOrDefault(false)
            runOnUiThread {
                val message = if (result) {
                    "Datos del juego listos"
                } else {
                    "No se encontró default.xex en la carpeta elegida"
                }
                updateGameImportStatus?.invoke(message, if (result) 1f else null)
            }
        }.start()
    }

    private fun scanTree(treeUri: Uri, documentId: String): CopyStats {
        var bytes = 0L
        var files = 0L
        queryChildren(treeUri, documentId) { childId, _, mimeType, size ->
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val nested = scanTree(treeUri, childId)
                bytes += nested.bytes
                files += nested.files
            } else {
                bytes += size.coerceAtLeast(0L)
                files++
            }
        }
        return CopyStats(bytes, files)
    }

    private fun copyTree(
        treeUri: Uri,
        documentId: String,
        destination: File,
        onBytes: (Long) -> Unit,
        onFile: () -> Unit,
    ) {
        queryChildren(treeUri, documentId) { childId, name, mimeType, _ ->
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            val target = File(destination, name)
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                target.mkdirs()
                copyTree(treeUri, childId, target, onBytes, onFile)
            } else {
                contentResolver.openInputStream(childUri)?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            onBytes(read.toLong())
                        }
                    }
                }
                onFile()
            }
        }
    }

    private fun queryChildren(
        treeUri: Uri,
        documentId: String,
        visitor: (String, String, String, Long) -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            documentId,
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                val size = if (cursor.isNull(sizeIndex)) -1L else cursor.getLong(sizeIndex)
                visitor(childId, name, cursor.getString(typeIndex), size)
            }
        }
    }
}

@Composable
private fun HellsgateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFD84A3A),
            secondary = Color(0xFFE0A24A),
            background = Color(0xFF0D0909),
            surface = Color(0xFF211313),
        ),
        content = content,
    )
}

@Composable
private fun HellsgateApp(
    onStartGame: () -> Unit,
    onChooseGameFolder: () -> Unit,
    gameImportStatus: String,
    gameImportProgress: Float?,
) {
    var showSettings by remember { mutableStateOf(false) }
    var startingGame by remember { mutableStateOf(false) }
    val importing = gameImportStatus.contains("Preparando") ||
        gameImportStatus.contains("Copiando") ||
        gameImportStatus.contains("Validando")

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xD90D0909),
        ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                Text(
                    text = "HELL'S GATE",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Dante's Inferno",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Port Android nativo · ARM64 · Vulkan",
                    color = Color.White.copy(alpha = 0.65f),
                )
                Spacer(Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Estado del renderer", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("SDL/Vulkan se inicia en la pantalla de juego", color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(6.dp))
                        Text(gameImportStatus, color = Color.White.copy(alpha = 0.75f))
                        if (importing) {
                            Spacer(Modifier.height(8.dp))
                            if (gameImportProgress == null) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(
                                    progress = { gameImportProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (!startingGame) {
                                        startingGame = true
                                        onStartGame()
                                        startingGame = false
                                    }
                                },
                                enabled = !startingGame,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(if (startingGame) "CARGANDO…" else "JUGAR")
                            }
                            OutlinedButton(onClick = onChooseGameFolder) {
                                Text("ELEGIR DATOS")
                            }
                            OutlinedButton(onClick = { showSettings = !showSettings }) {
                                Text(if (showSettings) "OCULTAR" else "AJUSTES")
                            }
                        }
                        if (showSettings) {
                            Spacer(Modifier.height(16.dp))
                            Text("Resolución: nativa del dispositivo")
                            Text("Renderer: Vulkan", color = Color.White.copy(alpha = 0.65f))
                            Text(
                                "Controles táctiles: próximamente",
                                color = Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
        }
    }
}
}

@Preview(showBackground = true)
@Composable
private fun HellsgatePreview() {
    HellsgateTheme {
        HellsgateApp(
            onStartGame = {},
            onChooseGameFolder = {},
            gameImportStatus = "Preview",
            gameImportProgress = null,
        )
    }
}
