package com.notify2email.app.domain.repository

import com.notify2email.app.domain.model.PermissionState

interface PermissionRepository {
    suspend fun getPermissionState(): PermissionState
}
