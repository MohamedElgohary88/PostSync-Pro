package com.postsyncpro

import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.res.painterResource
import com.postsyncpro.data.database.AppDatabase
import com.postsyncpro.di.appModule
import com.postsyncpro.presentation.App
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.awt.Dimension
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Taskbar
import java.awt.event.KeyEvent
import javax.imageio.ImageIO

fun main() {
    System.setProperty("apple.awt.application.name", "PostSync Pro")

    // Initialize database
    AppDatabase.initDatabase()

    // Initialize Koin DI before the Compose application starts.
    startKoin {
        modules(appModule)
    }

    try {
        val taskbar = Taskbar.getTaskbar()
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("logo.png")
        if (stream != null) {
            val image = ImageIO.read(stream)
            taskbar.iconImage = image
        }
    } catch (e: Exception) {
        // Ignore taskbar errors
    }

    try {
        application {
            val appIcon = painterResource("logo.png")
            var commandPaletteSignal by remember { mutableIntStateOf(0) }

            val windowState = rememberWindowState(
                size = DpSize(1440.dp, 900.dp),
                position = WindowPosition(Alignment.Center)
            )

            Window(
                onCloseRequest = ::exitApplication,
                title = "PostSync Pro",
                icon = appIcon,
                state = windowState,
                onPreviewKeyEvent = { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.K &&
                        (event.isMetaPressed || event.isCtrlPressed)
                    ) {
                        commandPaletteSignal++
                        true
                    } else {
                        false
                    }
                }
            ) {
                DisposableEffect(Unit) {
                    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    val dispatcher = KeyEventDispatcher { event ->
                        if (
                            event.id == KeyEvent.KEY_PRESSED &&
                            event.keyCode == KeyEvent.VK_K &&
                            (event.isMetaDown || event.isControlDown)
                        ) {
                            commandPaletteSignal++
                            true
                        } else {
                            false
                        }
                    }
                    focusManager.addKeyEventDispatcher(dispatcher)
                    onDispose { focusManager.removeKeyEventDispatcher(dispatcher) }
                }
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(1_080, 700)
                }
                App(commandPaletteSignal = commandPaletteSignal)
            }
        }
    } finally {
        stopKoin()
    }
}
