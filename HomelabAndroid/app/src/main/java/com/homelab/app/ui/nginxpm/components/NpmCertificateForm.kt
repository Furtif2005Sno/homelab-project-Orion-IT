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
import com.homelab.app.data.remote.dto.nginxpm.NpmCertificateRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmCertificateRequestMeta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpmCertificateForm(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (NpmCertificateRequest) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var niceName by remember { mutableStateOf("") }
    var domainNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var email by remember { mutableStateOf("") }
    var dnsChallenge by remember { mutableStateOf(false) }
    var agreeTos by remember { mutableStateOf(false) }

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
                text = stringResource(R.string.npm_add_certificate),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            OutlinedTextField(
                value = niceName,
                onValueChange = { niceName = it },
                label = { Text(stringResource(R.string.npm_nice_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            DomainNamesInput(domains = domainNames, onDomainsChanged = { domainNames = it })

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.npm_letsencrypt_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider()

            FormSwitch(stringResource(R.string.npm_dns_challenge), dnsChallenge) { dnsChallenge = it }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.npm_letsencrypt_agree),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(checked = agreeTos, onCheckedChange = { agreeTos = it })
            }

            Button(
                onClick = {
                    onSave(
                        NpmCertificateRequest(
                            niceName = niceName,
                            domainNames = domainNames,
                            meta = NpmCertificateRequestMeta(
                                letsencryptAgree = agreeTos,
                                letsencryptEmail = email,
                                dnsChallenge = dnsChallenge
                            )
                        )
                    )
                },
                enabled = domainNames.isNotEmpty() && email.isNotBlank() && agreeTos && !isLoading,
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
