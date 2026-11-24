package com.example.printing

import com.example.domain.model.CustomerInvoiceDocument
import com.example.domain.model.InvoicePaperSize
import com.example.domain.model.KitchenKotDocument
import com.example.domain.model.KotPaperWidth

enum class PrinterConnectionState {
    NOT_CONFIGURED,
    READY
}

data class ThermalPrinterStatus(
    val state: PrinterConnectionState,
    val driverName: String,
    val setupGuidance: String
)

interface ThermalPrinterService {
    fun getStatus(): ThermalPrinterStatus
    suspend fun printCustomerInvoice(document: CustomerInvoiceDocument, paperSize: InvoicePaperSize): Result<String>
    suspend fun printKitchenKot(document: KitchenKotDocument, paperWidth: KotPaperWidth): Result<String>
}

/**
 * Honest thermal printer adapter when no Bluetooth/Network ESC/POS hardware SDK is paired/configured.
 * Never claims thermal hardware printing succeeded when no physical printer driver is configured.
 */
class UnconfiguredThermalPrinterService : ThermalPrinterService {
    override fun getStatus(): ThermalPrinterStatus = ThermalPrinterStatus(
        state = PrinterConnectionState.NOT_CONFIGURED,
        driverName = "No ESC/POS Hardware Printer Configured",
        setupGuidance = "Pair a Bluetooth or TCP/IP ESC/POS thermal printer SDK in Phase 7, or use Android System Print / PDF Export right now."
    )

    override suspend fun printCustomerInvoice(
        document: CustomerInvoiceDocument,
        paperSize: InvoicePaperSize
    ): Result<String> {
        return Result.failure(
            IllegalStateException(
                "Direct thermal printer is not configured for ${paperSize.label}. Use 'System Print Preview' or 'Export PDF' instead."
            )
        )
    }

    override suspend fun printKitchenKot(
        document: KitchenKotDocument,
        paperWidth: KotPaperWidth
    ): Result<String> {
        return Result.failure(
            IllegalStateException(
                "Direct kitchen thermal printer is not configured for ${paperWidth.label}. Use 'Kitchen Print Preview' (Android Print) or 'Export KOT PDF' instead."
            )
        )
    }
}
