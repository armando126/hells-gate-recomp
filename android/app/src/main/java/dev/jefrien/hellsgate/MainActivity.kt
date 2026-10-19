package dev.jefrien.hellsgate

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.libsdl.app.SDL

class MainActivity : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("hells_gate_android")
        }
    }

    external fun nativeStatus(): String
    external fun nativeStartGame(): String
    external fun nativeSetSurface(surface: android.view.Surface)
    external fun nativeClearSurface()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reuse SDL3's Android JNI bootstrap while keeping our Compose Activity.
        SDL.setupJNI()
        SDL.initialize()
        SDL.setContext(this)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        setContent {
            HellsgateTheme {
                HellsgateApp(
                    nativeStatus = ::nativeStatus,
                    onStartGame = ::nativeStartGame,
                    onSurfaceCreated = ::nativeSetSurface,
                    onSurfaceDestroyed = ::nativeClearSurface,
                )
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
    nativeStatus: () -> String,
    onStartGame: () -> String,
    onSurfaceCreated: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    var status by remember { mutableStateOf("Esperando superficie Vulkan…") }
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        GameSurface(
            modifier = Modifier.fillMaxSize(),
            onCreated = {
                onSurfaceCreated(it)
                status = nativeStatus()
            },
            onDestroyed = {
                onSurfaceDestroyed()
                status = "Superficie Vulkan detenida"
            },
        )

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
                        Text(status, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { status = onStartGame() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text("JUGAR")
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

@Composable
private fun GameSurface(
    modifier: Modifier = Modifier,
    onCreated: (android.view.Surface) -> Unit,
    onDestroyed: () -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            SurfaceView(context).also { view ->
                view.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onCreated(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onDestroyed()
                    }
                })
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose(onDestroyed)
    }
}

@Preview(showBackground = true)
@Composable
private fun HellsgatePreview() {
    HellsgateTheme {
        HellsgateApp(
            nativeStatus = { "Preview" },
            onStartGame = { "Preview" },
            onSurfaceCreated = {},
            onSurfaceDestroyed = {},
        )
    }
}
