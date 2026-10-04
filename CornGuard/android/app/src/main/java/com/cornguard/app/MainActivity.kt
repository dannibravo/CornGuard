package com.cornguard.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cornguard.app.databinding.ActivityMainBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Single-activity shell hosting res/navigation/nav_graph.xml. The bottom bar has the design's
 * three tabs (Dashboard / History / Settings); Scan, Community and Map are opened from the
 * Dashboard cards and keep the Dashboard tab highlighted.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigationView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in DASHBOARD_CHILDREN) {
                binding.bottomNavigationView.menu.findItem(R.id.homeFragment).isChecked = true
            }
        }

        observeConnectivity()
        if (savedInstanceState == null) handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    /** An outbreak alert opens the Outbreak Map (see CornGuardMessagingService). */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_MAP, false) != true) return
        intent.removeExtra(EXTRA_OPEN_MAP)
        val navController = (supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment)
            .navController
        if (navController.currentDestination?.id != R.id.mapFragment) navController.navigate(R.id.mapFragment)
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.connectivityObserver.observe().collect { isOnline ->
                    binding.offlineStatusBanner.setOnline(isOnline)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_MAP = "com.cornguard.app.OPEN_MAP"
        private val DASHBOARD_CHILDREN = setOf(R.id.scanFragment, R.id.communityFragment, R.id.mapFragment)
    }
}
