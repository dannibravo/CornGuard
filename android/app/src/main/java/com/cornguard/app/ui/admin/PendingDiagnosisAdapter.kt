package com.cornguard.app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.R
import com.cornguard.app.data.model.DiagnosisRecordCloud
import com.cornguard.app.databinding.ItemPendingDiagnosisBinding
import com.cornguard.app.model.diseaseCodeToLabel
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Pending (verification_status == "unverified") diagnosisRecordsCloud rows for Admin Review.
 * Farm linkage is shown as present/absent only — resolving farm_id to a display name would need
 * a new admin-scoped farm lookup, out of this phase's stated scope (verify/reject + audit trail).
 */
class PendingDiagnosisAdapter(
    private val onVerify: (DiagnosisRecordCloud) -> Unit,
    private val onReject: (DiagnosisRecordCloud) -> Unit
) : ListAdapter<DiagnosisRecordCloud, PendingDiagnosisAdapter.ViewHolder>(DIFF) {

    /** recordId -> true while a verify/reject call for that row is in flight. */
    private val pendingActions = mutableSetOf<String>()
    /** recordId -> last action's failure, cleared on the next successful bind after a retry. */
    private val failedActions = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPendingDiagnosisBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        holder.bind(
            record = record,
            isInFlight = pendingActions.contains(record.recordId),
            didFail = failedActions.contains(record.recordId),
            onVerify = { markInFlight(record.recordId); onVerify(record) },
            onReject = { markInFlight(record.recordId); onReject(record) }
        )
    }

    private fun markInFlight(recordId: String) {
        pendingActions.add(recordId)
        failedActions.remove(recordId)
        notifyRowChanged(recordId)
    }

    /** Called by the fragment once the verify/reject call resolves — true removes the row, false marks it failed. */
    fun onActionResolved(recordId: String, succeeded: Boolean) {
        pendingActions.remove(recordId)
        if (succeeded) {
            failedActions.remove(recordId)
            submitList(currentList.filterNot { it.recordId == recordId })
        } else {
            failedActions.add(recordId)
            notifyRowChanged(recordId)
        }
    }

    private fun notifyRowChanged(recordId: String) {
        val index = currentList.indexOfFirst { it.recordId == recordId }
        if (index >= 0) notifyItemChanged(index)
    }

    class ViewHolder(private val binding: ItemPendingDiagnosisBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            record: DiagnosisRecordCloud,
            isInFlight: Boolean,
            didFail: Boolean,
            onVerify: () -> Unit,
            onReject: () -> Unit
        ) {
            val context = binding.root.context
            binding.pendingDiseaseLabel.text = diseaseCodeToLabel(record.diseaseCode)

            val confidencePct = (record.confidence * 100).roundToInt()
            val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(record.capturedAt))
            val farmText = if (record.farmId != null) {
                context.getString(R.string.admin_review_farm_linked)
            } else {
                context.getString(R.string.admin_review_no_farm)
            }
            val imageText = if (record.hasImage) {
                context.getString(R.string.admin_review_has_image)
            } else {
                context.getString(R.string.admin_review_no_image)
            }
            binding.pendingMeta.text = context.getString(
                R.string.admin_review_meta_format,
                confidencePct,
                dateText,
                farmText,
                imageText
            )

            binding.pendingVerifyButton.isEnabled = !isInFlight
            binding.pendingRejectButton.isEnabled = !isInFlight
            binding.pendingProgress.visibility = if (isInFlight) View.VISIBLE else View.GONE
            binding.pendingActionError.visibility = if (didFail) View.VISIBLE else View.GONE
            if (didFail) {
                binding.pendingActionError.text = context.getString(R.string.admin_review_action_failed)
            }

            binding.pendingVerifyButton.setOnClickListener { onVerify() }
            binding.pendingRejectButton.setOnClickListener { onReject() }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DiagnosisRecordCloud>() {
            override fun areItemsTheSame(old: DiagnosisRecordCloud, new: DiagnosisRecordCloud) =
                old.recordId == new.recordId
            override fun areContentsTheSame(old: DiagnosisRecordCloud, new: DiagnosisRecordCloud) =
                old == new
        }
    }
}
