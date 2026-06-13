package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.example.data.LedgerDatabase
import com.example.data.LedgerRepository
import com.example.ui.LedgerDashboard
import com.example.ui.theme.BalanceTrackerTheme
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.LedgerViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize local offline Room SQLite components
        val database = LedgerDatabase.getDatabase(applicationContext)
        val repository = LedgerRepository(database.ledgerDao())

        // 2. Initialize ViewModel with custom Factory
        val factory = LedgerViewModelFactory(repository, applicationContext)
        val viewModel = ViewModelProvider(this, factory)[LedgerViewModel::class.java]

        setContent {
            val sharedPref = remember { applicationContext.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
            val systemTheme = androidx.compose.foundation.isSystemInDarkTheme()
            var isDarkTheme by remember { androidx.compose.runtime.mutableStateOf(sharedPref.getBoolean("is_dark_theme", systemTheme)) }

            val toggleTheme: () -> Unit = {
                val newTheme = !isDarkTheme
                isDarkTheme = newTheme
                sharedPref.edit().putBoolean("is_dark_theme", newTheme).apply()
            }

            BalanceTrackerTheme(darkTheme = isDarkTheme) {
                LedgerDashboard(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = toggleTheme,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
