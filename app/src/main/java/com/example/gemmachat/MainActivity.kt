package com.example.gemmachat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { GemmaChatApp() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary          = Color(0xFF4FC3F7),
            background       = Color(0xFF0D1117),
            surface          = Color(0xFF161B22),
            surfaceVariant   = Color(0xFF21262D),
            onBackground     = Color(0xFFE6EDF3),
            onSurface        = Color(0xFFE6EDF3),
            onSurfaceVariant = Color(0xFF8B949E),
            outline          = Color(0xFF30363D),
            error            = Color(0xFFF85149),
        ),
        content = content
    )
}

enum class Tab { CHAT, MODELS }

@Composable
fun GemmaChatApp(vm: ChatViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.CHAT) }
    val state by vm.engineState
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar    = { TopBar(vm, state, tab) },
        bottomBar = { BottomNav(tab) { tab = it } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.CHAT   -> ChatTab(vm, state)
                Tab.MODELS -> ModelsTab(vm) { tab = Tab.CHAT }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(vm: ChatViewModel, state: EngineState, tab: Tab) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        title = {
            Column {
                Text(if (tab == Tab.CHAT) "Gemma Chat" else "LiteRT Models",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when (state) {
                        is EngineState.Ready   -> state.speedupLabel
                        is EngineState.Loading -> "Loading model..."
                        else -> "No model loaded"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state is EngineState.Ready) Color(0xFF4FC3F7)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            if (state is EngineState.Ready && tab == Tab.CHAT) {
                IconButton(onClick = { vm.newChat() }) {
                    Icon(Icons.Filled.Add, "New Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = current == Tab.CHAT, onClick = { onSelect(Tab.CHAT) },
            icon = { Icon(Icons.Filled.Chat, null) }, label = { Text("Chat") }
        )
        NavigationBarItem(
            selected = current == Tab.MODELS, onClick = { onSelect(Tab.MODELS) },
            icon = { Icon(Icons.Filled.List, null) }, label = { Text("Models") }
        )
    }
}

// ════════════ CHAT TAB ════════════

@Composable
fun ChatTab(vm: ChatViewModel, state: EngineState) {
    when (state) {
        is EngineState.Idle    -> SetupScreen(vm)
        is EngineState.Loading -> LoadingScreen()
        is EngineState.Error   -> ErrorScreen(state.message) { vm.engineState.value = EngineState.Idle }
        is EngineState.Ready   -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { ChatMessages(vm) }
            ChatInput(vm)
        }
    }
}

@Composable
fun SetupScreen(vm: ChatViewModel) {
    val modelPath  by vm.modelPath
    val useDrafter by vm.useDrafter
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🤖", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("Load Gemma 4 E4B", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Download model from Models tab first", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = modelPath, onValueChange = { vm.modelPath.value = it },
            label = { Text("Model path") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.loadModel() }, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))) {
            Text("Load Model", color = Color(0xFF003549), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChatMessages(vm: ChatViewModel) {
    val messages  = vm.messages
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    if (messages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨", fontSize = 40.sp)
                Text("Gemma 4 E4B ready", style = MaterialTheme.typography.titleMedium)
                Text("Running fully on-device", style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4FC3F7))
            }
        }
    } else {
        LazyColumn(state = listState, contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(messages.size) { i -> Bubble(messages[i]) }
        }
    }
}

@Composable
fun Bubble(msg: ChatMessage) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 280.dp),
            color = if (msg.isUser) Color(0xFF4FC3F7).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(12.dp)) {
                if (msg.text.isNotEmpty()) {
                    Text(msg.text, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                if (msg.isStreaming) {
                    val inf = rememberInfiniteTransition(label = "b")
                    val a by inf.animateFloat(1f, 0f,
                        infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
                    Text("▌", color = Color(0xFF4FC3F7).copy(alpha = a))
                }
            }
        }
    }
}

@Composable
fun ChatInput(vm: ChatViewModel) {
    val isGenerating by vm.isGenerating
    var input by remember { mutableStateOf("") }
    fun send() { if (input.isNotBlank() && !isGenerating) { vm.sendMessage(input.trim()); input = "" } }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = input, onValueChange = { input = it },
                placeholder = { Text("Message Gemma...") },
                modifier = Modifier.weight(1f), maxLines = 4, enabled = !isGenerating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                shape = RoundedCornerShape(24.dp))
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send() }, enabled = input.isNotBlank() && !isGenerating,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF4FC3F7))) {
                if (isGenerating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF003549))
                else Icon(Icons.Filled.Send, null, tint = Color(0xFF003549))
            }
        }
    }
}

