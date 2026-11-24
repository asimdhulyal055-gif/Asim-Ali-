package com.example.domain.model

enum class ModuleSection(val title: String) {
    PHASE_1_CORE("Core Workspace"),
    OPERATIONS_ROADMAP("Operations, POS & Loyalty"),
    INVENTORY_SUPPLY("Catalog, Inventory & Supply"),
    PEOPLE_FINANCE("People, HR & Accounting"),
    GOVERNANCE("Governance & System")
}

enum class AppModule(
    val route: String,
    val title: String,
    val section: ModuleSection,
    val phaseNumber: Int,
    val isImplementedInCurrentPhase: Boolean
) {
    // Active Phase 1 Modules
    DASHBOARD("dashboard", "Dashboard", ModuleSection.PHASE_1_CORE, 1, true),
    USER_PROFILE("profile", "User Profile & Session", ModuleSection.PHASE_1_CORE, 1, true),
    BRANCH_MANAGEMENT("branches", "Branch Management", ModuleSection.GOVERNANCE, 1, true),
    AUDIT_LOGS("audit_logs", "Audit Logs", ModuleSection.GOVERNANCE, 1, true),
    SETTINGS("settings", "Settings & Firebase", ModuleSection.GOVERNANCE, 1, true),

    // Phase 2 Modules (POS, Billing, Separate Invoice & KOT, Orders, Cash Closing)
    POS("pos", "POS Terminal", ModuleSection.OPERATIONS_ROADMAP, 2, true),
    BILLING("billing", "Billing & Payments", ModuleSection.OPERATIONS_ROADMAP, 2, true),
    CUSTOMER_INVOICES("customer_invoices", "Customer Invoices", ModuleSection.OPERATIONS_ROADMAP, 2, true),
    KITCHEN_KOT("kitchen_kot", "Kitchen KOT", ModuleSection.OPERATIONS_ROADMAP, 2, true),
    ORDERS("orders", "Orders History", ModuleSection.OPERATIONS_ROADMAP, 2, true),
    CASH_CLOSING("cash_closing", "Daily Cash Closing", ModuleSection.OPERATIONS_ROADMAP, 2, true),

    // Phase 3 Modules (Kitchen Display System, Menu, Recipes, Inventory)
    KITCHEN_DISPLAY("kitchen_display", "Kitchen Display (KDS)", ModuleSection.OPERATIONS_ROADMAP, 3, true),
    MENU("menu", "Menu Management", ModuleSection.INVENTORY_SUPPLY, 3, true),
    RECIPES("recipes", "Recipes & Costing", ModuleSection.INVENTORY_SUPPLY, 3, true),
    INVENTORY("inventory", "Inventory & Stock", ModuleSection.INVENTORY_SUPPLY, 3, true),

    // Phase 4 Modules (Purchases, Suppliers, Customers & Loyalty, Expenses)
    PURCHASES("purchases", "Purchases & Receiving", ModuleSection.INVENTORY_SUPPLY, 4, true),
    SUPPLIERS("suppliers", "Suppliers & Ledgers", ModuleSection.INVENTORY_SUPPLY, 4, true),
    CUSTOMERS("customers", "Customers & Loyalty", ModuleSection.OPERATIONS_ROADMAP, 4, true),
    LOYALTY("loyalty", "Smart Loyalty & Rewards", ModuleSection.OPERATIONS_ROADMAP, 7, true),
    EXPENSES("expenses", "Expense Management", ModuleSection.PEOPLE_FINANCE, 4, true),

    // Phase 5 Modules (Staff, Attendance, Shifts, Leave Management, Payroll)
    STAFF("staff", "Staff Directory", ModuleSection.PEOPLE_FINANCE, 5, true),
    ATTENDANCE("attendance", "Attendance", ModuleSection.PEOPLE_FINANCE, 5, true),
    SHIFTS("shifts", "Shifts", ModuleSection.PEOPLE_FINANCE, 5, true),
    LEAVE_MANAGEMENT("leave_management", "Leave Management", ModuleSection.PEOPLE_FINANCE, 5, true),
    PAYROLL("payroll", "Payroll", ModuleSection.PEOPLE_FINANCE, 5, true),

    // Phase 6 Modules (Accounting, Financial Reports & Multi-Branch Management)
    ACCOUNTING("accounting", "Accounting & Ledgers", ModuleSection.PEOPLE_FINANCE, 6, true),
    REPORTS("reports", "Financial Reports", ModuleSection.PEOPLE_FINANCE, 6, true),

    // Phase 7 Modules (Cloud Sync, Backup, Security Permissions & Notifications)
    USER_PERMISSIONS("user_permissions", "User Permissions & Security", ModuleSection.GOVERNANCE, 7, true),
    NOTIFICATIONS("notifications", "Notifications & Alerts", ModuleSection.GOVERNANCE, 7, true),
    BACKUP_RESTORE("backup_restore", "Cloud Sync & Backup", ModuleSection.GOVERNANCE, 7, true)
}
