package com.buge.appmanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.BaseActivity
import com.buge.appmanager.R
import com.buge.appmanager.data.AppRepository
import com.buge.appmanager.model.PermissionCategory
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LogManager
import com.google.android.material.imageview.ShapeableImageView

class PermissionCategoriesFragment : Fragment() {

    private var _binding: View? = null
    private val binding get() = _binding!!
    private var fontApplied = false

    // 使用项目中已有的图标资源
    private val categories = listOf(
        PermissionCategory(
            id = "mic",
            displayName = "麦克风",
            permissions = AppRepository.PERMISSION_MICROPHONE,
            iconResId = R.drawable.ic_mic
        ),
        PermissionCategory(
            id = "camera",
            displayName = "摄像头",
            permissions = AppRepository.PERMISSION_CAMERA,
            iconResId = R.drawable.ic_camera
        ),
        PermissionCategory(
            id = "location",
            displayName = "位置",
            permissions = AppRepository.PERMISSION_LOCATION,
            iconResId = R.drawable.ic_location
        ),
        PermissionCategory(
            id = "contacts",
            displayName = "联系人",
            permissions = AppRepository.PERMISSION_CONTACTS,
            iconResId = R.drawable.ic_security  // 使用已有图标
        ),
        PermissionCategory(
            id = "storage",
            displayName = "存储",
            permissions = AppRepository.PERMISSION_STORAGE,
            iconResId = R.drawable.ic_storage
        ),
        PermissionCategory(
            id = "manage_storage",
            displayName = "所有文件访问",
            permissions = AppRepository.PERMISSION_MANAGE_STORAGE,
            iconResId = R.drawable.ic_storage
        ),
        PermissionCategory(
            id = "phone",
            displayName = "电话",
            permissions = AppRepository.PERMISSION_PHONE,
            iconResId = R.drawable.ic_phone  // 使用已有图标
        ),
        PermissionCategory(
            id = "sms",
            displayName = "短信",
            permissions = AppRepository.PERMISSION_SMS,
            iconResId = R.drawable.ic_sms  // 使用已有图标
        ),
        PermissionCategory(
            id = "calendar",
            displayName = "日历",
            permissions = AppRepository.PERMISSION_CALENDAR,
            iconResId = R.drawable.ic_calendar  // 使用已有图标
        ),
        PermissionCategory(
            id = "sensors",
            displayName = "身体传感器",
            permissions = AppRepository.PERMISSION_SENSORS,
            iconResId = R.drawable.ic_sensors  // 使用已有图标
        ),
        PermissionCategory(
            id = "notifications",
            displayName = "通知",
            permissions = AppRepository.PERMISSION_NOTIFICATIONS,
            iconResId = R.drawable.ic_notifications  // 使用已有图标
        ),
        PermissionCategory(
            id = "overlay",
            displayName = "悬浮窗",
            permissions = AppRepository.PERMISSION_OVERLAY,
            iconResId = R.drawable.ic_overlay  // 使用已有图标
        ),
        PermissionCategory(
            id = "install_unknown",
            displayName = "安装未知应用",
            permissions = AppRepository.PERMISSION_INSTALL_UNKNOWN_APPS,
            iconResId = R.drawable.ic_install_unknown  // 使用已有图标
        ),
        PermissionCategory(
            id = "write_settings",
            displayName = "修改系统设置",
            permissions = listOf("android.permission.WRITE_SETTINGS"),
            iconResId = R.drawable.ic_settings
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = inflater.inflate(R.layout.fragment_permission_categories, container, false)
        return binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBackPressedCallback()
        setupRecyclerView()

        LogManager.info(requireContext(), "PermissionCategoriesFragment created")
    }

    override fun onResume() {
        super.onResume()
        if (activity is BaseActivity && !fontApplied) {
            FontOverrideHelper.applyToActivity(activity as BaseActivity)
            fontApplied = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun setupRecyclerView() {
        val recyclerView = binding.findViewById<RecyclerView>(R.id.categories_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = CategoriesAdapter(categories) { category ->
            navigateToPermissionDetail(category)
        }
    }

    private fun navigateToPermissionDetail(category: PermissionCategory) {
        LogManager.info(requireContext(), "Navigating to permission detail", "Category: ${category.displayName}")
        
        val detailFragment = PermissionDetailFragment().apply {
            arguments = Bundle().apply {
                putStringArrayList("permissions", ArrayList(category.permissions))
                putString("categoryName", category.displayName)
                putInt("categoryIcon", category.iconResId)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack("permission_detail")
            .commit()
    }

    inner class CategoriesAdapter(
        private val items: List<PermissionCategory>,
        private val onItemClick: (PermissionCategory) -> Unit
    ) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_permission_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
            holder.itemView.setOnClickListener { onItemClick(items[position]) }
            
            val container = holder.itemView.findViewById<View>(R.id.item_container)
            val size = items.size
            val background = when {
                size == 1 -> R.drawable.bg_setting_item_single
                position == 0 -> R.drawable.bg_setting_item_top
                position == size - 1 -> R.drawable.bg_setting_item_bottom
                else -> R.drawable.bg_setting_item_middle
            }
            container.setBackgroundResource(background)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val icon: ShapeableImageView = itemView.findViewById(R.id.category_icon)
            private val name: TextView = itemView.findViewById(R.id.category_name)

            fun bind(category: PermissionCategory) {
                icon.setImageResource(category.iconResId)
                name.text = category.displayName
            }
        }
    }
}
