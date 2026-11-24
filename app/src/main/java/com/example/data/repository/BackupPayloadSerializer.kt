package com.example.data.repository

import com.example.data.local.*
import org.json.JSONArray
import org.json.JSONObject

object BackupPayloadSerializer {

    suspend fun exportDatabaseTablesJson(database: AaCraveDatabase): Pair<JSONObject, Map<String, Int>> {
        val db = database.openHelper.readableDatabase
        val tablesObj = JSONObject()
        val counts = linkedMapOf<String, Int>()
        val tableNames = listOf(
            "branches",
            "user_profiles",
            "product_categories",
            "products",
            "product_variations",
            "product_addons",
            "customers",
            "pos_orders",
            "order_items",
            "kot_tickets",
            "daily_cash_closings",
            "inventory_items",
            "recipes",
            "recipe_ingredients",
            "recipe_versions",
            "stock_movements",
            "suppliers",
            "purchase_orders",
            "purchase_items",
            "purchase_receivings",
            "purchase_returns",
            "supplier_payments",
            "loyalty_config",
            "loyalty_rewards",
            "loyalty_transactions",
            "customer_feedback",
            "expenses",
            "staff_profiles",
            "staff_documents",
            "shift_definitions",
            "shift_assignments",
            "attendance_records",
            "leave_requests",
            "payroll_records",
            "hr_permission_policies",
            "chart_of_accounts",
            "journal_entries",
            "journal_lines",
            "cashbook_entries",
            "role_permission_policies",
            "loyalty_program_settings",
            "loyalty_cards",
            "loyalty_stamp_transactions",
            "loyalty_reward_vouchers",
            "loyalty_redemptions",
            "loyalty_audit_logs"
        )

        tableNames.forEach { table ->
            val arr = JSONArray()
            db.query("SELECT * FROM `$table`").use { cursor ->
                val colCount = cursor.columnCount
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until colCount) {
                        val colName = cursor.getColumnName(i)
                        when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> row.put(colName, JSONObject.NULL)
                            android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(colName, cursor.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(colName, cursor.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_STRING -> row.put(colName, cursor.getString(i))
                            else -> row.put(colName, cursor.getString(i))
                        }
                    }
                    arr.put(row)
                }
            }
            tablesObj.put(table, arr)
            counts[table] = arr.length()
        }
        return tablesObj to counts
    }

    fun restoreTablesFromJson(database: AaCraveDatabase, tablesObj: JSONObject): Int {
        val sqLiteDb = database.openHelper.writableDatabase
        var totalInserted = 0
        val tableNames = listOf(
            "branches",
            "user_profiles",
            "product_categories",
            "products",
            "product_variations",
            "product_addons",
            "customers",
            "pos_orders",
            "order_items",
            "kot_tickets",
            "daily_cash_closings",
            "inventory_items",
            "recipes",
            "recipe_ingredients",
            "recipe_versions",
            "stock_movements",
            "suppliers",
            "purchase_orders",
            "purchase_items",
            "purchase_receivings",
            "purchase_returns",
            "supplier_payments",
            "loyalty_config",
            "loyalty_rewards",
            "loyalty_transactions",
            "customer_feedback",
            "expenses",
            "staff_profiles",
            "staff_documents",
            "shift_definitions",
            "shift_assignments",
            "attendance_records",
            "leave_requests",
            "payroll_records",
            "hr_permission_policies",
            "chart_of_accounts",
            "journal_entries",
            "journal_lines",
            "cashbook_entries",
            "role_permission_policies",
            "loyalty_program_settings",
            "loyalty_cards",
            "loyalty_stamp_transactions",
            "loyalty_reward_vouchers",
            "loyalty_redemptions",
            "loyalty_audit_logs"
        )

        sqLiteDb.beginTransaction()
        try {
            sqLiteDb.execSQL("PRAGMA defer_foreign_keys = ON")
            tableNames.forEach { table ->
                val arr = tablesObj.optJSONArray(table) ?: return@forEach
                sqLiteDb.execSQL("DELETE FROM `$table`")
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    val values = android.content.ContentValues()
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = row.get(k)
                        when (v) {
                            JSONObject.NULL -> values.putNull(k)
                            is Boolean -> values.put(k, if (v) 1 else 0)
                            is Int -> values.put(k, v)
                            is Long -> values.put(k, v)
                            is Double -> values.put(k, v)
                            is Float -> values.put(k, v.toDouble())
                            is Number -> values.put(k, v.toLong())
                            else -> values.put(k, v.toString())
                        }
                    }
                    sqLiteDb.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
                    totalInserted++
                }
            }
            sqLiteDb.setTransactionSuccessful()
        } finally {
            sqLiteDb.endTransaction()
        }
        return totalInserted
    }
}
