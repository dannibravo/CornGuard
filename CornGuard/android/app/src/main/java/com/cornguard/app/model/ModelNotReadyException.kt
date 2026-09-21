package com.cornguard.app.model

/**
 * Thrown when a scan is attempted before a real [CornLeafClassifier] is wired in. Distinguished
 * from a generic model-load failure so the UI can show a specific "not available yet" state
 * instead of misreporting a device/runtime problem (claude/15_CLAUDE.md Error Handling rule:
 * every scan feature must handle model load failure).
 */
class ModelNotReadyException(message: String) : IllegalStateException(message)
