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

    private var _binding: android.view.View? = null
    private val binding get() = _binding!!
    private var fontApplied = false

    private val categories = listOf(
        PermissionCategory(
            id = "mic",
            displayName = "麦克风",
            iconRes = R.drawable.ic_mic,
            permissions = AppRepository.PERMISSION_MICROPHONE
        ),
        PermissionCategory(
            id = "camera",
            displayName = "摄像头",
            iconRes = R.drawable.ic_camera,
            permissions = AppRepository.PERMISSION_CAMERA
        ),
        PermissionCategory(
            id = "location",
            displayName = "位置",
            iconRes = R.drawable.ic_location,
            permissions = AppRepository.PERMISSION_LOCATION
        ),
        PermissionCategory(
            id = "contacts",
            displayName = "联系人",
            iconRes = R.drawable.ic_contacts,
            permissions = AppRepository.PERMISSION_CONTACTS
        ),
        PermissionCategory(
            id = "storage",
            displayName = "存储",
            iconRes = R.drawable.ic_storage,
            permissions = AppRepository.PERMISSION_STORAGE
        ),
        PermissionCategory(
            id = "manage_storage",
            displayName = "所有文件访问",
            iconRes = R.drawable.ic_storage,
            permissions = AppRepository.PERMISSION_MANAGE_STORAGE
        ),
        PermissionCategory(
            id = "phone",
            displayName = "电话",
            iconRes = R.drawable.ic_phone,
            permissions = AppRepository.PERMISSION_PHONE
        ),
        PermissionCategory(
            id = "sms",
            displayName = "短信",
            iconRes = R.drawable.ic_sms,
            permissions = AppRepository.PERMISSION_SMS
        ),
        PermissionCategory(
            id = "calendar",
            displayName = "日历",
            iconRes = R.drawable.ic_calendar,
            permissions = AppRepository.PERMISSION_CALENDAR
        ),
        PermissionCategory(
            id = "sensors",
            displayName = "身体传感器",
            iconRes = R.drawable.ic_sensors,
            permissions = AppRepository.PERMISSION_SENSORS
        ),
        PermissionCategory(
            id = "notifications",
            displayName = "通知",
            iconRes = R.drawable.ic_notifications,
            permissions = AppRepository.PERMISSION_NOTIFICATIONS
        ),
        PermissionCategory(
            id = "overlay",
            displayName = "悬浮窗",
            iconRes = R.drawable.ic_overlay,
            permissions = AppRepository.PERMISSION_OVERLAY
        ),
        PermissionCategory(
            id = "install_unknown",
            displayName = "安装未知应用",
            iconRes = R.drawable.ic_install_unknown,
            permissions = AppRepository.PERMISSION_INSTALL_UNKNOWN_APPS
        ),
        PermissionCategory(
            id = "write_settings",
            displayName = "修改系统设置",
            iconRes = R.drawable.ic_settings,
            permissions = listOf("android.permission.WRITE_SETTINGS")
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
                putInt("categoryIcon", category.iconRes)
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
            
            // Apply corner radius
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
            private val arrow: View = itemView.findViewById(R.id.arrow_forward)

            fun bind(category: PermissionCategory) {
                icon.setImageResource(category.iconRes)
                name.text = category.displayName
            }
        }
    }
}
