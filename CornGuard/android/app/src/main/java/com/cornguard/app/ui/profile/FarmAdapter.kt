package com.cornguard.app.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.data.model.Farm
import com.cornguard.app.databinding.ItemFarmBinding

class FarmAdapter : ListAdapter<Farm, FarmAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val farm = getItem(position)
        holder.binding.farmName.text = farm.farmNameOrLabel
        holder.binding.farmLocation.text = "${farm.barangay}, ${farm.municipality}, ${farm.province}"
    }

    class ViewHolder(val binding: ItemFarmBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Farm>() {
            override fun areItemsTheSame(old: Farm, new: Farm) = old.farmId == new.farmId
            override fun areContentsTheSame(old: Farm, new: Farm) = old == new
        }
    }
}
