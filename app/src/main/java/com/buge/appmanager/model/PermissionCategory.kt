package com.buge.appmanager.model

data class PermissionCategory(
    val id: String,
    val displayName: String,
    val iconRes: Int,
    val permissions: List<String>
)
