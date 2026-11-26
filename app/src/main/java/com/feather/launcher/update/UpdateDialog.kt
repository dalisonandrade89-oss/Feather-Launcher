package com.feather.launcher.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feather.launcher.BuildConfig

@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    needsInstallPermission: Boolean,
    onConfirmDownload: (ReleaseInfo) -> Unit,
    onInstallNow: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateUiState.Idle -> Unit

        is UpdateUiState.Available -> {
            val release = state.release
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(text = "Atualização disponível (${release.tagName})")
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Versão atual: v${BuildConfig.VERSION_NAME} -> Nova: v${release.versionName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (release.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = release.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onConfirmDownload(release) }) {
                        Text("Baixar e instalar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Agora não")
                    }
                }
            )
        }

        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(text = "Baixando ${state.release.tagName}...")
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val progress = state.progressPercent
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$progress%",
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Baixando pacote APK...",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateUiState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = if (needsInstallPermission) {
                            "Permissão necessária"
                        } else {
                            "Pronto para instalar (${state.release.tagName})"
                        }
                    )
                },
                text = {
                    Text(
                        text = if (needsInstallPermission) {
                            "Para o Feather Launcher instalar atualizações, autorize Instalar apps desconhecidos nas configurações do Android e volte para concluir."
                        } else {
                            "O download da versão ${state.release.tagName} foi concluído. Toque em Instalar para atualizar agora."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    if (needsInstallPermission) {
                        TextButton(onClick = onOpenInstallPermission) {
                            Text("Autorizar")
                        }
                    } else {
                        TextButton(onClick = onInstallNow) {
                            Text("Instalar")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar")
                    }
                }
            )
        }

        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Falha na atualização") },
                text = { Text(state.message) },
                confirmButton = {
                    val release = state.release
                    if (release != null) {
                        TextButton(onClick = { onConfirmDownload(release) }) {
                            Text("Tentar novamente")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar")
                    }
                }
            )
        }
    }
}
