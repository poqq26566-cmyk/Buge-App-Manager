package com.buge.appmanager.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.BaseActivity
import com.buge.appmanager.R
import com.buge.appmanager.adapter.AppPermissionAdapter
import com.buge.appmanager.adapter.AppPermissionItem
import com.buge.appmanager.databinding.FragmentPermissionDetailBinding
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.viewmodel.PermissionsViewModel

class PermissionDetailFragment : Fragment() {
    private var _binding: FragmentPermissionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PermissionsViewModel by viewModels()
    private lateinit var adapter: AppPermissionAdapter
    private var currentPermissions: List<String> = emptyList()
    private var categoryName: String = ""
    private var categoryIcon: Int = R.drawable.ic_security
    private var fontApplied = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            currentPermissions = it.getStringArrayList("permissions") ?: emptyList()
            categoryName = it.getString("categoryName") ?: "权限"
            categoryIcon = it.getInt("categoryIcon", R.drawable.ic_security)
        }

        if (currentPermissions.isEmpty()) {
            currentPermissions = listOf("android.permission.RECORD_AUDIO")
            categoryName = "麦克风"
        }

        setupBackPressedCallback()
        setupToolbar()
        setupRecyclerView()
        setupBatchActions()
        observeViewModel()
        viewModel.loadAppsForPermissions(currentPermissions)
    }

    override fun onResume() {
        super.onResume()
        if (activity is BaseActivity && !fontApplied) {
            FontOverrideHelper.applyToActivity(activity as BaseActivity)
            fontApplied = true
        }
        // 从系统设置返回后刷新列表，反映最新授权状态
        if (::adapter.isInitialized) {
            viewModel.loadAppsForPermissions(currentPermissions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.isInSelectionMode()) {
                    adapter.clearSelection()
                    adapter.setSelectionMode(false)
                    hideBatchActionBar()
                } else {
                    isEnabled = false
                    parentFragmentManager.popBackStack()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener {
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
                adapter.setSelectionMode(false)
                hideBatchActionBar()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
        binding.toolbar.title = categoryName
        binding.categoryIcon.setImageResource(categoryIcon)
    }

    private fun setupRecyclerView() {
        if (!isAdded || view == null) return
        adapter = AppPermissionAdapter(
            onSelectionChanged = { count -> updateSelectionUI(count) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupBatchActions() {
        if (!isAdded || view == null) return

        // 批量操作：跳转第一个选中应用的该权限设置页
        binding.btnBatchRevoke.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            val first = selected.first()
            adapter.clearSelection()
            adapter.setSelectionMode(false)
            hideBatchActionBar()
            openDirectPermissionSettings(first.app.packageName, first.primaryPermission)
        }

        binding.btnBatchGrant.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            val first = selected.first()
            adapter.clearSelection()
            adapter.setSelectionMode(false)
            hideBatchActionBar()
            openDirectPermissionSettings(first.app.packageName, first.primaryPermission)
        }
    }

    /**
     * 直接跳转到该应用的指定权限页（如"麦克风权限"页）
     * 通过读取权限所属权限组来构造 Intent
     */
    private fun openDirectPermissionSettings(packageName: String, permission: String) {
        try {
            val permInfo = requireContext().packageManager.getPermissionInfo(permission, 0)
            val groupName = permInfo.group
            if (groupName != null) {
                val intent = Intent("android.intent.action.MANAGE_APP_PERMISSION").apply {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                    putExtra("android.intent.extra.PERMISSION_GROUP_NAME", groupName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            }
        } catch (_: Exception) { }
        // 回退：跳应用详情页
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(fallback)
    }

    private fun showBatchActionBar() {
        if (!isAdded || view == null) return
        if (binding.batchActionBar.visibility != View.VISIBLE) {
            binding.batchActionBar.visibility = View.VISIBLE
            binding.batchActionBar.alpha = 0f
            binding.batchActionBar.scaleX = 0.8f
            binding.batchActionBar.scaleY = 0.8f
            binding.batchActionBar.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(250).setInterpolator(OvershootInterpolator()).start()
        }
    }

    private fun hideBatchActionBar() {
        if (!isAdded || view == null) return
        if (binding.batchActionBar.visibility == View.VISIBLE) {
            binding.batchActionBar.animate()
                .alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(200)
                .withEndAction {
                    if (isAdded && view != null) binding.batchActionBar.visibility = View.GONE
                }.start()
        }
    }

    private fun updateSelectionUI(count: Int) {
        if (!isAdded || view == null) return
        if (count > 0) {
            binding.selectedCountText.text = getString(R.string.selected_count, count)
            adapter.setSelectionMode(true)
            showBatchActionBar()
        } else {
            adapter.setSelectionMode(false)
            hideBatchActionBar()
        }
    }

    private fun observeViewModel() {
        viewModel.appsWithPermission.observe(viewLifecycleOwner) { apps ->
            if (!isAdded || view == null) return@observe
            val primaryPerm = currentPermissions.firstOrNull() ?: return@observe
            val items = apps.map { (app, permMap) ->
                AppPermissionItem(app = app, permissionMap = permMap, primaryPermission = primaryPerm)
            }
            adapter.submitList(items)
            binding.toolbar.subtitle = getString(R.string.apps_count, items.size)
            val isEmpty = items.isEmpty()
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.loadingOverlay.visibility = View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isAdded || view == null) return@observe
            val hasData = viewModel.appsWithPermission.value?.isNotEmpty() == true
            if (isLoading && !hasData) {
                binding.loadingOverlay.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
            } else {
                binding.loadingOverlay.visibility = View.GONE
                if (hasData) binding.recyclerView.visibility = View.VISIBLE
            }
        }
    }
}
