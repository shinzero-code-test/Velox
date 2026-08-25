package com.exapps.velox.feature.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exapps.velox.core.network.model.NetworkProtocol
import com.exapps.velox.core.network.model.NetworkServer
import com.exapps.velox.core.network.model.defaultPort
import com.exapps.velox.core.ui.components.ClickableGlassCard
import com.exapps.velox.core.ui.components.VeloxGlassIconButton
import com.exapps.velox.core.ui.theme.VeloxColors
import com.exapps.velox.core.ui.theme.VeloxShapes
import com.exapps.velox.core.ui.theme.VeloxSpacing

/**
 * Phase 2 "Network browsing" + "Network streams": one screen with three states —
 * server list, add/edit dialog, and the in-server directory browser. URL playback
 * (http/HLS/DASH/RTSP and our custom schemes) lives at the top.
 */
@Composable
fun NetworkScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val recents by viewModel.recentStreams.collectAsStateWithLifecycle()
    val browse by viewModel.browse.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<com.exapps.velox.core.network.model.NetworkServer?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeloxSpacing.lg, vertical = VeloxSpacing.sm),
        ) {
            VeloxGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.network_back),
                onClick = {
                    if (viewModel.isBrowsing) viewModel.goUp() else onBack()
                },
            )
            Column {
                Text(
                    text = stringResource(
                        if (viewModel.isBrowsing) R.string.network_browse_title else R.string.network_title,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = VeloxColors.OnBackground,
                )
                browse.server?.let {
                    Text(it.name, style = MaterialTheme.typography.bodyMedium, color = VeloxColors.OnSurfaceVariant)
                }
            }
        }

        when {
            // --- directory browser ---
            viewModel.isBrowsing -> BrowserContent(
                state = browse,
                onOpenDirectory = viewModel::openDirectory,
                onPlayEntry = viewModel::play,
                onUp = viewModel::goUp,
            )

            // --- server list + streams ---
            else -> ServersContent(
                servers = servers,
                recents = recents,
                onPlayStream = viewModel::playStream,
                onOpenServer = viewModel::openServer,
                onEdit = { editTarget = it; showAddDialog = true },
                onDelete = viewModel::deleteServer,
                onTest = viewModel::testServer,
                onAdd = { editTarget = null; showAddDialog = true },
            )
        }
    }

    if (showAddDialog) {
        ServerEditorDialog(
            existing = editTarget,
            onSave = { name, protocol, host, port, user, pass, base, secure ->
                viewModel.saveServer(name, protocol, host, port, user, pass, base, secure, editTarget?.id)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun ServersContent(
    servers: List<com.exapps.velox.core.network.model.NetworkServer>,
    recents: List<String>,
    onPlayStream: (String) -> Unit,
    onOpenServer: (com.exapps.velox.core.network.model.NetworkServer) -> Unit,
    onEdit: (com.exapps.velox.core.network.model.NetworkServer) -> Unit,
    onDelete: (Long) -> Unit,
    onTest: (com.exapps.velox.core.network.model.NetworkServer, (Boolean) -> Unit) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(VeloxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
    ) {
        item {
            StreamUrlField(onPlay = onPlayStream)
            Spacer(Modifier.height(VeloxSpacing.lg))
        }
        if (recents.isNotEmpty()) {
            item {
                Text(stringResource(R.string.network_recent), style = MaterialTheme.typography.labelLarge, color = VeloxColors.OnSurfaceVariant)
                Spacer(Modifier.height(VeloxSpacing.xs))
            }
            items(recents, key = { it }) { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentTint(),
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayStream(url) }
                        .padding(vertical = 4.dp),
                )
            }
            item { Spacer(Modifier.height(VeloxSpacing.md)) }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.network_servers), style = MaterialTheme.typography.labelLarge, color = VeloxColors.OnSurfaceVariant)
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.network_add_server))
                }
            }
        }
        if (servers.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.network_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VeloxColors.OnSurfaceVariant,
                )
            }
        }
        items(servers, key = { it.id }) { server ->
            ClickableGlassCard(onClick = { onOpenServer(server) }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = VeloxSpacing.xs)) {
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.titleMedium, color = VeloxColors.OnSurface)
                        Text(
                            "${server.protocol.name} · ${server.host}:${server.port}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VeloxColors.OnSurfaceVariant,
                        )
                    }
                    TestButton(server, onTest)
                    IconButton(onClick = { onEdit(server) }) {
                        Icon(Icons.Filled.Lan, contentDescription = stringResource(R.string.network_edit))
                    }
                    IconButton(onClick = { onDelete(server.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.network_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun accentTint() = com.exapps.velox.core.ui.theme.accentColor()

@Composable
private fun TestButton(server: com.exapps.velox.core.network.model.NetworkServer, onTest: (com.exapps.velox.core.network.model.NetworkServer, (Boolean) -> Unit) -> Unit) {
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    IconButton(onClick = {
        testing = true; result = null
        onTest(server) { ok -> testing = false; result = ok }
    }) {
        when {
            testing -> CircularProgressIndicator(modifier = Modifier.height(18.dp))
            result == true -> Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            result == false -> Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            else -> Icon(Icons.Filled.Lan, contentDescription = stringResource(R.string.network_test))
        }
    }
}

@Composable
private fun StreamUrlField(onPlay: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(stringResource(R.string.network_stream_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { if (url.isNotBlank()) onPlay(url) }, enabled = url.isNotBlank()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.network_play))
            }
        },
    )
}

