package org.duckdns.woodysside.campingfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import org.duckdns.woodysside.campingfinder.ui.navigation.CampingNavHost
import org.duckdns.woodysside.campingfinder.ui.theme.CampingFinderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampingFinderTheme {
                CampingNavHost(navController = rememberNavController())
            }
        }
    }
}
