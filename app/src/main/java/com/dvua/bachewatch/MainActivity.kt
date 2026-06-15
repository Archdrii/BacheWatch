package com.dvua.bachewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dvua.bachewatch.ui.theme.MyApplicationTheme
import com.dvua.bachewatch.ui.DashboardScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val viewModel = BacheViewModel(application)

    setContent {
      MyApplicationTheme {
        DashboardScreen(viewModel = viewModel)
      }
    }
  }
}