@Composable
private fun BrowserContent(
    state: NetworkViewModel.BrowseState,
    onOpenDirectory: (com.exapps.velox.core.network.model.NetworkEntry) -> Unit,
    onPlayEntry: (com.exapps.velox.core.network.model.NetworkEntry, List<com.exapps.velox.core.network.model.NetworkEntry>) -> Unit,
    onUp: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (state.isLoading && state.entries.isEmpty()) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    state.error?.let {
        Column(Modifier.fillMaxSize().padding(VeloxSpacing.lg)) {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(VeloxSpacing.md))
            TextButton(onClick = onUp) { Text(stringResource(R.string.network_back)) }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(VeloxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(VeloxSpacing.xs),
    ) {
        items(state.entries, key = { it.url }) { entry ->
            ClickableGlassCard(
                onClick = {
                    if (entry.isDirectory) onOpenDirectory(entry)
                    else {
                        onPlayEntry(entry, state.entries)
                        onUp() // pop back toward the library once playback starts
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = VeloxSpacing.xs)) {
                    Icon(
                        imageVector = when {
                            entry.isDirectory -> Icons.Filled.Folder
                            else -> Icons.Filled.MusicNote
                        },
                        contentDescription = null,
                        tint = com.exapps.velox.core.ui.theme.accentColor(),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium, color = VeloxColors.OnSurface, maxLines = 1)
                        if (!entry.isDirectory && entry.sizeBytes >= 0) {
                            Text(
                                android.text.format.Formatter.formatShortFileSize(context, entry.sizeBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = VeloxColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}


/** Add / edit server dialog — protocol dropdown, connection fields, WebDAV secure toggle. */
@Composable
private fun ServerEditorDialog(
    existing: com.exapps.velox.core.network.model.NetworkServer?,
    onSave: (
        name: String,
        protocol: NetworkProtocol,
        host: String,
        port: String,
        username: String,
        password: String,
        basePath: String,
        secure: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var protocol by remember { mutableStateOf(existing?.protocol ?: NetworkProtocol.SMB) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember { mutableStateOf(existing?.port?.toString() ?: "") }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var basePath by remember { mutableStateOf(existing?.basePath ?: "/") }
    var secure by remember { mutableStateOf(existing?.secure ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.network_add_server else R.string.network_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.network_field_name)) }, singleLine = true)

                // Protocol picker (3 options — a menu keeps the dialog compact).
                var protocolOpen by remember { mutableStateOf(false) }
                ExposedProtocolField(
                    selected = protocol,
                    expanded = protocolOpen,
                    onExpand = { protocolOpen = it },
                    onSelect = { protocol = it; protocolOpen = false },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(VeloxSpacing.md)) {
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(stringResource(R.string.network_field_host)) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text(protocol.defaultPort().toString()) },
                        singleLine = true,
                        modifier = Modifier.width(110.dp),
                    )
                }

                if (protocol == NetworkProtocol.WEBDAV || protocol == NetworkProtocol.FTP || protocol == NetworkProtocol.SMB) {
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(stringResource(R.string.network_field_user)) }, singleLine = true)
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.network_field_password)) }, singleLine = true)
                }
                OutlinedTextField(value = basePath, onValueChange = { basePath = it }, label = { Text(stringResource(R.string.network_field_basepath)) }, singleLine = true)

                if (protocol == NetworkProtocol.WEBDAV) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.network_secure), style = MaterialTheme.typography.bodyMedium, color = VeloxColors.OnSurface)
                        Spacer(Modifier.weight(1f))
                    }
                    SwitchRow(checked = secure, onChange = { secure = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank(),
                onClick = { onSave(name, protocol, host, port, username, password, basePath, secure) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Three protocols — a cycling field avoids the experimental menu API entirely. */
@Composable
private fun ExposedProtocolField(
    selected: NetworkProtocol,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onSelect: (NetworkProtocol) -> Unit,
) {
    val options = NetworkProtocol.entries
    OutlinedTextField(
        value = selected.name,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.network_field_protocol)) },
        modifier = Modifier.fillMaxWidth().clickable {
            onSelect(options[(options.indexOf(selected) + 1).mod(options.size)])
        },
    )
}

@Composable
private fun SwitchRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
}
