package com.cornguard.app.data.repository.convex

import com.cornguard.app.data.remote.convex.CornGuardConvexClient
import com.cornguard.app.data.remote.convex.TreatmentGuideDto
import com.cornguard.app.data.remote.convex.observe
import kotlinx.coroutines.flow.Flow

/**
 * Live list of the admin-managed treatment guides (backend/convex/diseaseReferences.ts `list`;
 * public, so it works signed out). Emits null when the query fails, so TreatmentGuideSync keeps the
 * cached guides rather than reading a failure as "no guides". Offline, the subscription just waits.
 */
fun CornGuardConvexClient.observeTreatmentGuides(): Flow<List<TreatmentGuideDto>?> =
    observe<List<TreatmentGuideDto>?>("diseaseReferences:list", emptyMap(), fallback = null)
