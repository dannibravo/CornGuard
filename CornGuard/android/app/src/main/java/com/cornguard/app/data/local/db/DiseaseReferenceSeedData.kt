package com.cornguard.app.data.local.db

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import com.cornguard.app.model.DiseaseCode
import com.cornguard.app.model.ReviewStatus

/**
 * Offline disease reference content, upserted at every startup so updated content reaches devices
 * that already have an older copy. The model's "Blight" class = [DiseaseCode.NORTHERN_LEAF_BLIGHT].
 *
 * Content version 2026-10-evidence-v1 replaces the caps 3 knowledge base. The three disease entries
 * were checked against published sources — the Crop Protection Network corn fungicide efficacy
 * table (Feb 2026), the Philippine FPA list of registered pesticides (30 Sep 2026) and the studies
 * listed with their evidence grades in docs/TREATMENT_SOURCES.md — so they carry
 * [ReviewStatus.SOURCES_REVIEWED]. They have NOT been reviewed by an agricultural expert
 * (claude/09_ML_MODEL_CONTRACT.md Treatment Guidance Boundary); the on-screen disclaimer stays
 * until one signs off in docs/TREATMENT_SOURCES.md and the entries are set to
 * [ReviewStatus.EXPERT_VERIFIED] with the reviewer's name.
 */
object DiseaseReferenceSeedData {

    private const val CONTENT_VERSION = "2026-10-evidence-v1"
    private const val SOURCE_REFERENCE =
        "Crop Protection Network corn fungicide efficacy table (Feb 2026); Philippine FPA registered " +
            "pesticides (30 Sep 2026); full list in CornGuard docs/TREATMENT_SOURCES.md"
    private const val REVIEWED_BY = "CornGuard team literature review (CPN 2026, FPA registry 2026)"

    /** 2026-10-30 00:00 UTC — fixed so the label's date doesn't change on every launch. */
    private const val REVIEWED_AT = 1_793_318_400_000L

    private const val SAFETY =
        "Safety:\n" +
            "• Wear gloves, a mask, long sleeves and boots while mixing and spraying.\n" +
            "• Keep out of the field until the spray has dried, and follow the waiting period before harvest on the label.\n" +
            "• Do not spray near wells, rivers or where children play. Wash up and change clothes after spraying."

    private const val NO_HOME_REMEDY =
        "Home Remedies:\n" +
            "No homemade spray has been shown in field trials to control this disease on corn. Put your effort into the prevention steps instead — they are cheap and they work season after season."

