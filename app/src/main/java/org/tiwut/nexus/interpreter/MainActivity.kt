package org.tiwut.nexus.interpreter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

enum class Screen { HOME, FILES, DISCOVER }

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
        NexusApp()
    }
  }
}

val defaultScript = """
loadstring(game:HttpGet("https://nexus.io/v2/init"))()
local Nexus = getgenv().Nexus
Nexus:Initialize({"HighPerformance"})

# Welcome to Nexus Runner!
set a="Nexus"
out "Hello, " + a

fn myfunc()
  out "Running myfunc..."
  out "Math: PI = " + math.pi()
  out "Math: sqrt(16) = " + math.sqrt("16")
  out "API: type = " + api.type()
  out "Str: upper = " + str.upper(a)
end

myfunc()
""".trimIndent()

val BgColor = Color(0xFF1B1B1F)
val TextColor = Color(0xFFE3E2E6)
val SurfaceColor = Color(0xFF2B2930)
val BorderColor = Color(0xFF49454F)
val TertiaryColor = Color(0xFF1C1B1F)
val AccentColor = Color(0xFFD0BCFF)
val OnAccentColor = Color(0xFF381E72)
val GreenAccent = Color(0xFF4ADE80)
val TextSecondary = Color(0xFF938F99)
val TextTertiary = Color(0xFFCAC4D0)

data class ScriptTab(
    val id: String,
    val name: String,
    val code: TextFieldValue
)

