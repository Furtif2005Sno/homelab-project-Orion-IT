package com.homelab.app.ui.nginxpm.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.remote.dto.nginxpm.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpmRedirectionHostForm(
    existing: NpmRedirectionHost? = null,
    certificates: List<NpmCertificate>,
    accessLists: List<NpmAccessList>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (NpmRedirectionHostRequest) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var domainNames by remember { mutableStateOf(existing?.domainNames ?: emptyList()) }
    var forwardHttpCode by remember { mutableIntStateOf(existing?.forwardHttpCode ?: 301) }
    var forwardScheme by remember { mutableStateOf(existing?.forwardScheme ?: "http") }
    var forwardDomainName by remember { mutableStateOf(existing?.forwardDomainName ?: "") }
    var preservePath by remember { mutableStateOf(existing?.preservePath == 1) }
    var certificateId by remember { mutableIntStateOf(existing?.certificateId ?: 0) }
    var accessListId by remember { mutableIntStateOf(existing?.accessListId ?: 0) }
    var sslForced by remember { mutableStateOf(existing?.sslForced == 1) }
    var blockExploits by remember { mutableStateOf(existing?.blockExploits == 1) }
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
                text = stringResource(if (existing != null) R.string.npm_edit_redirection else R.string.npm_add_redirection),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            DomainNamesInput(domains = domainNames, onDomainsChanged = { domainNames = it })

            HttpCodePicker(selected = forwardHttpCode, onSelected = { forwardHttpCode = it })

            SchemeDropdown(selected = forwardScheme, onSelected = { forwardScheme = it })

            OutlinedTextField(
                value = forwardDomainName,
                onValueChange = { forwardDomainName = it },
                label = { Text(stringResource(R.string.npm_forward_domain)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
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

            FormSwitch(stringResource(R.string.npm_preserve_path), preservePath) { preservePath = it }
            FormSwitch(stringResource(R.string.npm_ssl_forced), sslForced) { sslForced = it }
            FormSwitch(stringResource(R.string.npm_block_exploits), blockExploits) { blockExploits = it }
            FormSwitch(stringResource(R.string.npm_http2), http2) { http2 = it }
            FormSwitch(stringResource(R.string.npm_hsts), hsts) { hsts = it }
            if (hsts) {
                FormSwitch(stringResource(R.string.npm_hsts_subdomains), hstsSubdomains) { hstsSubdomains = it }
            }
            FormSwitch(stringResource(R.string.npm_enabled), enabled) { enabled = it }

            Button(
                onClick = {
                    onSave(
                        NpmRedirectionHostRequest(
                            domainNames = domainNames,
                            forwardHttpCode = forwardHttpCode,
                            forwardScheme = forwardScheme,
                            forwardDomainName = forwardDomainName,
                            preservePath = if (preservePath) 1 else 0,
                            certificateId = certificateId,
                            accessListId = accessListId,
                            sslForced = if (sslForced) 1 else 0,
                            blockExploits = if (blockExploits) 1 else 0,
                            http2Support = if (http2) 1 else 0,
                            hstsEnabled = if (hsts) 1 else 0,
                            hstsSubdomains = if (hstsSubdomains) 1 else 0,
                            enabled = if (enabled) 1 else 0
                        )
                    )
                },
                enabled = domainNames.isNotEmpty() && forwardDomainName.isNotBlank() && !isLoading,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HttpCodePicker(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(301 to "301 – Permanent", 302 to "302 – Temporary")

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = options.find { it.first == selected }?.second ?: "$selected",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.npm_forward_http_code)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in options) {
                val (code, label) = option
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelected(code); expanded = false }
                )
            }
        }
    }
}
