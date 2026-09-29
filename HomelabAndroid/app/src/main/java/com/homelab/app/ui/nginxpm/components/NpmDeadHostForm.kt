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
fun NpmDeadHostForm(
    existing: NpmDeadHost? = null,
    certificates: List<NpmCertificate>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (NpmDeadHostRequest) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var domainNames by remember { mutableStateOf(existing?.domainNames ?: emptyList()) }
    var certificateId by remember { mutableIntStateOf(existing?.certificateId ?: 0) }
    var sslForced by remember { mutableStateOf(existing?.sslForced == 1) }
    var http2 by remember { mutableStateOf(existing?.http2Support == 1) }
    var hsts by remember { mutableStateOf(existing?.hstsEnabled == 1) }
    var hstsSubdomains by remember { mutableStateOf(existing?.hstsSubdomains == 1) }
    var enabled by remember { mutableStateOf(existing?.isEnabled ?: true) }

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
                text = stringResource(if (existing != null) R.string.npm_edit_dead_host else R.string.npm_add_dead_host),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            DomainNamesInput(domains = domainNames, onDomainsChanged = { domainNames = it })

            CertificateDropdown(
                certificates = certificates,
                selectedId = certificateId,
                onSelected = { certificateId = it }
            )

            HorizontalDivider()

            FormSwitch(stringResource(R.string.npm_ssl_forced), sslForced) { sslForced = it }
            FormSwitch(stringResource(R.string.npm_http2), http2) { http2 = it }
            FormSwitch(stringResource(R.string.npm_hsts), hsts) { hsts = it }
            if (hsts) {
                FormSwitch(stringResource(R.string.npm_hsts_subdomains), hstsSubdomains) { hstsSubdomains = it }
            }
            FormSwitch(stringResource(R.string.npm_enabled), enabled) { enabled = it }

            Button(
                onClick = {
                    onSave(
                        NpmDeadHostRequest(
                            domainNames = domainNames,
                            certificateId = certificateId,
                            sslForced = if (sslForced) 1 else 0,
                            http2Support = if (http2) 1 else 0,
                            hstsEnabled = if (hsts) 1 else 0,
                            hstsSubdomains = if (hstsSubdomains) 1 else 0,
                            enabled = if (enabled) 1 else 0
                        )
                    )
                },
                enabled = domainNames.isNotEmpty() && !isLoading,
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
