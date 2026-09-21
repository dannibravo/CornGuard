package com.cornguard.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.databinding.ItemDiagnosisRecordBinding
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class DiagnosisHistoryAdapter(
    private val onClick: (DiagnosisRecordEntity) -> Unit
) : ListAdapter<DiagnosisRecordEntity, DiagnosisHistoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiagnosisRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(private val binding: ItemDiagnosisRecordBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: DiagnosisRecordEntity, onClick: (DiagnosisRecordEntity) -> Unit) {
            binding.recordDiseaseLabel.text = record.displayLabel
            val confidencePct = (record.confidence * 100).roundToInt()
            val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(record.capturedAt))
            binding.recordMeta.text = "$confidencePct% confidence • $dateText"
            binding.recordSyncBadge.text = if (record.sharedToCloud) "Shared" else "Local only"
            binding.root.setOnClickListener { onClick(record) }
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
