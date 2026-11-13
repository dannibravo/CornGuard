package com.cornguard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cornguard.app.databinding.ActivityMainBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.permissions.AppPermission
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

    // Android 13+ only shows push alerts (outbreaks, replies) once this is granted. The answer is
    // the user's; we only ask once per install.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigationView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // The farm pin picker needs the whole screen (map + name field above the keyboard).
            binding.bottomNavigationView.visibility =
                if (destination.id == R.id.farmPickerFragment) android.view.View.GONE else android.view.View.VISIBLE
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
                        val guideShowing = supportFragmentManager.findFragmentByTag(OnboardingDialogFragment.TAG) != null
                        if (!profile.isFresh) {
                            // Not over the guide: once it's done the profile turns "done" and we get here.
                            if (!guideShowing) maybeAskNotificationPermission()
                            return@collect
                        }
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

    /** Once per install, after sign-in, on Android 13+ when notifications aren't allowed yet. */
    private fun maybeAskNotificationPermission() {
        val permission = AppPermission.NOTIFICATIONS.manifestPermissions.firstOrNull() ?: return
        if (ServiceLocator.permissionManager.isGranted(AppPermission.NOTIFICATIONS)) return
        val prefs = getSharedPreferences(PREFS_PERMISSIONS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
        notificationPermissionLauncher.launch(permission)
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
        private const val PREFS_PERMISSIONS = "permissions"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
        private val DASHBOARD_CHILDREN =
            setOf(R.id.scanFragment, R.id.communityFragment, R.id.mapFragment, R.id.notificationsFragment)
    }
}
