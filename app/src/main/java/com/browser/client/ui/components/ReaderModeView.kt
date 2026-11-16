package com.browser.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.client.engine.DomainUtils
import com.browser.client.engine.ReaderArticle
import com.browser.client.engine.ReaderBlock
import com.browser.client.engine.ReaderBlockType

@Composable
fun ReaderModeView(
    article: ReaderArticle,
    url: String,
    fontScale: Float,
    isSepiaPaper: Boolean,
    isIncognito: Boolean,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onToggleSepia: () -> Unit,
    onExitReaderMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageBackground = if (isSepiaPaper && !isIncognito) {
        Color(0xFFF6EFDF)
    } else {
        MaterialTheme.colorScheme.background
    }
    val pageTextPrimary = if (isSepiaPaper && !isIncognito) {
        Color(0xFF2B2114)
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    val pageTextSecondary = if (isSepiaPaper && !isIncognito) {
        Color(0xFF6B5B45)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val cardSurface = if (isSepiaPaper && !isIncognito) {
        Color(0xFFEDE2CA)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .testTag("reader_mode_view")
    ) {
        // Distraction-free Reader Controls Header
        Surface(
            color = if (isSepiaPaper && !isIncognito) Color(0xFFEDE2CA) else MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Reader Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = pageTextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onDecreaseFont,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("reader_font_decrease")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextDecrease,
                            contentDescription = "Decrease text size",
                            tint = pageTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onIncreaseFont,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("reader_font_increase")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextIncrease,
                            contentDescription = "Increase text size",
                            tint = pageTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (!isIncognito) {
                        TextButton(
                            onClick = onToggleSepia,
                            modifier = Modifier.testTag("reader_sepia_toggle")
                        ) {
                            Text(
                                text = if (isSepiaPaper) "Theme" else "Sepia",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    IconButton(
                        onClick = onExitReaderMode,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("exit_reader_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit Reader Mode",
                            tint = pageTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (article.blocks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = pageTextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No article text found on this page",
                        style = MaterialTheme.typography.titleMedium,
                        color = pageTextPrimary
                    )
                    Text(
                        text = "Some interactive web apps or media-only pages don't have long-form text to extract.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pageTextSecondary
                    )
                    Button(onClick = onExitReaderMode) {
                        Text("Return to Web View")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = article.siteName.ifBlank {
                                            DomainUtils.extractHost(url).ifBlank { "Article" }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = pageTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${article.readingTimeMinutes} min read",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pageTextSecondary
                                    )
                                }
                            }

                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = (26f * fontScale).sp,
                                    lineHeight = (33f * fontScale).sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = pageTextPrimary
                            )

                            if (article.byline.isNotBlank()) {
                                Text(
                                    text = article.byline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pageTextSecondary
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(top = 6.dp),
                                color = pageTextSecondary.copy(alpha = 0.22f)
                            )
                        }
                    }

                    items(article.blocks) { block ->
                        ReaderBlockItem(
                            block = block,
                            fontScale = fontScale,
                            textPrimary = pageTextPrimary,
                            textSecondary = pageTextSecondary,
                            cardSurface = cardSurface
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBlockItem(
    block: ReaderBlock,
    fontScale: Float,
    textPrimary: Color,
    textSecondary: Color,
    cardSurface: Color
) {
    when (block.type) {
        ReaderBlockType.HEADING -> {
            val baseSize = if (block.level <= 2) 20f else 17.5f
            Text(
                text = block.text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (baseSize * fontScale).sp,
                    lineHeight = ((baseSize + 6f) * fontScale).sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = textPrimary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        ReaderBlockType.PARAGRAPH -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (17f * fontScale).sp,
                    lineHeight = (27.5f * fontScale).sp
                ),
                color = textPrimary
            )
        }
        ReaderBlockType.QUOTE -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (16f * fontScale).sp,
                            lineHeight = (25f * fontScale).sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = textSecondary
                    )
                }
            }
        }
        ReaderBlockType.LIST_ITEM -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (17f * fontScale).sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (16.5f * fontScale).sp,
                        lineHeight = (26f * fontScale).sp
                    ),
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ReaderBlockType.CODE -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cardSurface,
                border = BorderStroke(1.dp, textSecondary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (13.5f * fontScale).sp,
                        lineHeight = (20f * fontScale).sp
                    ),
                    color = textPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
