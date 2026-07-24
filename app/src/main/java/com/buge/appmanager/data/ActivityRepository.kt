package com.buge.appmanager.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import com.buge.appmanager.model.ActivityDetail
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActivityRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    // ✅ 修复2：添加图标 LRU 缓存，最多缓存150个图标，避免重复加载
    private val iconCache = LruCache<String, Drawable>(150)

    companion object {
        private const val TAG = "ActivityRepository"
    }

    // ✅ 修复2：统一图标获取方法，优先读缓存
    private fun getCachedIcon(packageName: String): Drawable? {
        return iconCache.get(packageName) ?: try {
            pm.getApplicationIcon(packageName).also { iconCache.put(packageName, it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getInstalledAppsWithActivities(showSystemApps: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            LogManager.info(context, "ActivityRepository: Getting apps with activities", "ShowSystemApps: $showSystemApps")
            
            val allApps = mutableListOf<AppInfo>()
            
            // ✅ 修复1：一次性带 GET_ACTIVITIES flag 获取所有包信息，彻底消除循环内的重复查询
            val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            }
            
            LogManager.debug(context, "ActivityRepository: Total packages", "${packages.size}")
            
            for (pkgInfo in packages) {
                try {
                    val packageName = pkgInfo.packageName
                    // ✅ 修复1：直接从 pkgInfo 中取 applicationInfo，无需再调用 getApplicationInfo
                    val appInfo = pkgInfo.applicationInfo ?: continue
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    if (!showSystemApps && isSystem) {
                        continue
                    }
                    
                    // ✅ 修复1：直接用 pkgInfo.activities，不再重复调用 getPackageInfo
                    val activities = pkgInfo.activities
                    if (activities.isNullOrEmpty()) {
                        continue
                    }
                    
                    allApps.add(
                        AppInfo(
                            packageName = packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            versionName = pkgInfo.versionName ?: "",
                            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                pkgInfo.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                pkgInfo.versionCode.toLong()
                            },
                            // ✅ 修复2：使用缓存方法加载图标
                            icon = getCachedIcon(packageName),
                            isSystemApp = isSystem,
                            isEnabled = appInfo.enabled,
                            installTime = pkgInfo.firstInstallTime,
                            updateTime = pkgInfo.lastUpdateTime,
                            targetSdkVersion = appInfo.targetSdkVersion,
                            minSdkVersion = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                appInfo.minSdkVersion
                            } else 0,
                            apkPath = appInfo.sourceDir ?: ""
                        )
                    )
                } catch (e: Exception) {
                    LogManager.warning(context, "ActivityRepository: Error getting app info", "${pkgInfo.packageName}: ${e.message}")
                }
            }
            
            val sorted = allApps.sortedBy { it.appName.lowercase() }
            LogManager.info(context, "ActivityRepository: Apps with activities loaded", "Count: ${sorted.size}")
            sorted
        } catch (e: Exception) {
            LogManager.error(context, "ActivityRepository: Error getting apps with activities", e.message)
            emptyList()
        }
    }

    suspend fun getAppActivities(packageName: String, showUndeclared: Boolean): List<ActivityDetail> = withContext(Dispatchers.IO) {
        try {
            LogManager.info(context, "ActivityRepository: Getting activities for app", "Package: $packageName, ShowUndeclared: $showUndeclared")
            
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
            val activities = packageInfo?.activities ?: return@withContext emptyList()
            
            LogManager.debug(context, "ActivityRepository: Total activities in manifest", "${activities.size}")
            
            val result = mutableListOf<ActivityDetail>()
            
            // Pre-load intent filter counts in batch to avoid per-activity queries
            val intentFilterCounts = mutableMapOf<String, Int>()
            
            for (activityInfo in activities) {
                val className = activityInfo.name
                var intentFilterCount = 0
                try {
                    val intent = Intent().apply { 
                        setClassName(packageName, className)
                        action = Intent.ACTION_MAIN
                    }
                    val resolveInfoList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(intent, 0)
                    }
                    intentFilterCount = if (resolveInfoList.isNotEmpty()) 1 else 0
                } catch (e: Exception) {
                    intentFilterCount = 0
                }
                intentFilterCounts[className] = intentFilterCount
            }
            
            var exportedCount = 0
            var undeclaredCount = 0
            
            for (activityInfo in activities) {
                val className = activityInfo.name
                val intentFilterCount = intentFilterCounts[className] ?: 0
                
                val activity = ActivityDetail(
                    name = try { 
                        val label = activityInfo.loadLabel(pm)
                        if (label.isNullOrEmpty()) activityInfo.name.substringAfterLast(".") else label.toString()
                    } catch (e: Exception) { 
                        activityInfo.name.substringAfterLast(".") 
                    },
                    className = className,
                    isExported = activityInfo.exported,
                    intentFilterCount = intentFilterCount,
                    permission = activityInfo.permission ?: "None",
                    launchMode = getLaunchModeString(activityInfo.launchMode),
                    parentActivityName = activityInfo.parentActivityName ?: "None"
                )
                
                if (showUndeclared) {
                    result.add(activity)
                    undeclaredCount++
                } else {
                    if (activity.isExported) {
                        result.add(activity)
                        exportedCount++
                    }
                }
            }
            
            val sorted = result.sortedBy { it.name.lowercase() }
            LogManager.info(context, "ActivityRepository: Activities loaded", "Package: $packageName, Exported: $exportedCount, Undeclared: $undeclaredCount, Total: ${sorted.size}")
            sorted
        } catch (e: Exception) {
            LogManager.error(context, "ActivityRepository: Error getting activities", "Package: $packageName, Error: ${e.message}")
            emptyList()
        }
    }

    private fun getLaunchModeString(launchMode: Int): String {
        return when (launchMode) {
            android.content.pm.ActivityInfo.LAUNCH_MULTIPLE -> "standard"
            android.content.pm.ActivityInfo.LAUNCH_SINGLE_TOP -> "singleTop"
            android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK -> "singleTask"
            android.content.pm.ActivityInfo.LAUNCH_SINGLE_INSTANCE -> "singleInstance"
            else -> "unknown"
        }
    }

    fun launchActivity(packageName: String, className: String): Boolean {
        return try {
            val componentName = ComponentName(packageName, className)
            val intent = Intent().apply {
                setComponent(componentName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            LogManager.info(context, "Activity launched", "Package: $packageName, Class: $className")
            true
        } catch (e: Exception) {
            LogManager.error(context, "Error launching activity", "Package: $packageName, Class: $className, Error: ${e.message}")
            false
        }
    }
}
