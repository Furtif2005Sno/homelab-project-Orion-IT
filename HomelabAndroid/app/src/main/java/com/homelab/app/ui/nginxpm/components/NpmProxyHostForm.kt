package com.homelab.app.ui.nginxpm.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.remote.dto.nginxpm.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpmProxyHostForm(
    existing: NpmProxyHost? = null,
    certificates: List<NpmCertificate>,
    accessLists: List<NpmAccessList>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (NpmProxyHostRequest) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var domainNames by remember { mutableStateOf(existing?.domainNames ?: emptyList()) }
    var forwardScheme by remember { mutableStateOf(existing?.forwardScheme ?: "http") }
    var forwardHost by remember { mutableStateOf(existing?.forwardHost ?: "") }
    var forwardPort by remember { mutableStateOf(existing?.forwardPort?.toString() ?: "80") }
    var certificateId by remember { mutableIntStateOf(existing?.certificateId ?: 0) }
    var accessListId by remember { mutableIntStateOf(existing?.accessListId ?: 0) }
    var sslForced by remember { mutableStateOf(existing?.sslForced == 1) }
    var cachingEnabled by remember { mutableStateOf(existing?.cachingEnabled == 1) }
    var blockExploits by remember { mutableStateOf(existing?.blockExploits == 1) }
    var websocket by remember { mutableStateOf(existing?.allowWebsocketUpgrade == 1) }
    var http2 by remember { mutableStateOf(existing?.http2Support == 1) }
    var hsts by remember { mutableStateOf(existing?.hstsEnabled == 1) }
    var hstsSubdomains by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(existing?.isEnabled ?: true) }
    var advancedConfig by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(if (existing != null) R.string.npm_edit_proxy_host else R.string.npm_add_proxy_host),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            DomainNamesInput(domains = domainNames, onDomainsChanged = { domainNames = it })

            SchemeDropdown(selected = forwardScheme, onSelected = { forwardScheme = it })

            OutlinedTextField(
                value = forwardHost,
                onValueChange = { forwardHost = it },
                label = { Text(stringResource(R.string.npm_forward_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            PortTextField(
                value = forwardPort,
                onValueChange = { forwardPort = it },
                label = stringResource(R.string.npm_forward_port)
            )

            CertificateDropdown(
                certificates = certificates,
                selectedId = certificateId,
                onSelected = { certificateId = it }
            )

            AccessListDropdown(
                accessLists = accessLists,
                selectedId = accessListId,
                onSelected = { accessListId = it }
            )

            HorizontalDivider()

            FormSwitch(stringResource(R.string.npm_ssl_forced), sslForced) { sslForced = it }
            FormSwitch(stringResource(R.string.npm_caching), cachingEnabled) { cachingEnabled = it }
            FormSwitch(stringResource(R.string.npm_block_exploits), blockExploits) { blockExploits = it }
            FormSwitch(stringResource(R.string.npm_websocket), websocket) { websocket = it }
            FormSwitch(stringResource(R.string.npm_http2), http2) { http2 = it }
            FormSwitch(stringResource(R.string.npm_hsts), hsts) { hsts = it }
            if (hsts) {
                FormSwitch(stringResource(R.string.npm_hsts_subdomains), hstsSubdomains) { hstsSubdomains = it }
            }
            HorizontalDivider()

            OutlinedTextField(
                value = advancedConfig,
                onValueChange = { advancedConfig = it },
                label = { Text(stringResource(R.string.npm_advanced_config)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    onSave(
                        NpmProxyHostRequest(
                            domainNames = domainNames,
                            forwardScheme = forwardScheme,
                            forwardHost = forwardHost,
                            forwardPort = forwardPort.toIntOrNull() ?: 80,
                            certificateId = certificateId,
                            accessListId = accessListId,
                            sslForced = if (sslForced) 1 else 0,
                            cachingEnabled = if (cachingEnabled) 1 else 0,
                            blockExploits = if (blockExploits) 1 else 0,
                            allowWebsocketUpgrade = if (websocket) 1 else 0,
                            http2Support = if (http2) 1 else 0,
                            hstsEnabled = if (hsts) 1 else 0,
                            hstsSubdomains = if (hstsSubdomains) 1 else 0,
                            enabled = if (enabled) 1 else 0,
                            advancedConfig = advancedConfig
                        )
                    )
                },
                enabled = domainNames.isNotEmpty() && forwardHost.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
