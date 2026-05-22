package org.tiwut.nexus.interpreter

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class GuiElement {
    data class Label(val text: String) : GuiElement()
    data class Button(val text: String, val action: String) : GuiElement()
}

class NexusUIState(
    var onOutputLine: (String) -> Unit,
    val scope: CoroutineScope
) : NexusUI {
    var showInputDialog by mutableStateOf(false)
    var inputPrompt by mutableStateOf("")
    var inputCallback: ((String) -> Unit)? = null

    var showMsgDialog by mutableStateOf(false)
    var msgText by mutableStateOf("")
    var msgCallback: (() -> Unit)? = null

    var showGuiWindow by mutableStateOf(false)
    var guiTitle by mutableStateOf("")
    var guiColor by mutableStateOf("white")
    val guiElements = mutableStateListOf<GuiElement>()
    var guiActionCallback: ((String) -> Unit)? = null
    var guiCloseCallback: (() -> Unit)? = null

    override fun onOutput(text: String) {
        onOutputLine(text)
    }

    override suspend fun onInput(prompt: String): String = suspendCancellableCoroutine { cont ->
        inputPrompt = prompt
        inputCallback = {
            showInputDialog = false
            cont.resume(it)
        }
        showInputDialog = true
        
        cont.invokeOnCancellation {
            showInputDialog = false
        }
    }

    override suspend fun showGuiMsg(text: String) = suspendCancellableCoroutine<Unit> { cont ->
        msgText = text
        msgCallback = {
            showMsgDialog = false
            cont.resume(Unit)
        }
        showMsgDialog = true

        cont.invokeOnCancellation {
            showMsgDialog = false
        }
    }

    override fun buildGuiWindow(title: String) {
        guiTitle = title
        guiElements.clear()
        guiColor = "white"
    }

    override fun buildGuiColor(color: String) {
        guiColor = color
    }

    override fun buildGuiLabel(text: String) {
        guiElements.add(GuiElement.Label(text))
    }

    override fun buildGuiButton(text: String, action: String) {
        guiElements.add(GuiElement.Button(text, action))
    }

    override suspend fun showGuiRun(onAction: suspend (String) -> Unit) = suspendCancellableCoroutine<Unit> { cont ->
        guiActionCallback = { action ->
            scope.launch {
                try {
                    onAction(action)
                } catch (e: Exception) {
                    onOutputLine("[ERROR] ${e.message}")
                }
            }
        }
        guiCloseCallback = {
            showGuiWindow = false
            cont.resume(Unit)
        }
        showGuiWindow = true

        cont.invokeOnCancellation {
            showGuiWindow = false
        }
    }
}
