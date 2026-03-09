package com.example.portmonnai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.portmonnai.ui.theme.Gold
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.NotificationsActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dataFolderPath: String?,
    notificationsEnabled: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onChooseFolder: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Section Données ──────────────────────────────────────
            SettingsSectionTitle("Données")

            // Choix du dossier
            SettingsCard(
                icon = Icons.Default.FolderOpen,
                iconTint = Gold,
                title = "Dossier de sauvegarde",
                subtitle = dataFolderPath ?: "Non défini — appuyez pour choisir",
                onClick = onChooseFolder
            )

            // Export
            SettingsCard(
                icon = Icons.Default.FileUpload,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Exporter les données",
                subtitle = "Enregistre un fichier de votre portefeuille (.val)",
                onClick = onExport
            )

            // Import
            SettingsCard(
                icon = Icons.Default.FileDownload,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = "Importer les données",
                subtitle = "Restaure un fichier de sauvegarde Valoria (.val)",
                onClick = onImport
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Section Alertes ──────────────────────────────────────
            SettingsSectionTitle("Alertes")

            SettingsToggleCard(
                icon = androidx.compose.material.icons.Icons.Default.NotificationsActive,
                iconTint = Gold,
                title = "Alertes de prix",
                subtitle = "Notifier si un actif varie de plus de 3% en 24h",
                checked = notificationsEnabled,
                onCheckedChange = onToggleNotifications
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Info
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ℹ️  À propos de l'export", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "Le fichier exporté (valoria_backup.val) contient tous vos actifs et transactions. " +
                        "Vous pouvez le partager, l'envoyer par email ou le synchroniser avec Google Drive.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun SettingsCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
    }
}
@Composable
fun SettingsToggleCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Gold
                )
            )
        }
    }
}