@Composable
fun NexusApp() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
  var tabs by remember { mutableStateOf(listOf(ScriptTab(UUID.randomUUID().toString(), "main.nexus.nx", TextFieldValue(defaultScript)))) }
  var activeTabId by remember { mutableStateOf(tabs.first().id) }
  var outputLog by remember { mutableStateOf(listOf(
      LogLine("[INFO] Nexus Core v4.2.0 loaded.", Color(0xFF60A5FA)),
      LogLine("[SUCCESS] Internal API hook established.", Color(0xFF4ADE80)),
      LogLine("[LOG] Memory usage: 42.4MB | Ping: 18ms", Color(0xFF938F99))
  )) }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var showSettings by remember { mutableStateOf(false) }
  var isWindowMode by remember { mutableStateOf(false) }
  var isConsoleExpanded by remember { mutableStateOf(false) }
  var currentScreen by remember { mutableStateOf(Screen.HOME) }

  val filePickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
      uri?.let {
          try {
              context.contentResolver.openInputStream(it)?.use { inputStream ->
                  val text = BufferedReader(InputStreamReader(inputStream)).readText()
                  var fileName = "script.nx"
                  
                  context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                      if (cursor.moveToFirst()) {
                          val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                          if (nameIndex != -1) {
                              fileName = cursor.getString(nameIndex)
                          }
                      }
                  }

                  val newTab = ScriptTab(UUID.randomUUID().toString(), fileName, TextFieldValue(text))
                  tabs = tabs + newTab
                  activeTabId = newTab.id
                  
                  outputLog = outputLog + LogLine("[INFO] Loaded script from ${it.path}", Color(0xFF4ADE80))
                  scope.launch { listState.animateScrollToItem(outputLog.size - 1) }
              }
          } catch (e: Exception) {
              outputLog = outputLog + LogLine("[ERROR] Failed to read file: ${e.message}", Color(0xFFEF4444))
              scope.launch { listState.animateScrollToItem(outputLog.size - 1) }
          }
      }
  }

  val handleTabAdded = {
      val newTab = ScriptTab(UUID.randomUUID().toString(), "untitled.nx", TextFieldValue(""))
      tabs = tabs + newTab
      activeTabId = newTab.id
  }

  val handleTabClosed = { idToClose: String ->
      val idx = tabs.indexOfFirst { it.id == idToClose }
      if (idx != -1) {
          tabs = tabs.filter { it.id != idToClose }
          if (activeTabId == idToClose) {
              activeTabId = tabs.getOrNull(maxOf(0, idx - 1))?.id ?: tabs.firstOrNull()?.id ?: ""
          }
          if (tabs.isEmpty()) {
              val fallback = ScriptTab(UUID.randomUUID().toString(), "untitled.nx", TextFieldValue(""))
              tabs = listOf(fallback)
              activeTabId = fallback.id
          }
      }
  }

  val handleCodeChange = { updated: TextFieldValue ->
      tabs = tabs.map { if (it.id == activeTabId) it.copy(code = updated) else it }
  }

  val uiState = remember { NexusUIState({}, scope) }
  uiState.onOutputLine = { log ->
      outputLog = outputLog + LogLine(log, Color(0xFFCAC4D0))
      scope.launch { listState.animateScrollToItem(outputLog.size - 1) }
  }

  val handleExecute = {
      val activeTab = tabs.find { it.id == activeTabId }
      if (activeTab != null) {
          outputLog = outputLog + LogLine("\nExecuting ${activeTab.name}...", TextSecondary)
          val engine = NexusEngine(uiState)
          scope.launch {
              engine.runCode(activeTab.code.text)
          }
      }
  }

  val handleClear = {
      tabs = tabs.map { if (it.id == activeTabId) it.copy(code = TextFieldValue("")) else it }
  }

  val handleCopy = {
      val activeTab = tabs.find { it.id == activeTabId }
      if (activeTab != null) {
          clipboardManager.setText(AnnotatedString(activeTab.code.text))
          outputLog = outputLog + LogLine("[INFO] Code copied to clipboard", TextSecondary)
          scope.launch { listState.animateScrollToItem(outputLog.size - 1) }
      }
  }

  val handlePaste = {
      val text = clipboardManager.getText()?.text ?: ""
      tabs = tabs.map { if (it.id == activeTabId) it.copy(code = TextFieldValue(text)) else it }
  }

  Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = BgColor,
      bottomBar = { 
          BottomNavBar(
              currentScreen = currentScreen,
              onNavigate = { currentScreen = it }
          ) 
      },
      contentWindowInsets = WindowInsets.systemBars
  ) { innerPadding ->
      Box(
          modifier = Modifier
              .padding(innerPadding)
              .fillMaxSize()
      ) {
          if (isWindowMode) {
              Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F13))) {
                  FloatingExecutorWindow(
                      tabs = tabs,
                      activeTabId = activeTabId,
                      onTabSelected = { activeTabId = it },
                      onTabAdded = handleTabAdded,
                      onTabClosed = handleTabClosed,
                      onCodeChange = handleCodeChange,
                      onExecute = handleExecute,
                      onClear = handleClear,
                      onCopy = handleCopy,
                      onPaste = handlePaste,
                      outputLog = outputLog,
                      listState = listState,
                      onClose = { isWindowMode = false }
                  )
              }
          } else if (currentScreen == Screen.HOME) {
              Column(modifier = Modifier.fillMaxSize()) {
                  Header(
                      onOpenFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                      onSettingsClick = { showSettings = true },
                      onWindowModeClick = { isWindowMode = true }
                  )

                  Column(
                      modifier = Modifier
                          .weight(1f)
                          .padding(horizontal = 16.dp)
                          .padding(bottom = 16.dp),
                      verticalArrangement = Arrangement.spacedBy(16.dp)
                  ) {
                      CodeEditorCard(
                          modifier = Modifier.weight(1f),
                          tabs = tabs,
                          activeTabId = activeTabId,
                          onTabSelected = { activeTabId = it },
                          onTabAdded = handleTabAdded,
                          onTabClosed = handleTabClosed,
                          onCodeChange = handleCodeChange,
                          onExecute = handleExecute,
                          onClear = handleClear,
                          onCopy = handleCopy,
                          onPaste = handlePaste
                      )

                      if (!isConsoleExpanded) {
                          QuickActionsGrid()
                      }

                      ConsoleOutput(
                          outputLog = outputLog,
                          listState = listState,
                          modifier = Modifier.fillMaxWidth().height(if (isConsoleExpanded) 300.dp else 96.dp),
                          isExpanded = isConsoleExpanded,
                          onToggleExpand = { isConsoleExpanded = !isConsoleExpanded }
                      )
                  }
              }
          } else if (currentScreen == Screen.FILES) {
              FilesScreen(
                  tabs = tabs,
                  onTabSelected = { activeTabId = it },
                  onNavigateHome = { currentScreen = Screen.HOME },
                  onImportFile = { filePickerLauncher.launch(arrayOf("*/*")) }
              )
          } else if (currentScreen == Screen.DISCOVER) {
              DiscoverScreen()
          }
      }
      
      if (showSettings) {
          SettingsDialog(onDismiss = { showSettings = false })
      }
      
      EngineDialogs(uiState)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineDialogs(uiState: NexusUIState) {
    if (uiState.showInputDialog) {
        var textState by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { /* forced action */ },
            title = { Text("Input Required", color = TextColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiState.inputPrompt, color = TextColor)
                    OutlinedTextField(
                        value = textState,
                        onValueChange = { textState = it },
                        textStyle = TextStyle(color = TextColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = BorderColor
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { uiState.inputCallback?.invoke(textState) }) {
                    Text("OK", color = AccentColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceColor,
            textContentColor = TextColor
        )
    }

    if (uiState.showMsgDialog) {
        AlertDialog(
            onDismissRequest = { uiState.msgCallback?.invoke() },
            title = { Text("Notice", color = TextColor, fontWeight = FontWeight.Bold) },
            text = { Text(uiState.msgText, color = TextColor) },
            confirmButton = {
                TextButton(onClick = { uiState.msgCallback?.invoke() }) {
                    Text("OK", color = AccentColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceColor,
            textContentColor = TextColor
        )
    }

    if (uiState.showGuiWindow) {
        AlertDialog(
            onDismissRequest = { uiState.guiCloseCallback?.invoke() },
            title = { Text(uiState.guiTitle, color = TextColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.guiElements.forEach { element ->
                        when (element) {
                            is GuiElement.Label -> {
                                Text(element.text, color = TextColor)
                            }
                            is GuiElement.Button -> {
                                Button(
                                    onClick = { uiState.guiActionCallback?.invoke(element.action) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                                ) {
                                    Text(element.text, color = OnAccentColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { uiState.guiCloseCallback?.invoke() }) {
                    Text("Close Panel", color = TextColor)
                }
            },
            containerColor = SurfaceColor,
            textContentColor = TextColor
        )
    }
}

data class LogLine(val text: String, val color: Color)

@Composable
fun Header(onOpenFile: () -> Unit, onSettingsClick: () -> Unit, onWindowModeClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Hub,
                    contentDescription = null,
                    tint = OnAccentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "Nexus Execution",
                    color = TextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GreenAccent)
                    )
                    Text(
                        text = "SYSTEM 100% INJECTED",
                        color = GreenAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onWindowModeClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DesktopMac,
                    contentDescription = "Window Mode",
                    tint = TextColor
                )
            }
            IconButton(
                onClick = onOpenFile,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = "Open File",
                    tint = TextColor
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = TextColor
                )
            }
        }
    }
}

@Composable
fun CodeEditorCard(
    modifier: Modifier = Modifier,
    tabs: List<ScriptTab>,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onTabAdded: () -> Unit,
    onTabClosed: (String) -> Unit,
    onCodeChange: (TextFieldValue) -> Unit,
    onExecute: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceColor)
            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs) { tab ->
                val isActive = tab.id == activeTabId
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) BorderColor else TertiaryColor)
                        .clickable { onTabSelected(tab.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tab.name.take(12) + if(tab.name.length > 12) "..." else "",
                        color = if (isActive) AccentColor else TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    if (tabs.size > 1) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isActive) AccentColor else TextSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .clickable { onTabClosed(tab.id) }
                        )
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TertiaryColor)
                        .clickable { onTabAdded() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        HorizontalDivider(color = BorderColor)

        val activeTab = tabs.find { it.id == activeTabId }

        if (activeTab != null) {
            val verticalScroll = androidx.compose.foundation.rememberScrollState()
            val horizontalScroll = androidx.compose.foundation.rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll)
                    .padding(8.dp)
            ) {
                val lineCount = maxOf(1, activeTab.code.text.count { it == '\n' } + 1)
                Text(
                    text = (1..lineCount).joinToString("\n"),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.padding(end = 12.dp)
                )
                BasicTextField(
                    value = activeTab.code,
                    onValueChange = onCodeChange,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFFD4D4D4),
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(AccentColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExecute,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = OnAccentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Execute",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("EXECUTE", fontWeight = FontWeight.Bold)
            }
            ActionButton(Icons.Outlined.Delete, "Clear", onClear)
            ActionButton(Icons.Outlined.ContentCopy, "Copy", onCopy)
            ActionButton(Icons.Outlined.ContentPaste, "Paste", onPaste)
        }
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BorderColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = TextColor
        )
    }
}