    val all: List<DiseaseReferenceEntity> = listOf(
        entry(
            code = DiseaseCode.NORTHERN_LEAF_BLIGHT,
            name = "Northern Leaf Blight",
            symptoms = "Elongated, elliptical \"cigar-shaped\" lesions, several centimeters long.",
            treatment =
                "Chemical Option:\n" +
                    "Use a fungicide registered for corn in the Philippines:\n" +
                    "• Azoxystrobin (e.g. Amistar 25 SC) — label rate for corn is 2–4 tablespoons per 16-liter knapsack.\n" +
                    "• A product combining azoxystrobin + difenoconazole whose label lists northern corn leaf blight (around 500–750 mL product per hectare).\n" +
                    "• Cheaper option: mancozeb (labeled for \"Helminthosporium leaf spot\" of corn, 3–6 tablespoons per 16 liters). It only protects leaves that are not yet infected and works less well than the options above.\n" +
                    "Always check that the label lists corn, and use the rate on the label.\n\n" +
                    "When and How to Spray:\n" +
                    "• Start checking your field about 2 weeks before tassels appear.\n" +
                    "• Spray when lesions are spreading toward the ear leaf, especially in warm, humid or rainy weather. The most useful time is from tasseling to silking.\n" +
                    "• Cover all leaves evenly, early in the morning or late afternoon.\n" +
                    "• If a second spray is needed, switch to a product from a different fungicide group to slow resistance.\n\n" +
                    SAFETY + "\n\n" +
                    NO_HOME_REMEDY,
            causes =
                "Northern Leaf Blight is caused by the fungus Exserohilum turcicum. It thrives in:\n\n" +
                    "• Moderate temperatures with long periods of leaf wetness — dew, fog and rain, common during the wet season in Bukidnon.\n" +
                    "• Infected debris left on the field — the fungus survives on old corn stalks and leaves and re-infects the next crop.\n" +
                    "• Planting corn after corn in the same field.\n" +
                    "• Susceptible corn varieties.",
            duration =
                "Treatment Duration:\n" +
                    "• Usually 1–2 well-timed sprays around tasseling are enough. Follow the label for the interval between sprays and the maximum number per season.\n" +
                    "• You should see the spread to new leaves slow within about 2 weeks.\n" +
                    "• Damaged leaves will NOT recover — the goal is to protect the ear leaf and the leaves above it so the harvest is not lost.",
            prevention =
                "How to Prevent Blight from Coming Back:\n\n" +
                    "1. Plant varieties resistant to northern leaf blight — ask your DA technician or seed supplier. Resistance to one disease does not cover the others.\n" +
                    "2. Practice crop rotation — plant a non-corn crop (like monggo, peanut or camote) for at least one season.\n" +
                    "3. Manage crop residue after harvest — plow it under or compost it so it breaks down before the next corn crop.\n" +
                    "4. Don't plant too close — follow the recommended spacing (about 75 cm between rows) so leaves dry faster.\n" +
                    "5. Scout weekly from knee height onward, and scan suspicious leaves with this app."
        ),
        entry(
            code = DiseaseCode.COMMON_RUST,
            name = "Common Rust",
            symptoms = "Small, reddish-brown pustules scattered across both leaf surfaces.",
            treatment =
                "Chemical Option:\n" +
                    "Use a fungicide registered for corn in the Philippines:\n" +
                    "• Azoxystrobin (e.g. Amistar 25 SC) — labeled for leaf rust of corn, 2–4 tablespoons per 16-liter knapsack.\n" +
                    "• A product combining propiconazole + difenoconazole or azoxystrobin + difenoconazole whose label lists rust on corn.\n" +
                    "Products with propiconazole alone are weaker against southern rust, a rust common in warm lowland areas that looks similar — prefer azoxystrobin or a combination product.\n" +
                    "Always check that the label lists corn, and use the rate on the label.\n\n" +
                    "When and How to Spray:\n" +
                    "• Rust on a few lower leaves of field corn often does not need spraying.\n" +
                    "• Spray when pustules are spreading up the plant before or around tasseling, especially on susceptible varieties or in long wet, cool spells.\n" +
                    "• Cover both sides of the leaves.\n" +
                    "• If a second spray is needed, switch to a product from a different fungicide group.\n\n" +
                    SAFETY + "\n\n" +
                    NO_HOME_REMEDY,
            causes =
                "Common Rust is caused by the fungus Puccinia sorghi. It spreads through:\n\n" +
                    "• Wind-blown spores — they can travel long distances, so even a clean field can get infected from a neighbor's farm.\n" +
                    "• Cool, moist conditions — temperatures around 15–25°C with high humidity, common in the higher-elevation areas of Bukidnon.\n" +
                    "• Susceptible corn varieties.\n" +
                    "In warm lowland areas a similar disease, southern rust (Puccinia polysora), is also common — the treatment above covers both.",
            duration =
                "Treatment Duration:\n" +
                    "• Usually 1–2 sprays. Follow the label for the interval between sprays and the maximum number per season.\n" +
                    "• New leaves should stay clean within 1–2 weeks if the spray worked.\n" +
                    "• Leaves that already have pustules will keep their spots.",
            prevention =
                "How to Prevent Common Rust from Coming Back:\n\n" +
                    "1. Plant rust-resistant varieties — ask your DA technician or seed supplier for varieties rated for rust.\n" +
                    "2. Plant on time — avoid late planting that puts young corn in the coolest, wettest months.\n" +
                    "3. Keep good spacing so leaves dry faster.\n" +
                    "4. Monitor weekly — check both sides of the lower leaves; catching rust early gives you time to decide on spraying.\n" +
                    "5. Remove volunteer corn plants that can carry the fungus between seasons."
        ),
        entry(
            code = DiseaseCode.GRAY_LEAF_SPOT,
            name = "Gray Leaf Spot",
            symptoms = "Rectangular, tan-to-gray lesions bounded by leaf veins, usually starting in the lower canopy.",
            treatment =
                "Chemical Option:\n" +
                    "Use a fungicide registered for corn in the Philippines:\n" +
                    "• A product combining azoxystrobin + difenoconazole whose label lists gray leaf spot (around 500–750 mL product per hectare).\n" +
                    "• Azoxystrobin (e.g. Amistar 25 SC) — 2–4 tablespoons per 16-liter knapsack.\n" +
                    "• Chlorothalonil products labeled for Cercospora leaf spot of corn are another option; they protect leaves that are not yet infected.\n" +
                    "Always check that the label lists corn, and use the rate on the label.\n\n" +
                    "When and How to Spray:\n" +
                    "• Start checking the lower leaves about 2 weeks before tassels appear.\n" +
                    "• The best time to spray is from tasseling to silking, if lesions are reaching the ear leaf and the weather is warm and humid. In dry weather with lesions not spreading, you can wait.\n" +
                    "• Cover the middle and lower leaves well.\n" +
                    "• If a second spray is needed, switch to a product from a different fungicide group.\n\n" +
                    SAFETY + "\n\n" +
                    NO_HOME_REMEDY,
            causes =
                "Gray Leaf Spot is caused by the fungi Cercospora zeae-maydis and Cercospora zeina. It thrives because of:\n\n" +
                    "• Continuous corn planting — the #1 cause. The fungus builds up in old corn residue on the soil surface.\n" +
                    "• Warm temperatures with high humidity and long periods of leaf wetness.\n" +
                    "• Minimum tillage — leaving old corn residue on the surface helps the fungus survive between seasons.\n" +
                    "• Susceptible corn varieties.",
            duration =
                "Treatment Duration:\n" +
                    "• Usually 1–2 sprays around tasseling. A single well-timed spray can protect the crop through grain fill.\n" +
                    "• Follow the label for the interval between sprays and the maximum number per season.\n" +
                    "• Spotted leaves will not recover, but protecting the ear leaf and the leaves above it is what saves the harvest.",
            prevention =
                "How to Prevent Gray Leaf Spot from Coming Back:\n\n" +
                    "1. Crop rotation is the most important step — grow a non-corn crop (like rice, vegetables or legumes) for at least one season.\n" +
                    "2. Manage crop residue after harvest — plow it under or compost it so less of the fungus survives.\n" +
                    "3. Plant varieties resistant to gray leaf spot — ask your DA technician or seed supplier.\n" +
                    "4. Improve field drainage and keep good spacing so leaves dry faster.\n" +
                    "5. Scout the lower leaves weekly from about 2 weeks before tasseling."
        ),
        entry(
            code = DiseaseCode.HEALTHY,
            name = "Healthy",
            symptoms = "Uniform green pigmentation, no visible lesions or discoloration.",
            treatment =
                "Your corn is healthy! No treatment required.\n\n" +
                    "Keep doing what you're doing:\n" +
                    "• Maintain your current watering schedule — water at the base of the plant, not from above.\n" +
                    "• Continue your fertilizer program as planned.\n" +
                    "• Keep the field clear of weeds and old debris.",
            causes =
                "No disease detected — your corn leaves look normal and healthy. This means:\n\n" +
                    "• Your field management practices are working well.\n" +
                    "• The growing conditions are favorable.\n" +
                    "• Your chosen corn variety is performing well in your area.",
            duration =
                "No treatment needed! Just continue monitoring your crops regularly:\n\n" +
                    "• Check your plants at least once a week.\n" +
                    "• Look at both the top and bottom sides of the leaves.\n" +
                    "• Pay extra attention during the wet season when diseases are more common.",
            prevention =
                "Tips to Keep Your Corn Healthy:\n\n" +
                    "1. Continue regular field scouting — scan a few plants every week using this app.\n" +
                    "2. Maintain proper spacing — don't overcrowd your plants.\n" +
                    "3. Practice crop rotation — even if your field is healthy now, rotating crops helps prevent future disease buildup.\n" +
                    "4. Keep field borders clean — trim grasses and weeds around your field that could harbor pests and diseases.\n" +
                    "5. Store seeds properly — use dry, clean seeds for the next planting season.",
            // Nothing to treat, so there is no treatment content to review or label.
            reviewStatus = ReviewStatus.UNREVIEWED
        )
    )

    private fun entry(
        code: String,
        name: String,
        symptoms: String,
        treatment: String,
        causes: String,
        duration: String,
        prevention: String,
        reviewStatus: ReviewStatus = ReviewStatus.SOURCES_REVIEWED
    ) = DiseaseReferenceEntity(
        diseaseCode = code,
        displayName = name,
        symptoms = symptoms,
        treatmentSteps = treatment,
        preventionSteps = prevention,
        causes = causes,
        duration = duration,
        sourceReference = SOURCE_REFERENCE,
        contentVersion = CONTENT_VERSION,
        updatedAt = System.currentTimeMillis(),
        reviewStatus = reviewStatus.storedValue,
        reviewedBy = if (reviewStatus == ReviewStatus.UNREVIEWED) "" else REVIEWED_BY,
        reviewedAt = if (reviewStatus == ReviewStatus.UNREVIEWED) 0L else REVIEWED_AT
    )
}
