package com.example.ui.components

import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.brand.BrandLogoState
import com.example.data.brand.LogoFormat
import com.example.ui.theme.CraveGold
import com.example.ui.theme.CraveGoldBright
import com.example.ui.theme.CraveGoldContainer
import com.example.ui.theme.ObsidianSurface
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/**
 * Displays the owner-supplied AA CRAVE logo (PNG, JPG, WEBP, SVG) preserving proportions.
 * If no original logo file is supplied yet, renders a clearly labelled Temporary Placeholder.
 */
@Composable
fun AaCraveBrandLogo(
    size: Dp = 64.dp,
    logoState: BrandLogoState = BrandLogoState(hasOwnerLogo = false),
    showPlaceholderLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val rasterBitmap = remember(logoState.filePath, logoState.format) {
        if (logoState.hasOwnerLogo && logoState.format != LogoFormat.SVG && !logoState.filePath.isNullOrBlank()) {
            val file = File(logoState.filePath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        } else {
            null
        }
    }

    val svgMarkup = remember(logoState.filePath, logoState.format) {
        if (logoState.hasOwnerLogo && logoState.format == LogoFormat.SVG && !logoState.filePath.isNullOrBlank()) {
            val file = File(logoState.filePath)
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        } else {
            null
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(size)
                .border(1.5.dp, CraveGold.copy(alpha = 0.75f), RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    rasterBitmap != null -> {
                        Image(
                            bitmap = rasterBitmap,
                            contentDescription = "Original AA CRAVE Brand Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    svgMarkup != null -> {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = false
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                }
                            },
                            update = { webView ->
                                val wrapped = "<html><body style='margin:0;padding:0;display:flex;align-items:center;justify-content:center;background:transparent;'>$svgMarkup</body></html>"
                                webView.loadDataWithBaseURL(null, wrapped, "text/html", "utf-8", null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        // Clearly labelled temporary placeholder (not final AI logo)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CraveGoldContainer.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = "AA",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = CraveGoldBright
                            )
                            Text(
                                text = "CRAVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (!logoState.hasOwnerLogo && showPlaceholderLabel) {
            Surface(
                shape = RoundedCornerShape(50),
                color = CraveGoldContainer,
                border = BorderStroke(0.5.dp, CraveGold.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "TEMP LOGO",
                    style = MaterialTheme.typography.labelSmall,
                    color = CraveGoldBright,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (badgeText != null) {
            StatusPill(
                text = badgeText,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

fun formatCurrency(amount: Double): String =
    com.example.domain.model.PkrCurrencyFormatter.formatMajorAmount(amount)
