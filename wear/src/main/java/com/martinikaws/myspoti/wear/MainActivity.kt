package com.martinikaws.myspoti.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.martinikaws.myspoti.wear.ui.MyspotiTheme
import com.martinikaws.myspoti.wear.ui.NowPlayingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyspotiTheme { NowPlayingScreen() }
        }
    }
}
