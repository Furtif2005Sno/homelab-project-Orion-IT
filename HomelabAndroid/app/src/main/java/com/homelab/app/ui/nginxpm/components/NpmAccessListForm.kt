package com.homelab.app.ui.nginxpm.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.InputChip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import com.homelab.app.R
import com.homelab.app.data.remote.dto.nginxpm.NpmAccessList
import com.homelab.app.data.remote.dto.nginxpm.NpmAccessListClient
import com.homelab.app.data.remote.dto.nginxpm.NpmAccessListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpmAccessListForm(
    editing: NpmAccessList?,
    onDismiss: () -> Unit,
    onSave: (name: String, items: List<NpmAccessListItem>, clients: List<NpmAccessListClient>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(editing?.id) { mutableStateOf(editing?.name ?: "") }
    var items by remember(editing?.id) { mutableStateOf(editing?.items ?: emptyList()) }
    var clients by remember(editing?.id) { mutableStateOf(editing?.clients ?: emptyList()) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var clientAddress by remember { mutableStateOf("") }
    var clientDirective by remember { mutableStateOf("allow") }

    LaunchedEffect(editing?.id) {
        username = ""
        password = ""
        clientAddress = ""
        clientDirective = "allow"
    }

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
                text = stringResource(
                    if (editing != null) R.string.npm_edit_access_list else R.string.npm_add_access_list
                ),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.npm_access_list)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider()

            // Users section
            Text(
                text = stringResource(R.string.npm_access_list_users),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.npm_access_list_username)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.npm_access_list_password)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                FilledIconButton(
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            items = items + NpmAccessListItem(username = username, password = password)
                            username = ""
                            password = ""
                        }
                    },
                    enabled = username.isNotBlank() && password.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }

            if (items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (item in items) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.username, style = MaterialTheme.typography.bodyMedium)
                                if (item.password.isNotBlank()) {
                                    Text("••••••", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            FilledIconButton(onClick = { items = items - item }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                }
            }

            HorizontalDivider()

            // Clients section
            Text(
                text = stringResource(R.string.npm_access_list_clients),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = clientAddress,
                    onValueChange = { clientAddress = it },
                    label = { Text(stringResource(R.string.npm_access_list_address)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                FilledIconButton(
                    onClick = {
                        if (clientAddress.isNotBlank()) {
                            clients = clients + NpmAccessListClient(
                                address = clientAddress,
                                directive = clientDirective
                            )
                            clientAddress = ""
                        }
                    },
                    enabled = clientAddress.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = clientDirective == "allow",
                    onClick = { clientDirective = "allow" },
                    label = { Text(stringResource(R.string.npm_access_list_allow)) },
                    modifier = Modifier.defaultMinSize(minWidth = 64.dp)
                )
                FilterChip(
                    selected = clientDirective == "deny",
                    onClick = { clientDirective = "deny" },
                    label = { Text(stringResource(R.string.npm_access_list_deny)) },
                    modifier = Modifier.defaultMinSize(minWidth = 64.dp)
                )
            }

            if (clients.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (client in clients) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(client.address, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (client.directive == "deny") stringResource(R.string.npm_access_list_deny) else stringResource(R.string.npm_access_list_allow),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledIconButton(onClick = { clients = clients - client }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                }
            }

            Button(
                onClick = { onSave(name, items, clients) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
