package com.cornguard.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.databinding.ItemBarangayBinding
import com.cornguard.app.databinding.SheetBarangayPickerBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.location.BarangayResolver
import com.cornguard.app.ui.common.CornGuardBottomSheet

/**
 * "Set location manually" (caps 3's location picker): searchable list of the 464 Bukidnon
 * barangays from the bundled boundaries. Reports the pick as a fragment result.
 */
class BarangayPickerBottomSheet : CornGuardBottomSheet() {

    private var _binding: SheetBarangayPickerBinding? = null
    private val binding get() = _binding!!

    private val all: List<BarangayResolver.Barangay> by lazy { ServiceLocator.barangayResolver.sorted() }
    private val adapter = Adapter { pick(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetBarangayPickerBinding.inflate(inflater, container, false)
        binding.root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.7).toInt()
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pickerClose.setOnClickListener { dismiss() }
        binding.pickerList.layoutManager = LinearLayoutManager(requireContext())
        binding.pickerList.adapter = adapter
        binding.pickerSearch.doAfterTextChanged { filter(it?.toString().orEmpty()) }
        filter("")
    }

    private fun filter(query: String) {
        val q = query.trim()
        val items = if (q.isEmpty()) all else all.filter {
            it.name.contains(q, ignoreCase = true) || it.municipality.contains(q, ignoreCase = true)
        }
        adapter.items = items
        binding.pickerEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pick(barangay: BarangayResolver.Barangay) {
        setFragmentResult(RESULT, bundleOf(KEY_BARANGAY to barangay.name, KEY_MUNICIPALITY to barangay.municipality))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class Adapter(private val onPick: (BarangayResolver.Barangay) -> Unit) :
        RecyclerView.Adapter<Adapter.ViewHolder>() {

        var items: List<BarangayResolver.Barangay> = emptyList()
            set(value) {
                field = value
                @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ItemBarangayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.barangayName.text = item.name
            holder.binding.barangayMunicipality.text = item.municipality
            holder.binding.root.setOnClickListener { onPick(item) }
        }

        class ViewHolder(val binding: ItemBarangayBinding) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val TAG = "BarangayPickerBottomSheet"
        const val RESULT = "barangay_picked"
        const val KEY_BARANGAY = "barangay"
        const val KEY_MUNICIPALITY = "municipality"
    }
}
