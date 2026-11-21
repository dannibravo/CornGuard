package com.cornguard.app.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.data.model.MapOccurrence
import com.cornguard.app.databinding.ItemGisEntryBinding
import java.text.DateFormat
import java.util.Date

class MapOccurrenceAdapter : ListAdapter<MapOccurrence, MapOccurrenceAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGisEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.gisEntryDisease.text = item.diseaseCode
        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.occurredAt))
        holder.binding.gisEntryMeta.text = "${item.barangay} • $dateText"
    }

    class ViewHolder(val binding: ItemGisEntryBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MapOccurrence>() {
            override fun areItemsTheSame(old: MapOccurrence, new: MapOccurrence) = old.occurrenceId == new.occurrenceId
            override fun areContentsTheSame(old: MapOccurrence, new: MapOccurrence) = old == new
        }
    }
}
