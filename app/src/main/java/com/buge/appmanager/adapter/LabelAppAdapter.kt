package com.buge.appmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
// ✅ 修复3：新增 DiffUtil 和 ListAdapter 相关导入
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.google.android.material.checkbox.MaterialCheckBox

data class LabelAppItem(
    val app: AppInfo,
    var isSelected: Boolean = false,
    var isChecked: Boolean = false
)

// ✅ 修复3：继承 ListAdapter 替代 RecyclerView.Adapter，
//    内置 DiffUtil 异步差异计算，只刷新真正变化的 item
class LabelAppAdapter(
    private val onItemClick: (AppInfo, Boolean) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<LabelAppItem, LabelAppAdapter.ViewHolder>(DiffCallback()) {

    // ✅ 修复3：DiffCallback 定义，按 packageName 判断是否同一条目
    class DiffCallback : DiffUtil.ItemCallback<LabelAppItem>() {
        override fun areItemsTheSame(oldItem: LabelAppItem, newItem: LabelAppItem): Boolean {
            return oldItem.app.packageName == newItem.app.packageName
        }
        override fun areContentsTheSame(oldItem: LabelAppItem, newItem: LabelAppItem): Boolean {
            return oldItem.app.packageName == newItem.app.packageName &&
                   oldItem.isSelected == newItem.isSelected &&
                   oldItem.isChecked == newItem.isChecked
        }
    }

    private var selectionMode: Boolean = false

    // ✅ 修复3：submitList 改为调用父类 submitList()，
    //    由 DiffUtil 在后台线程计算差异，仅通知变化的行，不再全量刷新
    fun submitList(newItems: List<LabelAppItem>) {
        super.submitList(newItems)
    }

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode != enabled) {
            selectionMode = enabled
            if (!enabled) {
                currentList.forEach { it.isChecked = false }
                onSelectionChanged(0)
            }
            notifyItemRangeChanged(0, itemCount, "selection_mode")
        }
    }

    fun isSelectionMode(): Boolean = selectionMode

    fun getSelectedItems(): List<AppInfo> {
        return currentList.filter { it.isChecked }.map { it.app }
    }

    fun clearSelection() {
        currentList.forEach { it.isChecked = false }
        notifyItemRangeChanged(0, itemCount, "selection")
        onSelectionChanged(0)
    }

    fun updateSelection(packageName: String, isChecked: Boolean) {
        val position = currentList.indexOfFirst { it.app.packageName == packageName }
        if (position >= 0) {
            currentList[position].isChecked = isChecked
            notifyItemChanged(position, "selection")
            onSelectionChanged(currentList.count { it.isChecked })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)  // ✅ 修复3：使用 getItem() 替代 items[position]
        holder.bind(item, selectionMode)
        applyItemBackground(holder, position)
        setupClickListeners(holder, position, item)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)  // ✅ 修复3：使用 getItem()
        if (payloads.contains("selection_mode")) {
            holder.updateSelectionMode(selectionMode, item.isChecked)
        } else if (payloads.contains("selection")) {
            holder.updateCheckbox(item.isChecked)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun setupClickListeners(holder: ViewHolder, position: Int, item: LabelAppItem) {
        holder.itemView.setOnClickListener(null)
        holder.checkbox.setOnCheckedChangeListener(null)

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                val newChecked = !item.isChecked
                item.isChecked = newChecked
                holder.checkbox.isChecked = newChecked
                holder.animateCheckbox(newChecked)
                notifyItemChanged(position, "selection")
                onSelectionChanged(currentList.count { it.isChecked })
            } else {
                val newSelected = !item.isSelected
                item.isSelected = newSelected
                onItemClick(item.app, newSelected)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                item.isChecked = true
                holder.checkbox.isChecked = true
                notifyItemRangeChanged(0, itemCount, "selection_mode")
                onSelectionChanged(currentList.count { it.isChecked })
                holder.animateCardSelection(true)
            }
            true
        }

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (item.isChecked != isChecked) {
                item.isChecked = isChecked
                holder.animateCheckbox(isChecked)
                notifyItemChanged(position, "selection")
                onSelectionChanged(currentList.count { it.isChecked })
            }
        }
    }

    private fun applyItemBackground(holder: ViewHolder, position: Int) {
        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
        val size = itemCount  // ✅ 修复3：使用 itemCount 替代 items.size
        val background = when {
            size == 1 -> R.drawable.bg_setting_item_single
            position == 0 -> R.drawable.bg_setting_item_top
            position == size - 1 -> R.drawable.bg_setting_item_bottom
            else -> R.drawable.bg_setting_item_middle
        }
        container.setBackgroundResource(background)
    }

    // ✅ 修复3：继承 ListAdapter 后 getItemCount() 由父类自动管理，无需再重写

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val systemAppBadge: View = itemView.findViewById(R.id.system_app_badge)
        private val textContainer: ViewGroup = appName.parent as ViewGroup
        val checkbox: MaterialCheckBox = itemView.findViewById(R.id.checkbox)
        private val cardView: View = itemView

        fun bind(item: LabelAppItem, selectionMode: Boolean) {
            val icon = item.app.icon
            if (icon != null) {
                appIcon.setImageDrawable(icon)
            } else {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
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
            checkbox.isChecked = item.isChecked
        }

        fun updateSelectionMode(mode: Boolean, isChecked: Boolean) {
            if (mode) {
                checkbox.visibility = View.VISIBLE
                checkbox.alpha = 0f
                checkbox.scaleX = 0.5f
                checkbox.scaleY = 0.5f

                appIcon.animate().translationX(28f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
                textContainer.animate().translationX(28f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
                packageName.animate().translationX(28f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
                checkbox.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator()).start()
            } else {
                appIcon.animate().translationX(0f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
                textContainer.animate().translationX(0f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
                packageName.animate().translationX(0f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
                checkbox.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(150)
                    .withEndAction { checkbox.visibility = View.GONE }.start()
            }
            checkbox.isChecked = isChecked
        }

        fun updateCheckbox(isChecked: Boolean) {
            checkbox.isChecked = isChecked
        }

        fun animateCardSelection(selected: Boolean) {
            if (selected) {
                cardView.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        cardView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    }.start()
            }
        }

        fun animateCheckbox(checked: Boolean) {
            if (checked) {
                checkbox.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        checkbox.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()
            }
        }
    }
}