@Composable
fun QuickActionsGrid() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GridItem(icon = Icons.Outlined.CloudDownload, title = "Cloud Scripts", desc = "2,491 items available")
            GridItem(icon = Icons.Outlined.Visibility, title = "ESP Hub", desc = "12 active overlays")
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GridItem(icon = Icons.Outlined.AutoFixHigh, title = "Auto-Farm", desc = "Optimized engine")
            GridItem(icon = Icons.Outlined.Speed, title = "Performance", desc = "Boost enabled")
        }
    }
}

@Composable
fun GridItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TertiaryColor)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .clickable { }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = AccentColor,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = title,
                color = TextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ConsoleOutput(
    outputLog: List<LogLine>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier.fillMaxWidth().height(96.dp),
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONSOLE OUTPUT",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LIVE",
                    color = GreenAccent,
                    fontSize = 10.sp
                )
                if (onToggleExpand != null) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = "Toggle Console Size",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp).clickable { onToggleExpand() }
                    )
                }
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(outputLog) { log ->
                Text(
                    text = log.text,
                    color = log.color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(currentScreen: Screen, onNavigate: (Screen) -> Unit) {
    Column {
        HorizontalDivider(color = BorderColor, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(TertiaryColor)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(icon = Icons.Filled.Home, label = "Home", isActive = currentScreen == Screen.HOME, onClick = { onNavigate(Screen.HOME) })
            NavItem(icon = Icons.Outlined.FolderOpen, label = "Files", isActive = currentScreen == Screen.FILES, onClick = { onNavigate(Screen.FILES) })
            NavItem(icon = Icons.Outlined.Public, label = "Discover", isActive = currentScreen == Screen.DISCOVER, onClick = { onNavigate(Screen.DISCOVER) })
        }
    }
}

@Composable
fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isActive: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BorderColor)
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = AccentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(text = label, color = TextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Text(text = label, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val settingsList = listOf(
        "Execution" to listOf("Auto Inject", "Auto Execute", "Safe Mode", "Anti-Cheat Bypass", "Unlock FPS", "Silent Execute", "Lua C API", "Debug Mode", "Disable Telemetry"),
        "Visuals" to listOf("Top Most Window", "Transparent Window", "Syntax Highlighting", "Line Numbers", "Minimap", "Custom Font", "Dark Mode", "RGB UI", "Smooth Scrolling", "Focus Mode"),
        "Miscellaneous" to listOf("Save Tabs on Exit", "Auto Update Hub", "Crash Handler", "Discord RPC", "Save Instance", "Anti-AFK", "Auto Join Server", "Script Restore", "UI Blur", "Background Image")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        titleContentColor = TextColor,
        textContentColor = TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = AccentColor)
                Text("Nexus Configuration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                settingsList.forEach { (category, items) ->
                    item {
                        Text(
                            text = category.uppercase(),
                            color = AccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(items) { setting ->
                        SettingToggle(setting)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Save & Close", color = AccentColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SettingToggle(title: String) {
    var checked by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked }
            .padding(vertical = 4.dp)
    ) {
        Text(title, color = TextColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccentColor,
                checkedTrackColor = AccentColor,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = BorderColor
            ),
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
fun FloatingExecutorWindow(
    tabs: List<ScriptTab>,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onTabAdded: () -> Unit,
    onTabClosed: (String) -> Unit,
    onCodeChange: (TextFieldValue) -> Unit,
    onExecute: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    outputLog: List<LogLine>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onClose: () -> Unit
) {
    var offsetX by remember { mutableStateOf(40f) }
    var offsetY by remember { mutableStateOf(100f) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(360.dp)
            .height(550.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222222))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(12.dp))
            .shadow(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Hub, contentDescription = null, tint = AccentColor, modifier = Modifier.size(14.dp))
                    Text("Nexus Interpreter - PC Edition", color = TextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFEAB308)))
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFEF4444)).clickable { onClose() })
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CodeEditorCard(
                    modifier = Modifier.weight(1f),
                    tabs = tabs,
                    activeTabId = activeTabId,
                    onTabSelected = onTabSelected,
                    onTabAdded = onTabAdded,
                    onTabClosed = onTabClosed,
                    onCodeChange = onCodeChange,
                    onExecute = onExecute,
                    onClear = onClear,
                    onCopy = onCopy,
                    onPaste = onPaste
                )
                ConsoleOutput(
                    outputLog = outputLog,
                    listState = listState,
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        }
    }
}


@Composable
fun DiscoverScreen(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl("https://github.com/Nexus-Titan/Nexus-the-programming-language")
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun FilesScreen(
    modifier: Modifier = Modifier,
    tabs: List<ScriptTab>,
    onTabSelected: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onImportFile: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = AccentColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("File Explorer", color = TextColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        
        Button(
            onClick = onImportFile,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = OnAccentColor)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("IMPORT FROM DEVICE", fontWeight = FontWeight.Bold)
        }
        
        Text("ACTIVE SCRIPTS (${tabs.size})", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs) { tab ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceColor)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .clickable { 
                            onTabSelected(tab.id)
                            onNavigateHome()
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = AccentColor)
                        Text(tab.name, color = TextColor, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Open", tint = TextSecondary)
                }
            }
        }
    }
}

