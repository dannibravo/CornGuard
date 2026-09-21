package com.cornguard.app.model

/**
 * Cross-module detection payload produced by [CornLeafClassifier] and consumed by the
 * scan/result/history/community-draft flows. Field set matches the `DetectionResult` contract in
 * claude/08_DATA_AND_INTEGRATION_CONTRACT.md and claude/09_ML_MODEL_CONTRACT.md — do not rename
 * or drop a field without updating that contract and notifying the team
 * (claude/04_DEVELOPMENT_RULES.md #3).
 *
 * @param classIndex raw model output index, exactly as returned by the TFLite interpreter.
 * @param diseaseCode one of [DiseaseCode], resolved from [classIndex] using Acenas's published
 *   class-order mapping — never inferred alphabetically or positionally by this module.
 * @param displayLabel human-readable label for UI display.
 * @param confidence the winning class's probability, in the 0.0–1.0 range (not 0–100).
 * @param probabilities all four class probabilities, in model output order, length 4.
 * @param modelVersion the `.tflite` release version used for this inference; stored with the
 *   diagnosis record per claude/04_DEVELOPMENT_RULES.md #6.
 * @param inferenceTimeMs raw TFLite inference time, logged separately from end-to-end result time
 *   per the D-06 measurement rule.
 */
data class DetectionResult(
    val classIndex: Int,
    val diseaseCode: String,
    val displayLabel: String,
    val confidence: Float,
    val probabilities: FloatArray,
    val modelVersion: String,
    val inferenceTimeMs: Long
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in 0.0..1.0, was $confidence" }
        require(probabilities.size == DiseaseCode.ALL.size) {
            "probabilities must have ${DiseaseCode.ALL.size} entries, had ${probabilities.size}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectionResult) return false
        return classIndex == other.classIndex &&
            diseaseCode == other.diseaseCode &&
            displayLabel == other.displayLabel &&
            confidence == other.confidence &&
            probabilities.contentEquals(other.probabilities) &&
            modelVersion == other.modelVersion &&
            inferenceTimeMs == other.inferenceTimeMs
    }

    override fun hashCode(): Int {
        var result = classIndex
        result = 31 * result + diseaseCode.hashCode()
        result = 31 * result + displayLabel.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + probabilities.contentHashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + inferenceTimeMs.hashCode()
        return result
    }
}
