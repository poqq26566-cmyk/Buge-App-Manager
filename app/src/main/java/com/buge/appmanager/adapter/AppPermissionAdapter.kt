package com.buge.appmanager.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip

data class AppPermissionItem(
    val app: AppInfo,
    val permissionMap: Map<String, Boolean>,
    val primaryPermission: String,
    var isSelected: Boolean = false
)

class AppPermissionAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<AppPermissionItem, AppPermissionAdapter.ViewHolder>(DiffCallback()) {

    private var selectionMode = false

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode != enabled) {
            selectionMode = enabled
            notifyItemRangeChanged(0, itemCount, "selection_mode")
        }
    }

    fun isInSelectionMode(): Boolean = selectionMode

    fun getSelectedItems(): List<AppPermissionItem> = currentList.filter { it.isSelected }

    fun selectAll() {
        currentList.forEachIndexed { index, item ->
            if (!item.isSelected) {
                item.isSelected = true
                notifyItemChanged(index, "selection")
            }
        }
        onSelectionChanged(currentList.size)
    }

    fun clearSelection() {
        currentList.forEachIndexed { index, item ->
            if (item.isSelected) {
                item.isSelected = false
                notifyItemChanged(index, "selection")
            }
        }
        onSelectionChanged(0)
    }

    companion object {
        private val PERMISSION_GROUP_MAP = mapOf(
            "android.permission.RECORD_AUDIO" to "android.permission-group.MICROPHONE",
            "android.permission.CAMERA" to "android.permission-group.CAMERA",
            "android.permission.ACCESS_FINE_LOCATION" to "android.permission-group.LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION" to "android.permission-group.LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION" to "android.permission-group.LOCATION",
            "android.permission.READ_CONTACTS" to "android.permission-group.CONTACTS",
            "android.permission.WRITE_CONTACTS" to "android.permission-group.CONTACTS",
            "android.permission.GET_ACCOUNTS" to "android.permission-group.CONTACTS",
            "android.permission.READ_EXTERNAL_STORAGE" to "android.permission-group.STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" to "android.permission-group.STORAGE",
            "android.permission.READ_MEDIA_IMAGES" to "android.permission-group.READ_MEDIA_VISUAL",
            "android.permission.READ_MEDIA_VIDEO" to "android.permission-group.READ_MEDIA_VISUAL",
            "android.permission.READ_MEDIA_AUDIO" to "android.permission-group.READ_MEDIA_AURAL",
            "android.permission.READ_PHONE_STATE" to "android.permission-group.PHONE",
            "android.permission.CALL_PHONE" to "android.permission-group.PHONE",
            "android.permission.READ_CALL_LOG" to "android.permission-group.CALL_LOG",
            "android.permission.WRITE_CALL_LOG" to "android.permission-group.CALL_LOG",
            "android.permission.ADD_VOICEMAIL" to "android.permission-group.PHONE",
            "android.permission.USE_SIP" to "android.permission-group.PHONE",
            "android.permission.PROCESS_OUTGOING_CALLS" to "android.permission-group.PHONE",
            "android.permission.SEND_SMS" to "android.permission-group.SMS",
            "android.permission.RECEIVE_SMS" to "android.permission-group.SMS",
            "android.permission.READ_SMS" to "android.permission-group.SMS",
            "android.permission.RECEIVE_WAP_PUSH" to "android.permission-group.SMS",
            "android.permission.RECEIVE_MMS" to "android.permission-group.SMS",
            "android.permission.READ_CALENDAR" to "android.permission-group.CALENDAR",
            "android.permission.WRITE_CALENDAR" to "android.permission-group.CALENDAR",
            "android.permission.BODY_SENSORS" to "android.permission-group.SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND" to "android.permission-group.SENSORS",
            "android.permission.ACTIVITY_RECOGNITION" to "android.permission-group.ACTIVITY_RECOGNITION",
            "android.permission.BLUETOOTH_SCAN" to "android.permission-group.NEARBY_DEVICES",
            "android.permission.BLUETOOTH_CONNECT" to "android.permission-group.NEARBY_DEVICES",
            "android.permission.BLUETOOTH_ADVERTISE" to "android.permission-group.NEARBY_DEVICES",
            "android.permission.UWB_RANGING" to "android.permission-group.NEARBY_DEVICES",
            "android.permission.POST_NOTIFICATIONS" to "android.permission-group.NOTIFICATIONS"
        )

        private fun getSpecialPermissionIntent(context: Context, packageName: String, permission: String): Intent? {
            return when (permission) {
                "android.permission.SYSTEM_ALERT_WINDOW" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                "android.permission.REQUEST_INSTALL_PACKAGES" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                "android.permission.MANAGE_EXTERNAL_STORAGE" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                "android.permission.WRITE_SETTINGS" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                else -> null
            }
        }

        fun openPermissionSettings(context: Context, packageName: String, permission: String) {
            val candidates = mutableListOf<Intent>()

            getSpecialPermissionIntent(context, packageName, permission)?.let {
                candidates.add(it)
            }

            val groupName = PERMISSION_GROUP_MAP[permission]
            if (groupName != null) {
                candidates.add(
                    Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
                        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                        putExtra("android.intent.extra.PERMISSION_GROUP_NAME", groupName)
                        putExtra("android.intent.extra.USER", Process.myUserHandle())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }

            // 直接用包名跳应用详情（兜底1）
            candidates.add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )

            // MIUI 安全中心权限管理
            candidates.add(
                Intent("android.settings.APP_PERMISSION_SETTINGS").apply {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )

            // 另一个备选
            candidates.add(
                Intent("android.settings.APPLICATION_DETAILS_SETTINGS").apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )

            for (intent in candidates) {
                try {
                    context.startActivity(intent)
                    return
                } catch (_: Exception) { }
            }

            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_permission, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))

        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
        val size = currentList.size
        val background = when {
            size == 1 -> R.drawable.bg_setting_item_single
            position == 0 -> R.drawable.bg_setting_item_top
            position == size - 1 -> R.drawable.bg_setting_item_bottom
            else -> R.drawable.bg_setting_item_middle
        }
        container.setBackgroundResource(background)

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                val item = getItem(holder.adapterPosition)
                item.isSelected = true
                notifyItemRangeChanged(0, itemCount, "selection_mode")
                val count = currentList.count { it.isSelected }
                onSelectionChanged(count)
                holder.animateCardSelection(true)
            }
            true
        }

        holder.itemView.setOnClickListener {
            holder.animateClick()
            val item = getItem(holder.adapterPosition)
            if (selectionMode) {
                val newSelected = !item.isSelected
                item.isSelected = newSelected
                holder.checkbox.isChecked = newSelected
                holder.animateCheckbox(newSelected)
                val count = currentList.count { it.isSelected }
                onSelectionChanged(count)
                notifyItemChanged(holder.adapterPosition, "selection")
            } else {
                openPermissionSettings(
                    holder.itemView.context,
                    item.app.packageName,
                    item.primaryPermission
                )
            }
        }

        holder.checkbox.setOnClickListener {
            holder.animateCheckboxClick()
            val item = getItem(holder.adapterPosition)
            val newSelected = holder.checkbox.isChecked
            item.isSelected = newSelected
            holder.animateCheckbox(newSelected)
            val count = currentList.count { it.isSelected }
            onSelectionChanged(count)
            notifyItemChanged(holder.adapterPosition, "selection")
        }

        holder.permStatusChip.setOnClickListener {
            val item = getItem(holder.adapterPosition)
            openPermissionSettings(
                holder.itemView.context,
                item.app.packageName,
                item.primaryPermission
            )
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)
        when {
            payloads.contains("selection_mode") -> holder.updateSelectionMode(selectionMode, item.isSelected)
            payloads.contains("selection") -> holder.updateSelection(item.isSelected)
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: MaterialCheckBox = itemView.findViewById(R.id.checkbox)
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        val permStatusChip: Chip = itemView.findViewById(R.id.perm_status_chip)
        private val systemAppBadge: View = itemView.findViewById(R.id.system_app_badge)
        private val cardView: View = itemView
        private val textContainer: ViewGroup = appName.parent as ViewGroup

        fun bind(item: AppPermissionItem) {
            appIcon.setImageDrawable(item.app.icon)
            appName.text = item.app.appName
            packageName.text = item.app.packageName
            systemAppBadge.visibility = if (item.app.isSystemApp) View.VISIBLE else View.GONE

            if (selectionMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.alpha = 1f
                checkbox.scaleX = 1f
                checkbox.scaleY = 1f
                appIcon.translationX = 28f
                textContainer.translationX = 28f
                packageName.translationX = 28f
            } else {
                checkbox.visibility = View.GONE
                appIcon.translationX = 0f
                textContainer.translationX = 0f
                packageName.translationX = 0f
            }
            checkbox.isChecked = item.isSelected

            val isGranted = item.permissionMap[item.primaryPermission] ?: false
            if (isGranted) {
                permStatusChip.text = itemView.context.getString(R.string.granted)
                permStatusChip.setChipBackgroundColorResource(R.color.color_granted_container)
                permStatusChip.setTextColor(itemView.context.getColor(R.color.color_granted))
                permStatusChip.chipIcon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_check_small)
                permStatusChip.chipIconTint = ContextCompat.getColorStateList(itemView.context, R.color.color_granted)
                permStatusChip.chipIconSize = 40f
                permStatusChip.chipStrokeWidth = 0f
            } else {
                permStatusChip.text = itemView.context.getString(R.string.denied)
                permStatusChip.setChipBackgroundColorResource(R.color.color_denied_container)
                permStatusChip.setTextColor(itemView.context.getColor(R.color.color_denied))
                permStatusChip.chipIcon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_close_small)
                permStatusChip.chipIconTint = ContextCompat.getColorStateList(itemView.context, R.color.color_denied)
                permStatusChip.chipIconSize = 40f
                permStatusChip.chipStrokeWidth = 0f
            }
            permStatusChip.chipCornerRadius = 32f
        }

        fun updateSelectionMode(mode: Boolean, isSelected: Boolean) {
            if (mode) {
                checkbox.visibility = View.VISIBLE
                checkbox.alpha = 0f
                checkbox.scaleX = 0.5f
                checkbox.scaleY = 0.5f
                appIcon.animate().translationX(28f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
                textContainer.animate().translationX(28f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
                packageName.animate().translationX(28f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
                checkbox.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(OvershootInterpolator()).start()
            } else {
                appIcon.animate().translationX(0f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
                textContainer.animate().translationX(0f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
                packageName.animate().translationX(0f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
                checkbox.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(150).withEndAction {
                    checkbox.visibility = View.GONE
                }.start()
            }
            checkbox.isChecked = isSelected
        }

        fun updateSelection(isSelected: Boolean) {
            checkbox.isChecked = isSelected
            animateCheckbox(isSelected)
        }

        fun animateCardSelection(selected: Boolean) {
            if (selected) {
                cardView.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction { cardView.animate().scaleX(1f).scaleY(1f).setDuration(150).start() }
                    .start()
            }
        }

        fun animateClick() {
            cardView.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { cardView.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                .start()
        }

        fun animateCheckbox(checked: Boolean) {
            if (checked) {
                checkbox.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction { checkbox.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }
                    .start()
            }
        }

        fun animateCheckboxClick() {
            checkbox.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80)
                .setInterpolator(OvershootInterpolator())
                .withEndAction { checkbox.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                .start()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppPermissionItem>() {
        override fun areItemsTheSame(oldItem: AppPermissionItem, newItem: AppPermissionItem) =
            oldItem.app.packageName == newItem.app.packageName

        override fun areContentsTheSame(oldItem: AppPermissionItem, newItem: AppPermissionItem) =
            oldItem.permissionMap.size == newItem.permissionMap.size &&
            oldItem.permissionMap.all { (key, value) -> newItem.permissionMap[key] == value } &&
            oldItem.isSelected == newItem.isSelected

        override fun getChangePayload(oldItem: AppPermissionItem, newItem: AppPermissionItem): Any? {
            if (oldItem.isSelected != newItem.isSelected) return "selection"
            return super.getChangePayload(oldItem, newItem)
        }
    }
}
