package com.cornguard.app.ui.notifications

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.R
import com.cornguard.app.data.model.AppNotification
import com.cornguard.app.databinding.ItemNotificationBinding
import com.cornguard.app.ui.common.TimeFormat

/** Inbox rows: outbreak alerts get the warning icon in red, community replies the chat icon in green. */
class NotificationsAdapter(
    private val onClick: (AppNotification) -> Unit
) : ListAdapter<AppNotification, NotificationsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), onClick)

    class ViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppNotification, onClick: (AppNotification) -> Unit) {
            val context = binding.root.context
            val isOutbreak = item.type == AppNotification.TYPE_OUTBREAK_ALERT
            val iconColor = ContextCompat.getColor(context, if (isOutbreak) R.color.cg_danger else R.color.cg_primary)
            binding.notificationIcon.setImageResource(if (isOutbreak) R.drawable.ic_warning else R.drawable.ic_chat)
            binding.notificationIcon.imageTintList = ColorStateList.valueOf(iconColor)
            binding.notificationIcon.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(iconColor, 0x20))

            binding.notificationTitle.text = item.title
            binding.notificationTitle.setTypeface(null, if (item.read) Typeface.NORMAL else Typeface.BOLD)
            binding.notificationMessage.text = item.message
            binding.notificationTime.text = TimeFormat.relative(context, item.createdAt)
            binding.notificationUnreadDot.visibility = if (item.read) View.GONE else View.VISIBLE
            binding.root.setBackgroundColor(
                if (item.read) android.graphics.Color.TRANSPARENT
                else ContextCompat.getColor(context, R.color.cg_green_tint)
            )

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppNotification>() {
            override fun areItemsTheSame(old: AppNotification, new: AppNotification) =
                old.notificationId == new.notificationId

            override fun areContentsTheSame(old: AppNotification, new: AppNotification) = old == new
        }
    }
}
