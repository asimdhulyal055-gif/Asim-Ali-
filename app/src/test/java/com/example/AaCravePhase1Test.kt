package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.DatabaseSeeder
import com.example.domain.model.AppModule
import com.example.domain.model.RoleAccessControl
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AaCravePhase1Test {

    private lateinit var database: AaCraveDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AaCraveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rbac denies admin and audit functions to ordinary employees`() {
        val restrictedRoles = listOf(
            UserRole.CASHIER,
            UserRole.KITCHEN_STAFF,
            UserRole.HR,
            UserRole.INVENTORY_MANAGER
        )

        restrictedRoles.forEach { role ->
            val caps = RoleAccessControl.capabilitiesFor(role)
            assertFalse("$role must not have admin access", caps.canAccessAdminFunctions)
            assertFalse("$role must not view audit logs", caps.canViewAuditLogs)
            assertFalse("$role must not switch branches", caps.canSwitchBranches)
            assertFalse(
                "$role must not access USER_PERMISSIONS",
                RoleAccessControl.canAccessModule(role, AppModule.USER_PERMISSIONS)
            )
            assertFalse(
                "$role must not access AUDIT_LOGS",
                RoleAccessControl.canAccessModule(role, AppModule.AUDIT_LOGS)
            )
            assertFalse(
                "$role must not access BRANCH_MANAGEMENT",
                RoleAccessControl.canAccessModule(role, AppModule.BRANCH_MANAGEMENT)
            )
        }
    }

    @Test
    fun `rbac grants executive permissions to owner and super admin`() {
        listOf(UserRole.SUPER_ADMIN, UserRole.OWNER).forEach { role ->
            val caps = RoleAccessControl.capabilitiesFor(role)
            assertTrue(caps.canAccessAdminFunctions)
            assertTrue(caps.canViewAuditLogs)
            assertTrue(caps.canSwitchBranches)
            assertTrue(caps.canViewFinancialKpis)
            assertTrue(RoleAccessControl.canAccessModule(role, AppModule.AUDIT_LOGS))
            assertTrue(RoleAccessControl.canAccessModule(role, AppModule.BRANCH_MANAGEMENT))
        }
    }

    @Test
    fun `least privilege fallback defaults unknown role code to cashier`() {
        assertEquals(UserRole.CASHIER, UserRole.fromCode(null))
        assertEquals(UserRole.CASHIER, UserRole.fromCode("UNKNOWN_ROLE"))
        assertEquals(UserRole.SUPER_ADMIN, UserRole.fromCode("SUPER_ADMIN"))
    }

    @Test
    fun `room seeder populates branches sample dashboard and audit logs`() = runTest {
        val seeder = DatabaseSeeder(database)
        seeder.ensureSeeded()

        val branches = database.branchDao().observeBranches().first()
        assertEquals(2, branches.size)
        assertTrue(branches.any { it.isMainBranch && it.branchId == DatabaseSeeder.MAIN_BRANCH_ID })

        val mainSnapshot = database.dashboardDao().observeSnapshot(DatabaseSeeder.MAIN_BRANCH_ID).first()
        assertNotNull(mainSnapshot)
        assertTrue(mainSnapshot!!.isSampleData)
        assertTrue(mainSnapshot.todaySales > 0.0)

        database.auditLogDao().insertLog(
            AuditLogEntity(
                branchId = DatabaseSeeder.MAIN_BRANCH_ID,
                actorUid = "uid_123",
                actorEmail = "manager@aacrave.local",
                actorRole = "Branch Manager",
                action = "TEST_EVENT",
                details = "Verified Phase 1 Room persistence"
            )
        )

        val logs = database.auditLogDao().observeRecentLogs().first()
        assertEquals(1, logs.size)
        assertEquals("TEST_EVENT", logs.first().action)
    }
}
