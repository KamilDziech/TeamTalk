package com.ekotak.teamtalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ekotak.teamtalk.presentation.navigation.TeamTalkNavGraph
import com.ekotak.teamtalk.presentation.theme.TeamTalkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TeamTalkTheme {
                TeamTalkNavGraph()
            }
        }
    }
}
