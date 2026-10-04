package com.cornguard.app.ui.history

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cornguard.app.R
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.databinding.ItemDiagnosisRecordBinding
import com.cornguard.app.ui.common.DiseaseStyle
import com.cornguard.app.ui.common.TimeFormat
import java.io.File

/** History cards (caps 3 design). [treatmentByCode] supplies the "Recommended action" preview. */
class DiagnosisHistoryAdapter(
    private val onClick: (DiagnosisRecordEntity) -> Unit,
    private val onDelete: (DiagnosisRecordEntity) -> Unit
) : ListAdapter<DiagnosisRecordEntity, DiagnosisHistoryAdapter.ViewHolder>(DIFF) {

    var treatmentByCode: Map<String, String> = emptyMap()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiagnosisRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        holder.bind(record, treatmentByCode[record.diseaseCode].orEmpty(), onClick, onDelete)
    }

    class ViewHolder(private val binding: ItemDiagnosisRecordBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.recordThumbnail.clipToOutline = true
        }

        fun bind(
            record: DiagnosisRecordEntity,
            treatment: String,
            onClick: (DiagnosisRecordEntity) -> Unit,
            onDelete: (DiagnosisRecordEntity) -> Unit
        ) {
            val context = binding.root.context
            DiseaseStyle.applyBadge(binding.recordDiseaseBadge, record.diseaseCode, record.displayLabel)

            val chipColor = ContextCompat.getColor(
                context,
                if (record.sharedToCloud) R.color.cg_primary else R.color.cg_text_secondary
            )
            binding.recordSyncBadge.setText(
                if (record.sharedToCloud) R.string.history_badge_shared else R.string.history_badge_local
            )
            binding.recordSyncBadge.setTextColor(chipColor)
            binding.recordSyncBadge.setBackgroundResource(R.drawable.bg_pill)
            binding.recordSyncBadge.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(chipColor, 0x20))

            binding.recordDate.text = TimeFormat.dateTime(record.capturedAt)
            binding.recordConfidence.text =
                context.getString(R.string.history_confidence_format, TimeFormat.percent(record.confidence))
            binding.recordTreatmentPreview.text = treatment
            binding.recordThumbnail.load(File(record.imageUriOrLocalPath)) { crossfade(true) }

            binding.root.setOnClickListener { onClick(record) }
            binding.recordDeleteButton.setOnClickListener { onDelete(record) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DiagnosisRecordEntity>() {
            override fun areItemsTheSame(old: DiagnosisRecordEntity, new: DiagnosisRecordEntity) =
                old.localId == new.localId

            override fun areContentsTheSame(old: DiagnosisRecordEntity, new: DiagnosisRecordEntity) =
                old == new
        }
    }
}
