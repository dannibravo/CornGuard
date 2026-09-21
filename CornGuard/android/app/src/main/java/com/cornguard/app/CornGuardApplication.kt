package com.cornguard.app

import android.app.Application
import com.cornguard.app.di.ServiceLocator

/**
 * Application entry point. Owns process-lifetime singletons via [ServiceLocator] rather than a
 * DI framework — kept deliberately minimal for Sprint 0 (claude/05_DEVELOPMENT_PLAN.md).
 * Swapping in Hilt/Koin later is an internal engineering decision, not a contract change
 * (claude/01_MASTER_DEVELOPMENT_CONTEXT.md priority list, item 6).
 */
class CornGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
