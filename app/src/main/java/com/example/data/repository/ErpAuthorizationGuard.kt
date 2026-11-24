package com.example.data.repository

import com.example.data.local.AaCraveDatabase
import com.example.domain.model.UserSession

/**
 * Centralized runtime authorization guard for repositories:
 * 1) Blocks suspended/deactivated operator accounts (isActive == false) from protected operations.
 * 2) Enforces strict branch isolation for non-multi-branch roles (e.g., BRANCH_MANAGER, CASHIER).
 */
object ErpAuthorizationGuard {

    suspend fun verifyActiveOperator(
        database: AaCraveDatabase,
        session: UserSession
    ): SecurityException? {
        val profile = database.userProfileDao().getProfileByUid(session.uid)
        if (profile != null && !profile.isActive) {
            return SecurityException("Access denied: operator account '${session.email}' is suspended or deactivated.")
        }
        return null
    }

    fun verifyBranchAccess(
        session: UserSession,
        targetBranchId: String,
        allowAllForConsolidated: Boolean = false
    ): SecurityException? {
        if (allowAllForConsolidated && session.capabilities.canSwitchBranches) {
            return null
        }
        if (!session.capabilities.canSwitchBranches && targetBranchId != session.branchId) {
            return SecurityException(
                "Access denied: ${session.role.displayName} is restricted to assigned branch '${session.branchName}'."
            )
        }
        return null
    }

    suspend fun verifyActiveAndBranch(
        database: AaCraveDatabase,
        session: UserSession,
        targetBranchId: String
    ): SecurityException? {
        verifyActiveOperator(database, session)?.let { return it }
        return verifyBranchAccess(session, targetBranchId)
    }
}
