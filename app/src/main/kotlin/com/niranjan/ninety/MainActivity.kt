package com.niranjan.ninety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niranjan.ninety.ui.AppViewModel
import com.niranjan.ninety.ui.NinetyApp
import com.niranjan.ninety.ui.designsystem.NinetyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: AppViewModel = viewModel(factory = AppViewModel.factory(
                (application as NinetyApplication).container))
            val state by model.appState.collectAsStateWithLifecycle()
            val dark = when (state.settings.theme) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            NinetyTheme(dark = dark) {
                NinetyApp(model)
            }
        }
    }
}