// ════════════ MODELS TAB ════════════

@Composable
fun ModelsTab(vm: ChatViewModel, onNavigate: () -> Unit) {
    val context        = LocalContext.current
    val downloadState by vm.downloadState
    val hfToken       by vm.hfToken

    Column(Modifier.fillMaxSize()) {

        // HuggingFace Token input
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp)) {
                Text("HuggingFace Token", style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4FC3F7), fontWeight = FontWeight.SemiBold)
                Text("Required to download Gemma models",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hfToken,
                        onValueChange = { vm.hfToken.value = it },
                        placeholder = { Text("hf_xxxxxxxxxxxxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://huggingface.co/settings/tokens")))
                    }) {
                        Icon(Icons.Filled.OpenInNew, "Get token",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Get token: huggingface.co/settings/tokens (read access)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Download progress banner
        if (downloadState is DownloadState.Downloading) {
            val ds = downloadState as DownloadState.Downloading
            Surface(color = Color(0xFF4FC3F7).copy(alpha = 0.1f)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Downloading ${ds.modelId}...",
                            style = MaterialTheme.typography.labelMedium, color = Color(0xFF4FC3F7))
                        Text("${ds.progress}%",
                            style = MaterialTheme.typography.labelMedium, color = Color(0xFF4FC3F7))
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ds.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4FC3F7),
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { vm.cancelDownload() }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (downloadState is DownloadState.Done) {
            val ds = downloadState as DownloadState.Done
            Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${ds.modelId} downloaded!", color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Row {
                        TextButton(onClick = {
                            vm.modelPath.value = ds.path
                            vm.loadModel()
                            onNavigate()
                        }) { Text("Load Now", color = Color(0xFF4FC3F7),
                            style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = { vm.resetDownload() }) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }

        if (downloadState is DownloadState.Failed) {
            val ds = downloadState as DownloadState.Failed
            Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("❌ ${ds.error}", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.resetDownload() }) {
                        Text("OK", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        // Model list
        LazyColumn(contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(ModelCatalogue.all.size) { i ->
                ModelCard(
                    model         = ModelCatalogue.all[i],
                    downloadState = downloadState,
                    onOpen        = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    onDownload    = { model -> vm.downloadModel(model) },
                    onUse         = { model -> vm.loadFromCatalogue(model); onNavigate() }
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: LiteRTModel,
    downloadState: DownloadState,
    onOpen: (String) -> Unit,
    onDownload: (LiteRTModel) -> Unit,
    onUse: (LiteRTModel) -> Unit
) {
    val isDownloaded  = ModelDownloader.isDownloaded("${model.id}.task")
    val isDownloading = downloadState is DownloadState.Downloading &&
                        (downloadState as DownloadState.Downloading).modelId == model.id
    val progress      = if (isDownloading) (downloadState as DownloadState.Downloading).progress else 0

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (model.isMTPDrafter) BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.4f)) else null) {
        Column(Modifier.padding(14.dp)) {

            // Header row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        if (model.isMTPDrafter) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = Color(0xFF4FC3F7).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)) {
                                Text("MTP⚡", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF4FC3F7))
                            }
                        }
                        if (isDownloaded) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(model.variant, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (model.sizeMB >= 1000) "${"%.1f".format(model.sizeMB / 1000f)}GB"
                     else "${model.sizeMB}MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(6.dp))
            Text(model.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = TextOverflow.Ellipsis)

            // Download progress bar
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Downloading...", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4FC3F7))
                    Text("$progress%", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4FC3F7))
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF4FC3F7),
                    trackColor = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(10.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Open on HF
                OutlinedButton(onClick = { onOpen(model.hfUrl) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("HF", style = MaterialTheme.typography.labelSmall)
                }

                // Download button
                if (!isDownloaded && !isDownloading) {
                    Button(onClick = { onDownload(model) },
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(14.dp),
                            tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Download", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }

                // Use button (if downloaded)
                if (isDownloaded && !model.isMTPDrafter) {
                    Button(onClick = { onUse(model) },
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                        Icon(Icons.Filled.RocketLaunch, null, modifier = Modifier.size(14.dp),
                            tint = Color(0xFF003549))
                        Spacer(Modifier.width(4.dp))
                        Text("Load", style = MaterialTheme.typography.labelSmall, color = Color(0xFF003549))
                    }
                }
            }
        }
    }
}

// ════════════ UTILITY SCREENS ════════════

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF4FC3F7))
            Spacer(Modifier.height(16.dp))
            Text("Loading Gemma 4 E4B...")
            Text("10-30 seconds", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Load failed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("Try Again") }
        }
    }
}
