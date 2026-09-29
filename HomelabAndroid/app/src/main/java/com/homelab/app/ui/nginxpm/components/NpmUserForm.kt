package com.homelab.app.ui.nginxpm.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.remote.dto.nginxpm.NpmUser
import com.homelab.app.data.remote.dto.nginxpm.NpmUserRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpmUserForm(
    editing: NpmUser?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (NpmUserRequest) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var email by remember(editing?.id) { mutableStateOf(editing?.email ?: "") }
    var name by remember(editing?.id) { mutableStateOf(editing?.name ?: "") }
    var nickname by remember(editing?.id) { mutableStateOf(editing?.nickname ?: "") }
    var password by remember { mutableStateOf("") }
    var role by remember(editing?.id) {
        mutableStateOf(if (editing?.roles?.contains("admin") == true) "admin" else "user")
    }
    var disabled by remember(editing?.id) { mutableStateOf(editing?.isDisabled ?: false) }

    LaunchedEffect(editing?.id) {
        password = ""
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
                text = stringResource(if (editing != null) R.string.npm_edit_user else R.string.npm_add_user),
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.npm_user_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.npm_user_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text(stringResource(R.string.npm_user_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.npm_user_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (editing != null) {
                    Text(
                        text = stringResource(R.string.npm_user_password_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.npm_user_role),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == "admin",
                        onClick = { role = "admin" },
                        label = { Text(stringResource(R.string.npm_user_role_admin)) }
                    )
                    FilterChip(
                        selected = role == "user",
                        onClick = { role = "user" },
                        label = { Text(stringResource(R.string.npm_user_role_user)) }
                    )
                }
            }

            FormSwitch(
                label = stringResource(R.string.npm_disabled),
                checked = disabled,
                onCheckedChange = { disabled = it }
            )

            Button(
                onClick = {
                    val trimmedEmail = email.trim()
                    val trimmedName = name.trim().ifEmpty { null }
                    val trimmedNickname = nickname.trim().ifEmpty { null }
                    onSave(
                        NpmUserRequest(
                            email = trimmedEmail,
                            name = trimmedName,
                            nickname = trimmedNickname,
                            password = password,
                            roles = listOf(role),
                            isDisabled = disabled
                        )
                    )
                },
                enabled = email.trim().isNotBlank() && (editing != null || password.isNotBlank()) && !isLoading,
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
