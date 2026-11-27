package com.cornguard.app.model

/**
 * Combines the per-leaf [DetectionResult]s of one multi-leaf scan (2+ different leaves of the
 * same plant) into a plant-level summary. See docs/MULTI_PHOTO_LEAF_CAPTURE_PLAN.md §4.2 and §4.5.
 *
 * Deliberately does NOT average probabilities across leaves: different leaves can really have
 * different conditions, and averaging a rust leaf with a healthy leaf would hide the rust. Each
 * leaf keeps its own result, and the summary reports leaf counts ("rust on 2 of 2 leaves"), never
 * a combined "boosted" confidence.
 *
 * Pure Kotlin, no Android dependencies, so it is unit-tested on the JVM.
 */
class PlantResultAggregator(private val config: PlantResultConfig) {

    /**
     * @param leaves one result per leaf photo, in capture order (index 0 = leaf 1).
     * @throws IllegalArgumentException if [leaves] is empty.
     */
    fun aggregate(leaves: List<DetectionResult>): PlantResult {
        require(leaves.isNotEmpty()) { "a scan needs at least one leaf result" }

        val leafResults = leaves.mapIndexed { index, detection ->
            LeafResult(
                position = index,
                detection = detection,
                isConfident = detection.confidence >= config.confidentThreshold
            )
        }
        val confident = leafResults.filter { it.isConfident }
        val confidentDiseased = confident.filter { it.detection.diseaseCode != DiseaseCode.HEALTHY }
        val diseasesFound = countByCode(confidentDiseased)

        val state: PlantResultState
        val unclearReason: UnclearReason?
        val mainCode: String
        when {
            confident.isEmpty() -> {
                state = PlantResultState.UNCLEAR
                unclearReason = UnclearReason.NO_CONFIDENT_LEAF
                mainCode = leafResults.maxBy { it.detection.confidence }.detection.diseaseCode
            }
            diseasesFound.size >= 2 -> {
                state = PlantResultState.MIXED_DISEASES
                unclearReason = null
                mainCode = diseasesFound.first().diseaseCode
            }
            diseasesFound.size == 1 -> {
                state = PlantResultState.DISEASE_FOUND
                unclearReason = null
                mainCode = diseasesFound.first().diseaseCode
            }
            confident.size >= config.minConfidentHealthyLeaves -> {
                state = PlantResultState.HEALTHY
                unclearReason = null
                mainCode = DiseaseCode.HEALTHY
            }
            else -> {
                state = PlantResultState.UNCLEAR
                unclearReason = UnclearReason.TOO_FEW_CONFIDENT_HEALTHY
                mainCode = DiseaseCode.HEALTHY
            }
        }

        val confidentMain = confident.filter { it.detection.diseaseCode == mainCode }
        val mainConfidence = if (confidentMain.isNotEmpty()) {
            confidentMain.map { it.detection.confidence }.average().toFloat()
        } else {
            leafResults.maxOf { it.detection.confidence }
        }
        val mainLeafPosition = confidentMain.maxByOrNull { it.detection.confidence }?.position ?: 0

        return PlantResult(
            state = state,
            unclearReason = unclearReason,
            leaves = leafResults,
            mainDiseaseCode = mainCode,
            mainConfidence = mainConfidence,
            leavesWithResult = confidentMain.size,
            mainLeafPosition = mainLeafPosition,
            diseasesFound = diseasesFound
        )
    }

    /**
     * Groups leaves by disease code, sorted by leaf count (desc), then mean confidence (desc),
     * then [DiseaseCode.ALL] order so ties are deterministic.
     */
    private fun countByCode(leaves: List<LeafResult>): List<DiseaseLeafCount> =
        leaves.groupBy { it.detection.diseaseCode }
            .map { (code, group) ->
                DiseaseLeafCount(
                    diseaseCode = code,
                    leafCount = group.size,
                    meanConfidence = group.map { it.detection.confidence }.average().toFloat()
                )
            }
            .sortedWith(
                compareByDescending<DiseaseLeafCount> { it.leafCount }
                    .thenByDescending { it.meanConfidence }
                    .thenBy { DiseaseCode.ALL.indexOf(it.diseaseCode) }
            )
}

/**
 * Development-only decision values (docs/MULTI_PHOTO_LEAF_CAPTURE_PLAN.md §4.2). There are no
 * defaults here on purpose: final confidence thresholds must not be invented in code
 * (claude/16_UI_UX_AND_DIAGRAM_REVISION_GUIDE.md). They come from capture_config.json until
 * validated values are recorded in claude/09_ML_MODEL_CONTRACT.md.
 *
 * @property confidentThreshold T_conf: a leaf counts toward the summary when its confidence is
 *   at least this value (0.0-1.0).
 * @property minConfidentHealthyLeaves how many confident healthy leaves are needed before the
 *   plant is called healthy.
 */
data class PlantResultConfig(
    val confidentThreshold: Float,
    val minConfidentHealthyLeaves: Int
) {
    init {
        require(confidentThreshold in 0f..1f) {
            "confidentThreshold must be within 0.0-1.0, was $confidentThreshold"
        }
        require(minConfidentHealthyLeaves >= 1) {
            "minConfidentHealthyLeaves must be at least 1, was $minConfidentHealthyLeaves"
        }
    }
}

enum class PlantResultState { DISEASE_FOUND, MIXED_DISEASES, HEALTHY, UNCLEAR }

/** Why a scan is [PlantResultState.UNCLEAR]; drives the retry hint (plan §4.5). */
enum class UnclearReason {
    /** No leaf reached T_conf, often damage the 4-class model wasn't trained on. */
    NO_CONFIDENT_LEAF,

    /** Confident leaves are all healthy, but too few to call the plant healthy. */
    TOO_FEW_CONFIDENT_HEALTHY
}

/**
 * @property position 0-based capture position (0 = leaf 1).
 * @property isConfident whether this leaf reached T_conf and counts toward the summary.
 */
data class LeafResult(
    val position: Int,
    val detection: DetectionResult,
    val isConfident: Boolean
)

/** One disease seen on confident leaves, e.g. common rust on 2 leaves. */
data class DiseaseLeafCount(
    val diseaseCode: String,
    val leafCount: Int,
    val meanConfidence: Float
)

/**
 * Plant-level summary of one multi-leaf scan.
 *
 * @property mainDiseaseCode stored in `diagnosis_records.diseaseCode`: the disease on the most
 *   confident leaves; `healthy` only when [state] is HEALTHY (or UNCLEAR with only healthy
 *   confident leaves). For UNCLEAR with no confident leaf, the top class of the highest-confidence
 *   leaf; callers must show it as unclear, never as a firm diagnosis.
 * @property mainConfidence mean confidence of the confident leaves showing [mainDiseaseCode], or
 *   the highest leaf confidence when no confident leaf shows it. Not a combined certainty.
 * @property leavesWithResult how many confident leaves show [mainDiseaseCode].
 * @property mainLeafPosition the leaf whose photo is stored as the record's main image.
 * @property diseasesFound diseases on confident leaves, most leaves first. Empty when no confident
 *   leaf is diseased.
 */
data class PlantResult(
    val state: PlantResultState,
    val unclearReason: UnclearReason?,
    val leaves: List<LeafResult>,
    val mainDiseaseCode: String,
    val mainConfidence: Float,
    val leavesWithResult: Int,
    val mainLeafPosition: Int,
    val diseasesFound: List<DiseaseLeafCount>
) {
    val imageCount: Int get() = leaves.size
}
