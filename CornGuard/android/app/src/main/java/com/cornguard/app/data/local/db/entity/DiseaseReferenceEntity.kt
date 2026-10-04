package com.cornguard.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local persistence for `LocalDiseaseReference` (claude/08_DATA_AND_INTEGRATION_CONTRACT.md).
 * Must remain readable offline — the model only classifies the leaf; this table is the sole
 * source of treatment/prevention guidance shown to the farmer
 * (claude/15_CLAUDE.md Treatment Rule).
 */
@Entity(tableName = "disease_references")
data class DiseaseReferenceEntity(
    @PrimaryKey
    val diseaseCode: String,

    val displayName: String,
    val symptoms: String,
    val treatmentSteps: String,
    val preventionSteps: String,

    /** Added in DB version 2 (caps 3 knowledge base); empty for content that lacks it. */
    @ColumnInfo(defaultValue = "")
    val causes: String = "",
    @ColumnInfo(defaultValue = "")
    val duration: String = "",

    /** Traceable agricultural source, per claude/04_DEVELOPMENT_RULES.md #12. */
    val sourceReference: String,
    val contentVersion: String,

    /** Epoch millis, UTC. */
    val updatedAt: Long
)
