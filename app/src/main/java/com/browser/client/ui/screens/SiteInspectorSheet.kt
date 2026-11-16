package com.browser.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browser.client.data.local.ClearanceEntity
import com.browser.client.engine.DomainClearanceStatus
import com.browser.client.engine.DomainUtils
import com.browser.client.ui.BrowserTabState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInspectorSheet(
    tab: BrowserTabState,
    clearanceEntity: ClearanceEntity?,
    onDismiss: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenPasswordManager: () -> Unit,
    onRevokeAndReload: (String) -> Unit,
    onShredIncognito: () -> Unit,
    onOpenVault: () -> Unit
) {
    val host = DomainUtils.extractHost(tab.url).ifBlank { tab.activeDomain }
    val isHttps = tab.url.startsWith("https://")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isHttps) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Public,
                        contentDescription = null,
                        tint = if (isHttps) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = host.ifBlank { "Current Page" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isHttps) "Encrypted HTTPS connection" else "Unencrypted HTTP connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Cloudflare Clearance / Hardened Incognito Status Card
            if (tab.isIncognito) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Hardened Incognito Active",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        InspectorDetailRow(label = "Screen Shield", value = "FLAG_SECURE Active")
                        InspectorDetailRow(label = "3rd-Party Cookies", value = "Blocked")
                        InspectorDetailRow(label = "Trackers & Beacons", value = "Blocked + DNT/GPC")
                        InspectorDetailRow(label = "History & Disk Cache", value = "Disabled (Zero Trace)")
                        InspectorDetailRow(label = "Clearance Vault", value = "Isolated (No Read/Write)")
                        InspectorDetailRow(label = "Mixed Content", value = "Strictly Blocked")
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        when (tab.clearanceStatus) {
                            DomainClearanceStatus.VERIFYING_1X -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            DomainClearanceStatus.VERIFIED_1X,
                            DomainClearanceStatus.RESTORED_FROM_VAULT -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                            DomainClearanceStatus.NONE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = when (tab.clearanceStatus) {
                                    DomainClearanceStatus.VERIFYING_1X -> Icons.Rounded.HourglassTop
                                    DomainClearanceStatus.VERIFIED_1X,
                                    DomainClearanceStatus.RESTORED_FROM_VAULT -> Icons.Rounded.VerifiedUser
                                    DomainClearanceStatus.NONE -> Icons.Rounded.Shield
                                },
                                contentDescription = null,
                                tint = when (tab.clearanceStatus) {
                                    DomainClearanceStatus.VERIFYING_1X -> MaterialTheme.colorScheme.primary
                                    DomainClearanceStatus.VERIFIED_1X,
                                    DomainClearanceStatus.RESTORED_FROM_VAULT -> MaterialTheme.colorScheme.secondary
                                    DomainClearanceStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = when (tab.clearanceStatus) {
                                    DomainClearanceStatus.VERIFYING_1X -> "Cloudflare Challenge Active"
                                    DomainClearanceStatus.VERIFIED_1X -> "1x Verified • Clearance Saved"
                                    DomainClearanceStatus.RESTORED_FROM_VAULT -> "Restored From Clearance Vault"
                                    DomainClearanceStatus.NONE -> "No Cloudflare Clearance Needed"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (clearanceEntity != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            InspectorDetailRow(
                                label = "cf_clearance",
                                value = DomainUtils.maskTokenPreview(clearanceEntity.cfClearanceToken),
                                mono = true
                            )
                            InspectorDetailRow(
                                label = "Verified",
                                value = DomainUtils.formatTimestamp(clearanceEntity.verifiedAt)
                            )
                            InspectorDetailRow(
                                label = "Times Reused",
                                value = "${clearanceEntity.reuseCount}x (no re-verify)"
                            )
                            InspectorDetailRow(
                                label = "Locked Profile",
                                value = if (clearanceEntity.isDesktopMode) "Desktop Chrome" else "Clean Mobile Chrome"
                            )
                        } else if (tab.clearanceStatus == DomainClearanceStatus.VERIFYING_1X) {
                            Text(
                                text = "Cloudflare verification is running on this page. Vortex will save the clearance cookie once completed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Standard website connection — no Cloudflare verification detected on this page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Desktop Site Toggle Row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DesktopWindows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Request Desktop Site",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Switch User-Agent for this tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = tab.isDesktopMode,
                        onCheckedChange = {
                            onToggleDesktopMode()
                            onDismiss()
                        }
                    )
                }
            }

            // Google Password Manager / Passkeys Row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = {
                    onDismiss()
                    onOpenPasswordManager()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inspector_password_manager_row")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Password Manager",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Manage passkeys & saved credentials",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tab.isIncognito) {
                    Button(
                        onClick = {
                            onDismiss()
                            onShredIncognito()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inspector_shred_incognito_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shred Incognito Session")
                    }
                } else {
                    if (clearanceEntity != null) {
                        OutlinedButton(
                            onClick = {
                                onRevokeAndReload(clearanceEntity.domain)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("revoke_site_clearance_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset 1x")
                        }
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenVault()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inspector_open_vault_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clearance Vault")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun InspectorDetailRow(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (mono) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
