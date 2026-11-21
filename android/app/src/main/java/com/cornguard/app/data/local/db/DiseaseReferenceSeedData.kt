package com.cornguard.app.data.local.db

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import com.cornguard.app.model.DiseaseCode

/**
 * PLACEHOLDER treatment/prevention content, seeded once so the Treatment screen has something to
 * render during development. This is explicitly NOT real agricultural guidance — per
 * claude/16_UI_UX_AND_DIAGRAM_REVISION_GUIDE.md ("Do not allow Claude to invent pesticide
 * instructions or agricultural treatments beyond the verified project reference content") and
 * claude/09_ML_MODEL_CONTRACT.md's Treatment Guidance Boundary, real content must come from an
 * approved agricultural source before this ships to an actual farmer. Every string below says so
 * on-screen, not just in this comment — see TreatmentFragment's disclaimer banner too.
 */
object DiseaseReferenceSeedData {

    private const val PLACEHOLDER_NOTICE = "[PLACEHOLDER — not verified agricultural guidance] "

    val all: List<DiseaseReferenceEntity> = listOf(
        entry(
            code = DiseaseCode.COMMON_RUST,
            name = "Common Rust",
            symptoms = "Small, reddish-brown pustules scattered across both leaf surfaces."
        ),
        entry(
            code = DiseaseCode.GRAY_LEAF_SPOT,
            name = "Gray Leaf Spot",
            symptoms = "Rectangular, tan-to-gray lesions bounded by leaf veins, usually starting in the lower canopy."
        ),
        entry(
            code = DiseaseCode.NORTHERN_LEAF_BLIGHT,
            name = "Northern Leaf Blight",
            symptoms = "Elongated, elliptical \"cigar-shaped\" lesions, several centimeters long."
        ),
        entry(
            code = DiseaseCode.HEALTHY,
            name = "Healthy",
            symptoms = "Uniform green pigmentation, no visible lesions or discoloration."
        )
    )

    private fun entry(code: String, name: String, symptoms: String) = DiseaseReferenceEntity(
        diseaseCode = code,
        displayName = name,
        symptoms = symptoms,
        treatmentSteps = PLACEHOLDER_NOTICE + "Consult a local agricultural technician before applying any treatment.",
        preventionSteps = PLACEHOLDER_NOTICE + "Consult a local agricultural technician for region-appropriate prevention practices.",
        sourceReference = "PLACEHOLDER — pending approved agricultural source",
        contentVersion = "dev-placeholder-0.1",
        updatedAt = System.currentTimeMillis()
    )
}
