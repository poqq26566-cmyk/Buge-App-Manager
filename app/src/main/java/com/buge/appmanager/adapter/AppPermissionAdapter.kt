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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.LogManager
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

        /**
         * 权限 → 权限组名 硬编码映射表
         * 用于普通运行时权限（有权限组），走 MANAGE_APP_PERMISSION Intent
         */
        private val PERMISSION_GROUP_MAP = mapOf(
            // 麦克风
            "android.permission.RECORD_AUDIO" to "android.permission-group.MICROPHONE",
            // 摄像头
            "android.permission.CAMERA" to "android.permission-group.CAMERA",
            // 位置
            "android.permission.ACCESS_FINE_LOCATION" to "android.permission-group.LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION" to "android.permission-group.LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION" to "android.permission-group.LOCATION",
            // 联系人
            "android.permission.READ_CONTACTS" to "android.permission-group.CONTACTS",
            "android.permission.WRITE_CONTACTS" to "android.permission-group.CONTACTS",
            "android.permission.GET_ACCOUNTS" to "android.permission-group.CONTACTS",
            // 存储（Android 12 及以下）
            "android.permission.READ_EXTERNAL_STORAGE" to "android.permission-group.STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" to "android.permission-group.STORAGE",
            // 媒体（Android 13+）
            "android.permission.READ_MEDIA_IMAGES" to "android.permission-group.READ_MEDIA_VISUAL",
            "android.permission.READ_MEDIA_VIDEO" to "android.permission-group.READ_MEDIA_VISUAL",
            "android.permission.READ_MEDIA_AUDIO" to "android.permission-group.READ_MEDIA_AURAL",
            // 媒体（Android 14+）
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" to "android.permission-group.READ_MEDIA_VISUAL",
            // 电话
            "android.permission.READ_PHONE_STATE" to "android.permission-group.PHONE",
            "android.permission.READ_PHONE_NUMBERS" to "android.permission-group.PHONE",
            "android.permission.CALL_PHONE" to "android.permission-group.PHONE",
            "android.permission.ADD_VOICEMAIL" to "android.permission-group.PHONE",
            "android.permission.USE_SIP" to "android.permission-group.PHONE",
            "android.permission.PROCESS_OUTGOING_CALLS" to "android.permission-group.PHONE",
            // 通话记录
            "android.permission.READ_CALL_LOG" to "android.permission-group.CALL_LOG",
            "android.permission.WRITE_CALL_LOG" to "android.permission-group.CALL_LOG",
            // 短信
            "android.permission.SEND_SMS" to "android.permission-group.SMS",
            "android.permission.RECEIVE_SMS" to "android.permission-group.SMS",
            "android.permission.READ_SMS" to "android.permission-group.SMS",
            "android.permission.RECEIVE_WAP_PUSH" to "android.permission-group.SMS",
            "android.permission.RECEIVE_MMS" to "android.permission-group.SMS",
            // 日历
            "android.permission.READ_CALENDAR" to "android.permission-group.CALENDAR",
            "android.permission.WRITE_CALENDAR" to "android.permission-group.CALENDAR",
            // 身体传感器
            "android.permission.BODY_SENSORS" to "android.permission-group.SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND" to "android.permission-group.SENSORS",
            // 活动识别
            "android.permission.ACTIVITY_RECOGNITION" to "android.permission-group.ACTIVITY_RECOGNITION",
            // 附近设备（Android 12+）
            "android.permission.BLUETOOTH_SCAN" to "android.permission-group.NEARBY_DEVICES",
            "android.permission.BLUETOOTH_CONNECT" to "android.permission-group.NEARBY_DEVICES",
            "android.permission.BLUETOOTH_ADVERTISE" to "android.permission-group.NEARBY_DEVICES",
            "android.permission.UWB_RANGING" to "android.permission-group.NEARBY_DEVICES",
            // 通知（Android 13+，低版本走 GroupMap，高版本走 getSpecialPermissionIntent 直跳）
            "android.permission.POST_NOTIFICATIONS" to "android.permission-group.NOTIFICATIONS"
        )

        /**
         * 统一入口：根据权限类型选择最精确的跳转方式
         * 优先级：特殊专属页 → 厂商定制页 → 原生权限组页
         * 所有路径均不兜底跳应用详情，失败则记日志 + Toast 提示
         */
        fun openPermissionSettings(context: Context, packageName: String, permission: String) {
            LogManager.info(
                context, "openPermissionSettings 开始",
                "pkg=$packageName perm=$permission manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}",
                tag = "PermJump"
            )

            // 1. 特殊权限：直接跳专属系统设置页
            val specialIntent = getSpecialPermissionIntent(context, packageName, permission)
            if (specialIntent != null) {
                try {
                    context.startActivity(specialIntent)
                    LogManager.success(context, "步骤1成功: 特殊权限专属页", "$specialIntent", tag = "PermJump")
                    return
                } catch (e: Exception) {
                    LogManager.warning(context, "步骤1失败: 特殊权限专属页", "$specialIntent -> ${e}", tag = "PermJump")
                }
            } else {
                LogManager.debug(context, "步骤1跳过: 该权限无专属特殊页", tag = "PermJump")
            }

            // 2. 厂商定制 ROM 权限页（ColorOS / OxygenOS 等会拦截原生 Intent 的机型）
            val vendorIntent = getVendorPermissionListIntent(context, packageName)
            if (vendorIntent != null) {
                try {
                    context.startActivity(vendorIntent)
                    LogManager.success(context, "步骤2成功: 厂商权限页", "${vendorIntent.component}", tag = "PermJump")
                    return
                } catch (e: Exception) {
                    LogManager.warning(context, "步骤2失败: 厂商权限页", "${vendorIntent.component} -> ${e}", tag = "PermJump")
                }
            } else {
                LogManager.warning(
                    context, "步骤2无候选可用: 厂商权限页一个都没 resolve 成功",
                    "manufacturer=${Build.MANUFACTURER}，说明候选包名/类名在当前系统版本上已失效", tag = "PermJump"
                )
            }

            // 3. 普通运行时权限：通过权限组直接跳到该权限设置页
            val groupName = PERMISSION_GROUP_MAP[permission]
                ?: runCatching {
                    context.packageManager.getPermissionInfo(permission, 0).group
                }.getOrNull()

            if (groupName != null) {
                try {
                    val intent = Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
                        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                        putExtra("android.intent.extra.PERMISSION_GROUP_NAME", groupName)
                        putExtra("android.intent.extra.USER", Process.myUserHandle())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    LogManager.success(context, "步骤3成功: 原生 MANAGE_APP_PERMISSION", "group=$groupName", tag = "PermJump")
                    return
                } catch (e: Exception) {
                    LogManager.error(
                        context, "步骤3失败: 原生 MANAGE_APP_PERMISSION",
                        "group=$groupName -> ${e}", tag = "PermJump", throwable = e
                    )
                }
            } else {
                LogManager.error(context, "步骤3无法执行: 找不到该权限所属的 permission group", "perm=$permission", tag = "PermJump")
            }

            // 所有路径均失败 → 不跳应用详情页，仅提示用户去日志页查看原因
            LogManager.error(context, "全部跳转路径均失败", "pkg=$packageName perm=$permission", tag = "PermJump")
            Toast.makeText(
                context,
                "跳转失败：当前系统不支持直接跳转到该权限设置页，详情见「活动」日志(tag: PermJump)",
                Toast.LENGTH_LONG
            ).show()
        }

        /**
         * 特殊权限 → 直接跳转对应系统设置页（完整覆盖版）
         *
         * 覆盖清单：
         *  SYSTEM_ALERT_WINDOW                    → 悬浮窗              (API 23+)
         *  REQUEST_INSTALL_PACKAGES               → 安装未知应用         (API 26+)
         *  MANAGE_EXTERNAL_STORAGE                → 所有文件访问         (API 30+)
         *  WRITE_SETTINGS                         → 修改系统设置         (API 23+)
         *  PACKAGE_USAGE_STATS                    → 使用情况访问         (API 21+)
         *  REQUEST_IGNORE_BATTERY_OPTIMIZATIONS   → 忽略电池优化         (API 23+)
         *  MANAGE_MEDIA                           → 媒体管理            (API 31+)
         *  ACCESS_NOTIFICATION_POLICY             → 勿扰模式访问         (API 23+)
         *  SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM → 精确闹钟            (API 31+)
         *  BIND_NOTIFICATION_LISTENER_SERVICE     → 通知监听服务         (全版本)
         *  BIND_VPN_SERVICE                       → VPN 设置            (API 24+)
         *  POST_NOTIFICATIONS                     → App 通知设置         (API 33+)
         */
        private fun getSpecialPermissionIntent(
            context: Context,
            packageName: String,
            permission: String
        ): Intent? {
            return when (permission) {

                // ── 悬浮窗 ──────────────────────────────────────────────────
                "android.permission.SYSTEM_ALERT_WINDOW" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 安装未知应用 (API 26+) ───────────────────────────────────
                "android.permission.REQUEST_INSTALL_PACKAGES" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 所有文件访问 (API 30+) ───────────────────────────────────
                "android.permission.MANAGE_EXTERNAL_STORAGE" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 修改系统设置 (API 23+) ───────────────────────────────────
                "android.permission.WRITE_SETTINGS" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 应用使用情况（全局列表，系统不支持直接定位单 App）(API 21+) ─
                "android.permission.PACKAGE_USAGE_STATS" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 忽略电池优化 (API 23+) ───────────────────────────────────
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 媒体管理 (API 31+) ───────────────────────────────────────
                "android.permission.MANAGE_MEDIA" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(
                            Settings.ACTION_REQUEST_MANAGE_MEDIA,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 勿扰模式访问（全局列表，系统不支持直接定位单 App）(API 23+) ─
                "android.permission.ACCESS_NOTIFICATION_POLICY" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 精确闹钟 (API 31+) ───────────────────────────────────────
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.USE_EXACT_ALARM" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── 通知监听服务（全局列表，系统不支持直接定位单 App）───────────
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" ->
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

                // ── VPN (API 24+) ────────────────────────────────────────────
                "android.permission.BIND_VPN_SERVICE" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Intent(Settings.ACTION_VPN_SETTINGS)
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else null

                // ── App 通知设置（API 33+ 可直跳单 App，低版本走 PERMISSION_GROUP_MAP）
                "android.permission.POST_NOTIFICATIONS" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else null

                // 其余权限交由 PERMISSION_GROUP_MAP + MANAGE_APP_PERMISSION 处理
                else -> null
            }
        }

        /**
         * 厂商定制 ROM 的应用权限列表页
         * 适配 OPPO / OnePlus / Realme（ColorOS / OxygenOS）
         */
        private fun getVendorPermissionListIntent(context: Context, packageName: String): Intent? {
            val manufacturer = Build.MANUFACTURER.lowercase()
            if (manufacturer != "oneplus" && manufacturer != "oppo" && manufacturer != "realme") {
                return null
            }

            val candidates = listOf(
                // OPLUS 品牌壳（新版 ColorOS / OxygenOS 核心权限页）
                "com.oplus.securitypermission" to "com.oplusos.securitypermission.permission.singlepage.AppPermissionsSettingsActivity",
                "com.oplus.securitypermission" to "com.oplusos.securitypermission.permission.singlepage.PermissionTabActivity",
                "com.coloros.securitypermission" to "com.coloros.securitypermission.permission.singlepage.AppPermissionsSettingsActivity",
                // ColorOS 老包名与历史路径
                "com.coloros.safecenter" to "com.coloros.privacypermissionsentry.PermissionTopActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.PermissionTopActivity",
                "com.color.safecenter" to "com.color.safecenter.permission.PermissionTopActivity",
                // OnePlus 自有包名
                "com.oneplus.security" to "com.oneplus.security.privacypermissionsentry.PermissionTopActivity",
                "com.oneplus.security" to "com.oneplus.security.permission.PermissionTopActivity"
            )

            val extraKeys = listOf(
                "packageName",
                "pkgName",
                "extra_pkgname",
                "android.intent.extra.PACKAGE_NAME"
            )

            for ((pkg, cls) in candidates) {
                val probe = Intent().apply { setClassName(pkg, cls) }
                val resolved = context.packageManager.resolveActivity(probe, 0) != null
                LogManager.debug(context, "厂商候选探测", "$pkg/$cls resolve=$resolved", tag = "PermJump")
                if (resolved) {
                    for (extraKey in extraKeys) {
                        val intent = Intent().apply {
                            setClassName(pkg, cls)
                            putExtra(extraKey, packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        return intent
                    }
                }
            }
            return null
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

        // 长按进入选择模式
        holder.itemView.setOnLongClickListener(null)
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

        // 点击行
        holder.itemView.setOnClickListener(null)
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

        // Checkbox
        holder.checkbox.setOnClickListener(null)
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

        // Chip 点击 → 直接跳该权限专属设置页
        holder.permStatusChip.setOnClickListener(null)
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
