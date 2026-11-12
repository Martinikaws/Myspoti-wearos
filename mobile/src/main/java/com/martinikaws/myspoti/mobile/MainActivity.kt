package com.martinikaws.myspoti.mobile

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Setup screen: the only thing the user must do on the phone is grant notification access. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SetupScreen() }
    }

    @Composable
    private fun SetupScreen() {
        var granted by remember { mutableStateOf(hasAccess()) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasAccess() }

        Column(
            Modifier.fillMaxSize().background(Color(0xFF121212)).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Myspoti Bridge", color = Color.White, fontSize = 28.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                if (granted) "Connected. Open Myspoti on your watch."
                else "Allow notification access so the watch can see and control what is playing.",
                color = Color(0xFFB3B3B3),
            )
            Spacer(Modifier.height(24.dp))
            if (!granted) {
                Button(
                    onClick = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                ) { Text("Grant access", color = Color.Black) }
            }
        }
    }

    private fun hasAccess() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}
