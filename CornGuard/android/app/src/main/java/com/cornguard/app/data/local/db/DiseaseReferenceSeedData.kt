package com.cornguard.app.data.local.db

import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import com.cornguard.app.model.DiseaseCode

/**
 * Offline disease reference content, upserted at every startup so updated content reaches devices
 * that already have an older copy. Treatment / causes / duration / prevention text is carried over
 * verbatim from the caps 3 project's `DISEASE_INFO` knowledge base (`src/services/ml-service.ts`,
 * where the model's "Blight" class = [DiseaseCode.NORTHERN_LEAF_BLIGHT]).
 *
 * Per claude/09_ML_MODEL_CONTRACT.md's Treatment Guidance Boundary this content has not yet been
 * verified by an agricultural expert — TreatmentFragment's disclaimer banner says so on-screen.
 */
object DiseaseReferenceSeedData {

    private const val CONTENT_VERSION = "caps3-v1"
    private const val SOURCE_REFERENCE =
        "CornGuard disease knowledge base (Bukidnon field guidance) — confirm with your local DA office or agricultural technician"

    val all: List<DiseaseReferenceEntity> = listOf(
        entry(
            code = DiseaseCode.NORTHERN_LEAF_BLIGHT,
            name = "Northern Leaf Blight",
            symptoms = "Elongated, elliptical \"cigar-shaped\" lesions, several centimeters long.",
            treatment =
                "Chemical Option:\n" +
                    "Use fungicides with Mancozeb (commonly sold as 'Dithane M-45', ₱120–₱180 per pack) or Pyraclostrobin (like 'Cabrio', available at agri-vet stores in Valencia or Malaybalay, Bukidnon).\n\n" +
                    "How to Apply:\n" +
                    "• Mix 2–3 tablespoons of Dithane M-45 per 16 liters of water in a knapsack sprayer.\n" +
                    "• Spray evenly on all leaves, especially the lower ones where the blight starts.\n" +
                    "• Spray early in the morning or late afternoon when the sun is not too strong.\n" +
                    "• Repeat every 7–10 days until symptoms stop spreading.\n\n" +
                    "Organic/Homemade Alternative:\n" +
                    "Mix 1 tablespoon of baking soda (sodium bicarbonate) and a few drops of mild dish soap (like Joy) into 1 gallon of water. Spray this on the leaves early in the morning to create an alkaline surface that prevents the fungus from spreading. Reapply after every rain.",
            causes =
                "Corn blight is caused by the fungus Exserohilum turcicum (Northern Corn Leaf Blight) or Bipolaris maydis (Southern Corn Leaf Blight). It thrives in:\n\n" +
                    "• Warm, humid weather — very common during the wet season in Bukidnon.\n" +
                    "• Overcrowded planting — when corn is planted too close, moisture gets trapped between the leaves.\n" +
                    "• Infected debris left on the field — if old corn stalks and leaves from the previous season were not cleaned up, the fungus can survive and re-infect the new crop.\n" +
                    "• Poor drainage — standing water around the roots creates the perfect environment for the fungus to grow.",
            duration =
                "Treatment Duration:\n" +
                    "• With chemical fungicide: Spray every 7–10 days for 3–4 weeks. You should see the spread stop within the first 2 weeks.\n" +
                    "• With homemade baking soda spray: Apply every 5–7 days for at least 4–5 weeks. This is slower but still effective for mild cases.\n" +
                    "• Severely damaged leaves will NOT recover — but the goal is to protect the healthy leaves and the ear of corn so the harvest is not lost.",
            prevention =
                "How to Prevent Blight from Coming Back:\n\n" +
                    "1. Clean your field after harvest — remove and burn all corn stalks, leaves, and husks. Do NOT leave them to rot on the field.\n" +
                    "2. Practice crop rotation — plant a different crop (like monggo, camote, or peanuts) in the same field next season before planting corn again.\n" +
                    "3. Use resistant corn varieties — ask your local DA technician in Bukidnon for Bt corn or blight-resistant OPV (Open Pollinated Variety) seeds.\n" +
                    "4. Don't plant too close — follow the recommended spacing of 75cm between rows and 25cm between plants.\n" +
                    "5. Avoid overhead watering — water at the base of the plant to keep the leaves dry."
        ),
        entry(
            code = DiseaseCode.COMMON_RUST,
            name = "Common Rust",
            symptoms = "Small, reddish-brown pustules scattered across both leaf surfaces.",
            treatment =
                "Chemical Option:\n" +
                    "Apply foliar fungicides like 'Amistar 25 SC' (Azoxystrobin, ₱350–₱500 per 100ml) or products containing Propiconazole (like 'Tilt 250 EC'). These are available at agri-vet supply stores in Bukidnon.\n\n" +
                    "How to Apply:\n" +
                    "• Mix 1.5–2ml of Amistar per liter of water in a knapsack sprayer.\n" +
                    "• Spray on all leaves, focusing on the underside where the rust pustules (orange-brown bumps) appear.\n" +
                    "• Apply early in the morning before the dew dries up.\n" +
                    "• Repeat every 10–14 days.\n\n" +
                    "Organic/Homemade Alternative:\n" +
                    "Create a garlic extract spray: Crush 1 whole bulb of garlic, soak it in 2 cups of water overnight, strain it, then mix the liquid with 1 gallon of water and a few drops of dish soap. Spray directly onto the rust pustules on the leaves. Garlic has natural antifungal properties.",
            causes =
                "Common Rust is caused by the fungus Puccinia sorghi. It spreads through:\n\n" +
                    "• Wind-blown spores — the orange-brown spores can travel long distances in the wind, meaning even a clean field can get infected from a neighbor's farm.\n" +
                    "• Cool, moist conditions — temperatures between 15–25°C with high humidity. This is common in the higher elevation areas of Bukidnon.\n" +
                    "• Planting the same corn variety repeatedly — some varieties are more susceptible than others.\n" +
                    "• Late planting — corn planted late in the season is more vulnerable because it coincides with cooler, wetter conditions.",
            duration =
                "Treatment Duration:\n" +
                    "• With chemical fungicide: Spray every 10–14 days for 2–3 applications. Improvement is usually visible within 1–2 weeks as the rust stops spreading to new leaves.\n" +
                    "• With garlic spray: Apply every 5–7 days for 4–6 weeks. Best used for mild infections only.\n" +
                    "• Already-rusted leaves will keep their brown spots, but new leaves should grow clean if treatment is consistent.",
            prevention =
                "How to Prevent Common Rust from Coming Back:\n\n" +
                    "1. Choose rust-resistant corn hybrids — ask your agri-vet store for varieties labeled 'rust-resistant.' Many Bt corn varieties have some resistance built in.\n" +
                    "2. Plant early in the season — avoid late planting that puts your corn's vulnerable growth stages during the cool, wet months.\n" +
                    "3. Keep good spacing — proper airflow between plants helps dry the leaves faster, making it harder for spores to germinate.\n" +
                    "4. Monitor weekly — check the undersides of lower leaves regularly. Catching rust early (when there are only a few spots) makes treatment far more effective.\n" +
                    "5. Remove volunteer corn plants — wild or leftover corn plants near your field can harbor the fungus between seasons."
        ),
        entry(
            code = DiseaseCode.GRAY_LEAF_SPOT,
            name = "Gray Leaf Spot",
            symptoms = "Rectangular, tan-to-gray lesions bounded by leaf veins, usually starting in the lower canopy.",
            treatment =
                "Chemical Option:\n" +
                    "Fungicides like 'Cabrio' (Pyraclostrobin, ₱600–₱800 per 200ml) or 'Fungitox' (Thiophanate Methyl) are effective when applied at the tasseling stage.\n\n" +
                    "How to Apply:\n" +
                    "• Mix according to the label instructions (usually 1–2ml per liter of water).\n" +
                    "• Spray on all leaves, particularly the middle and lower canopy where gray spots typically start.\n" +
                    "• Best applied at the V8 to VT (tasseling) growth stage for maximum protection.\n" +
                    "• Repeat every 14 days if disease pressure is high.\n\n" +
                    "Organic/Homemade Alternative:\n" +
                    "Neem oil spray works well for mild cases. Mix 1 tablespoon of Neem oil (available at organic farming supply stores, around ₱150–₱250 per bottle) with 1 liter of water and a few drops of liquid soap. Spray every 7 days.",
            causes =
                "Gray Leaf Spot is caused by the fungus Cercospora zeae-maydis. It thrives because of:\n\n" +
                    "• Continuous corn planting — this is the #1 cause. If you plant corn after corn in the same field every season, the fungus builds up in the old debris on the soil.\n" +
                    "• High humidity and warm temperatures — prolonged leaf wetness (morning dew that lasts for many hours) helps the fungus infect the leaves.\n" +
                    "• Conservation tillage (minimum tillage) — while good for soil health, leaving old corn residue on the surface allows the fungus to survive between seasons.\n" +
                    "• Susceptible corn variety — some cheaper or older corn varieties have very little resistance to GLS.",
            duration =
                "Treatment Duration:\n" +
                    "• With chemical fungicide: Apply 1–2 times during the tasseling stage. A single well-timed application can protect the crop through grain fill.\n" +
                    "• With neem oil: Apply every 7 days for 4–5 weeks, starting when you first notice the rectangular gray-brown spots.\n" +
                    "• Severely spotted leaves will not recover, but protecting the upper leaves (especially the ear leaf) is critical for a good harvest.",
            prevention =
                "How to Prevent Gray Leaf Spot from Coming Back:\n\n" +
                    "1. Crop rotation is the most important step — alternate corn with a non-host crop like rice, vegetables, or legumes for at least one season.\n" +
                    "2. Tillage after harvest — plow under old corn debris to help it decompose faster and reduce the amount of fungal spores that survive.\n" +
                    "3. Plant GLS-resistant hybrids — newer corn varieties have better resistance. Ask your DA (Department of Agriculture) office in Bukidnon for recommendations.\n" +
                    "4. Improve field drainage — avoid waterlogged fields as constant moisture promotes fungal growth.\n" +
                    "5. Avoid overfertilizing with nitrogen — excessive nitrogen makes the leaves lush and more attractive to the fungus."
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
                    "5. Store seeds properly — use dry, clean seeds for the next planting season."
        )
    )

    private fun entry(
        code: String,
        name: String,
        symptoms: String,
        treatment: String,
        causes: String,
        duration: String,
        prevention: String
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
        updatedAt = System.currentTimeMillis()
    )
}
