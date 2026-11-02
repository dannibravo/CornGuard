package com.cornguard.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cornguard.app.databinding.ActivityMainBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.community.CommunityFragment
import com.cornguard.app.ui.onboarding.OnboardingDialogFragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.catch
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
        observeOnboarding()
        if (savedInstanceState == null) handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * A tapped push (see CornGuardMessagingService) marks its inbox entry read, then an outbreak
     * alert opens the Outbreak Map and a community reply opens the post's comments.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra(EXTRA_NOTIFICATION_ID)?.let { id ->
            intent.removeExtra(EXTRA_NOTIFICATION_ID)
            lifecycleScope.launch { runCatching { ServiceLocator.notificationRepository.markRead(id) } }
        }
        val postId = intent.getStringExtra(EXTRA_POST_ID)
        intent.removeExtra(EXTRA_POST_ID)
        val openMap = intent.getBooleanExtra(EXTRA_OPEN_MAP, false)
        intent.removeExtra(EXTRA_OPEN_MAP)

        val navController = (supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment)
            .navController
        when {
            openMap -> if (navController.currentDestination?.id != R.id.mapFragment) navController.navigate(R.id.mapFragment)
            postId != null ->
                navController.navigate(R.id.communityFragment, bundleOf(CommunityFragment.ARG_OPEN_POST_ID to postId))
        }
    }

    /**
     * A "Fresh" account (just registered) gets the onboarding tutorial once, over whatever screen
     * it's on — right after registering, or at the next launch if it was closed half-way. If this
     * phone already finished it but the server wasn't told (e.g. offline), tell it now instead.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeOnboarding() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState()
                    .flatMapLatest { user ->
                        if (user == null) emptyFlow() else ServiceLocator.userFarmRepository.observeUserProfile(user.uid)
                    }
                    .filterNotNull()
                    .catch { }
                    .collect { profile ->
                        if (!profile.isFresh) return@collect
                        if (!OnboardingDialogFragment.shouldShow(this@MainActivity, profile.userId, profile.isFresh)) {
                            runCatching { ServiceLocator.userFarmRepository.completeOnboarding() }
                        } else if (supportFragmentManager.findFragmentByTag(OnboardingDialogFragment.TAG) == null) {
                            OnboardingDialogFragment.newInstance(profile.userId)
                                .show(supportFragmentManager, OnboardingDialogFragment.TAG)
                        }
                    }
            }
        }
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
        const val EXTRA_NOTIFICATION_ID = "com.cornguard.app.NOTIFICATION_ID"
        const val EXTRA_POST_ID = "com.cornguard.app.POST_ID"
        private val DASHBOARD_CHILDREN =
            setOf(R.id.scanFragment, R.id.communityFragment, R.id.mapFragment, R.id.notificationsFragment)
    }
}
