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
        topBar = { TopBar(vm, state, tab) },
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            Column {
                Text(if (tab == Tab.CHAT) "Gemma Chat" else "LiteRT Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                val sub = when (state) {
                    is EngineState.Ready   -> state.speedupLabel
                    is EngineState.Loading -> "Loading model..."
                    else -> "No model loaded"
                }
                Text(sub, style = MaterialTheme.typography.labelSmall,
                    color = if (state is EngineState.Ready && state.mtpActive)
                        Color(0xFF4FC3F7) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        actions = {
            if (state is EngineState.Ready && tab == Tab.CHAT) {
                if (state.mtpActive) {
                    Text("MTP⚡", modifier = Modifier.padding(end = 8.dp),
                        color = Color(0xFF4FC3F7),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { vm.newChat() }) {
                    Icon(Icons.Filled.Add, "New Chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = current == Tab.CHAT,
            onClick  = { onSelect(Tab.CHAT) },
            icon     = { Icon(Icons.Filled.Chat, null) },
            label    = { Text("Chat") }
        )
        NavigationBarItem(
            selected = current == Tab.MODELS,
            onClick  = { onSelect(Tab.MODELS) },
            icon     = { Icon(Icons.Filled.List, null) },
            label    = { Text("Models") }
        )
    }
}

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
    val draftPath  by vm.drafterPath
    val useDrafter by vm.useDrafter

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🤖", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("Load Gemma 4 E4B",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = modelPath, onValueChange = { vm.modelPath.value = it },
            label = { Text("Model path") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MTP Drafter", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Up to 3x faster", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4FC3F7))
                    }
                    Switch(checked = useDrafter, onCheckedChange = { vm.useDrafter.value = it })
                }
                if (useDrafter) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftPath, onValueChange = { vm.drafterPath.value = it },
                        label = { Text("Drafter path") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Button(onClick = { vm.loadModel() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))) {
            Text("Load Model", color = Color(0xFF003549), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChatMessages(vm: ChatViewModel) {
    val messages  = vm.messages
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (messages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨", fontSize = 40.sp)
                Text("Gemma 4 E4B ready", style = MaterialTheme.typography.titleMedium)
                Text("MTP Drafter • 3x faster",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF4FC3F7))
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(messages.size) { i -> Bubble(messages[i]) }
        }
    }
}

@Composable
fun Bubble(msg: ChatMessage) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (msg.isUser) Color(0xFF4FC3F7).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
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
        Row(Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Message Gemma...") },
                modifier = Modifier.weight(1f), maxLines = 4,
                enabled = !isGenerating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { send() },
                enabled = input.isNotBlank() && !isGenerating,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF4FC3F7))
            ) {
                if (isGenerating)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = Color(0xFF003549))
                else
                    Icon(Icons.Filled.Send, null, tint = Color(0xFF003549))
            }
        }
    }
}

@Composable
fun ModelsTab(vm: ChatViewModel, onNavigate: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(ModelCatalogue.all.size) { i ->
            ModelCard(ModelCatalogue.all[i],
                onOpen = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                onUse  = { model -> vm.loadFromCatalogue(model); onNavigate() }
            )
        }
    }
}

@Composable
fun ModelCard(model: LiteRTModel, onOpen: (String) -> Unit, onUse: (LiteRTModel) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        if (model.isMTPDrafter) {
                            Spacer(Modifier.width(6.dp))
                            Text("MTP⚡", style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4FC3F7))
                        }
                    }
                    Text(model.variant, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (model.sizeMB >= 1000) "${"%.1f".format(model.sizeMB/1000f)}GB"
                     else "${model.sizeMB}MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(model.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen(model.kaggleUrl) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("Kaggle", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { onOpen(model.hfUrl) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("HF", style = MaterialTheme.typography.labelSmall)
                }
                if (!model.isMTPDrafter) {
                    Button(onClick = { onUse(model) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("Use", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF003549))
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF4FC3F7))
            Spacer(Modifier.height(16.dp))
            Text("Loading Gemma 4 E4B...")
            Text("10–30 seconds", style = MaterialTheme.typography.bodySmall,
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
