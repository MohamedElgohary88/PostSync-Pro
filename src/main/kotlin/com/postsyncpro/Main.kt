package com.postsyncpro

import androidx.compose.ui.Alignment
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
import java.awt.Taskbar
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

    application {
        val appIcon = painterResource("logo.png")
        
        val windowState = rememberWindowState(
            size = DpSize(1440.dp, 900.dp),
            position = WindowPosition(Alignment.Center)
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "PostSync Pro",
            icon = appIcon,
            state = windowState,
        ) {
            App()
        }
    }
}
