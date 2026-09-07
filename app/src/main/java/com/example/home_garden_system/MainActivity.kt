package com.example.home_garden_system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.home_garden_system.garden.GardenViewModel
import com.example.home_garden_system.garden.RepositoryFactory
import com.example.home_garden_system.ui.GardenApp
import com.example.home_garden_system.ui.theme.Home_Garden_SystemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Home_Garden_SystemTheme {
                val model: GardenViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        GardenViewModel(RepositoryFactory.create(applicationContext)) as T
                })
                GardenApp(model)
            }
        }
    }
}
