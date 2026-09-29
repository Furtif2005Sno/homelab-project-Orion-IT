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
import com.homelab.app.data.remote.dto.nginxpm.NpmStream
import com.homelab.app.data.remote.dto.nginxpm.NpmStreamRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpmStreamForm(
    existing: NpmStream? = null,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (NpmStreamRequest) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var incomingPort by remember { mutableStateOf(existing?.incomingPort?.toString() ?: "") }
    var forwardingHost by remember { mutableStateOf(existing?.forwardingHost ?: "") }
    var forwardingPort by remember { mutableStateOf(existing?.forwardingPort?.toString() ?: "") }
    var tcpForwarding by remember { mutableStateOf(existing?.tcpForwarding != 0) }
    var udpForwarding by remember { mutableStateOf(existing?.udpForwarding == 1) }
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
                text = stringResource(if (existing != null) R.string.npm_edit_stream else R.string.npm_add_stream),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            PortTextField(
                value = incomingPort,
                onValueChange = { incomingPort = it },
                label = stringResource(R.string.npm_incoming_port)
            )

            OutlinedTextField(
                value = forwardingHost,
                onValueChange = { forwardingHost = it },
                label = { Text(stringResource(R.string.npm_forwarding_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            PortTextField(
                value = forwardingPort,
                onValueChange = { forwardingPort = it },
                label = stringResource(R.string.npm_forwarding_port)
            )

            HorizontalDivider()

            FormSwitch(stringResource(R.string.npm_tcp_forwarding), tcpForwarding) { tcpForwarding = it }
            FormSwitch(stringResource(R.string.npm_udp_forwarding), udpForwarding) { udpForwarding = it }
            FormSwitch(stringResource(R.string.npm_enabled), enabled) { enabled = it }

            Button(
                onClick = {
                    onSave(
                        NpmStreamRequest(
                            incomingPort = incomingPort.toIntOrNull() ?: 0,
                            forwardingHost = forwardingHost,
                            forwardingPort = forwardingPort.toIntOrNull() ?: 0,
                            tcpForwarding = if (tcpForwarding) 1 else 0,
                            udpForwarding = if (udpForwarding) 1 else 0,
                            enabled = if (enabled) 1 else 0
                        )
                    )
                },
                enabled = incomingPort.isNotBlank() && forwardingHost.isNotBlank() && forwardingPort.isNotBlank() && !isLoading,
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
