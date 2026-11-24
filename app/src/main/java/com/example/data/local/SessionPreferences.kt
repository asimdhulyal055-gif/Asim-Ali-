package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "aa_crave_session_prefs")

class SessionPreferences(private val context: Context) {

    private val activeBranchIdKey = stringPreferencesKey("active_branch_id")
    private val previewRoleCodeKey = stringPreferencesKey("preview_role_code")
    private val localStaffUidKey = stringPreferencesKey("local_staff_uid")
    private val deviceIdKey = stringPreferencesKey("erp_device_id")
    private val autoSyncEnabledKey = booleanPreferencesKey("auto_sync_enabled")
    private val lastCloudSyncAtKey = longPreferencesKey("last_cloud_sync_at")

    val activeBranchId: Flow<String> = context.sessionDataStore.data.map { prefs ->
        prefs[activeBranchIdKey] ?: DatabaseSeeder.MAIN_BRANCH_ID
    }

    val previewRoleCode: Flow<String?> = context.sessionDataStore.data.map { prefs ->
        prefs[previewRoleCodeKey]
    }

    val localStaffUid: Flow<String?> = context.sessionDataStore.data.map { prefs ->
        prefs[localStaffUidKey]
    }

    val autoSyncEnabled: Flow<Boolean> = context.sessionDataStore.data.map { prefs ->
        prefs[autoSyncEnabledKey] ?: true
    }

    val lastCloudSyncAt: Flow<Long?> = context.sessionDataStore.data.map { prefs ->
        prefs[lastCloudSyncAtKey]
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = context.sessionDataStore.data.first()[deviceIdKey]
        if (!current.isNullOrBlank()) return current
        val created = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        context.sessionDataStore.edit { prefs ->
            val existing = prefs[deviceIdKey]
            if (existing.isNullOrBlank()) {
                prefs[deviceIdKey] = created
            }
        }
        return context.sessionDataStore.data.first()[deviceIdKey] ?: created
    }

    suspend fun setActiveBranchId(branchId: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[activeBranchIdKey] = branchId
        }
    }

    suspend fun setPreviewRoleCode(roleCode: String?) {
        context.sessionDataStore.edit { prefs ->
            if (roleCode == null) {
                prefs.remove(previewRoleCodeKey)
            } else {
                prefs[previewRoleCodeKey] = roleCode
            }
        }
    }

    suspend fun setLocalStaffUid(uid: String?) {
        context.sessionDataStore.edit { prefs ->
            if (uid == null) {
                prefs.remove(localStaffUidKey)
            } else {
                prefs[localStaffUidKey] = uid
            }
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[autoSyncEnabledKey] = enabled
        }
    }

    suspend fun setLastCloudSyncAt(timestamp: Long) {
        context.sessionDataStore.edit { prefs ->
            prefs[lastCloudSyncAtKey] = timestamp
        }
    }
}
