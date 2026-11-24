package com.example.domain.model

enum class UserRole(
    val displayName: String,
    val code: String,
    val department: String,
    val privilegeLevel: Int
) {
    SUPER_ADMIN(
        displayName = "Super Admin",
        code = "SUPER_ADMIN",
        department = "System & Governance",
        privilegeLevel = 100
    ),
    OWNER(
        displayName = "Owner",
        code = "OWNER",
        department = "Executive",
        privilegeLevel = 90
    ),
    ADMIN(
        displayName = "Admin",
        code = "ADMIN",
        department = "Administration & Operations",
        privilegeLevel = 85
    ),
    BRANCH_MANAGER(
        displayName = "Branch Manager",
        code = "BRANCH_MANAGER",
        department = "Branch Operations",
        privilegeLevel = 70
    ),
    CASHIER(
        displayName = "Cashier",
        code = "CASHIER",
        department = "Front of House / POS",
        privilegeLevel = 30
    ),
    KITCHEN_STAFF(
        displayName = "Kitchen Staff",
        code = "KITCHEN_STAFF",
        department = "Culinary / KDS",
        privilegeLevel = 30
    ),
    HR(
        displayName = "HR",
        code = "HR",
        department = "Human Resources & Payroll",
        privilegeLevel = 50
    ),
    INVENTORY_MANAGER(
        displayName = "Inventory Manager",
        code = "INVENTORY_MANAGER",
        department = "Supply Chain & Stock",
        privilegeLevel = 50
    ),
    PURCHASE_STAFF(
        displayName = "Purchase Staff",
        code = "PURCHASE_STAFF",
        department = "Procurement & Purchasing",
        privilegeLevel = 45
    );

    companion object {
        fun fromCode(code: String?): UserRole {
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) ||
                    it.name.equals(code, ignoreCase = true)
            } ?: CASHIER
        }
    }
}
