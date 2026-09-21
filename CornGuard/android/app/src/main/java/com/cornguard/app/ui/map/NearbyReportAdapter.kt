package com.cornguard.app.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.data.model.NearbyReportSummary
import com.cornguard.app.databinding.ItemGisEntryBinding
import java.text.DateFormat
import java.util.Date

class NearbyReportAdapter : ListAdapter<NearbyReportSummary, NearbyReportAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGisEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.gisEntryDisease.text = item.diseaseTag
        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.createdAt))
        holder.binding.gisEntryMeta.text = "${item.barangay} • $dateText"
    }

    class ViewHolder(val binding: ItemGisEntryBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NearbyReportSummary>() {
            override fun areItemsTheSame(old: NearbyReportSummary, new: NearbyReportSummary) = old.postId == new.postId
            override fun areContentsTheSame(old: NearbyReportSummary, new: NearbyReportSummary) = old == new
        }
    }
}
