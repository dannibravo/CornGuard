package com.cornguard.app

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
 * Single-activity shell hosting the bottom-nav destinations declared in
 * res/navigation/nav_graph.xml (claude/05_DEVELOPMENT_PLAN.md Sprint 0: "base navigation").
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment
        binding.bottomNavigationView.setupWithNavController(navHostFragment.navController)

        observeConnectivity()
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
}
