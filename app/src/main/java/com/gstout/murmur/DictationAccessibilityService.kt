package com.gstout.murmur

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.text.InputType
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Watches for a focused text field with the keyboard up, shows the floating mic button,
 * and types the finished dictation into the field.
 */
class DictationAccessibilityService : AccessibilityService() {

    private enum class State { IDLE, RECORDING, PROCESSING }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val refreshRunnable = Runnable { refreshBubble() }
    private val prefs by lazy { Prefs(this) }

    private lateinit var windowManager: WindowManager
    private var bubble: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var state = State.IDLE
    private var target: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = scheduleRefresh()

    /** Events arrive in bursts; settle before checking focus and keyboard state. */
    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 150)
    }

    /**
     * Android 13+ mirrors the keyboard's input session to this service. That reports any
     * active text field, including ones (Jetpack Compose, Flutter, custom editors) that
     * don't show up correctly in the accessibility focus tree.
     */
    @RequiresApi(33)
    override fun onCreateInputMethod(): InputMethod = object : InputMethod(this) {
        override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
            super.onStartInput(attribute, restarting)
            handler.post { scheduleRefresh() }
        }

        override fun onFinishInput() {
            super.onFinishInput()
            handler.post { scheduleRefresh() }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (state == State.RECORDING) RecordingService.end(deliver = false)
        instance = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        removeBubble()
        super.onDestroy()
    }

    // ---- Bubble visibility ----

    /**
     * The focused, editable, non-password text field. Some UI toolkits (notably Jetpack
     * Compose) report the whole screen container as input-focused, so search inside it
     * for the text field that actually has focus.
     */
    private fun focusedEditable(): AccessibilityNodeInfo? {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused.takeUnless { it.isPassword }
        val roots = listOfNotNull(focused, rootInActiveWindow)
        for (root in roots) {
            findFocusedEditable(root)?.let { return it.takeUnless { node -> node.isPassword } }
        }
        return null
    }

    private fun findFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SEARCHED) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable && node.isFocused) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    /** The keyboard's active text field (Android 13+), unless it's a password field. */
    private fun activeInputSession(): EditorInfo? {
        if (Build.VERSION.SDK_INT < 33) return null
        val im = inputMethod ?: return null
        if (!im.currentInputStarted) return null
        val editor = im.currentInputEditorInfo ?: return null
        return editor.takeUnless { isPasswordOrNonText(it.inputType) }
    }

    private fun isPasswordOrNonText(inputType: Int): Boolean {
        if (inputType == InputType.TYPE_NULL) return true
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun keyboardBounds(): Rect? =
        windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { window -> Rect().also { window.getBoundsInScreen(it) } }

    private fun refreshBubble() {
        if (state != State.IDLE) return
        val keyboard = keyboardBounds()
        if (keyboard != null && (activeInputSession() != null || focusedEditable() != null)) {
            showBubble(keyboard)
        } else {
            removeBubble()
        }
    }

    private fun showBubble(keyboard: Rect) {
        val size = dp(BUBBLE_DP)
        val margin = dp(12)
        val screenWidth = resources.displayMetrics.widthPixels
        val x = (screenWidth - size - margin + prefs.bubbleDx).coerceIn(0, screenWidth - size)
        val y = (keyboard.top - size - margin + prefs.bubbleDy).coerceAtLeast(0)

        val existing = bubble
        val params = bubbleParams
        if (existing != null && params != null) {
            if (params.x != x || params.y != y) {
                params.x = x
                params.y = y
                windowManager.updateViewLayout(existing, params)
            }
            return
        }

        val view = BubbleView(this)
        val newParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        attachTouchHandling(view, newParams)
        windowManager.addView(view, newParams)
        bubble = view
        bubbleParams = newParams
    }

    private fun removeBubble() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        bubbleParams = null
    }

    /** Tap = start/stop. Drag = move (remembered). Long-press while recording = cancel. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchHandling(view: BubbleView, params: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressed = false
        val longPress = Runnable {
            if (state == State.RECORDING) {
                longPressed = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                cancelDictation()
            }
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressed = false
                    handler.postDelayed(longPress, 700)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        prefs.bubbleDx += params.x - startX
                        prefs.bubbleDy += params.y - startY
                    } else if (!longPressed) {
                        onBubbleTap(view)
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            true
        }
    }

    // ---- Dictation flow ----

    private fun onBubbleTap(view: BubbleView) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        when (state) {
            State.IDLE -> startDictation()
            State.RECORDING -> stopDictation()
            State.PROCESSING -> Unit
        }
    }

    private fun startDictation() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Open Spoke and allow microphone access")
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        target = focusedEditable()
        RecordingService.begin()
        try {
            startForegroundService(Intent(this, RecordingService::class.java))
        } catch (e: Exception) {
            toast("Couldn't start recording: ${e.message}")
            return
        }
        setState(State.RECORDING)
    }

    private fun stopDictation() {
        setState(State.PROCESSING)
        RecordingService.end(deliver = true)
    }

    private fun cancelDictation() {
        RecordingService.end(deliver = false)
        toast("Dictation cancelled")
        finishDictation()
    }

    /** Called by [RecordingService] with the finished audio. */
    fun onAudioReady(audio: File) {
        scope.launch {
            setState(State.PROCESSING)
            try {
                val raw = withContext(Dispatchers.IO) { Transcriber.transcribe(prefs, audio) }
                if (raw.isBlank()) {
                    toast("Didn't catch anything")
                    return@launch
                }
                val text = if (prefs.cleanupEnabled && prefs.anthropicKey.isNotBlank()) {
                    try {
                        withContext(Dispatchers.IO) { Cleaner.clean(prefs, raw) }
                    } catch (e: Exception) {
                        toast("Cleanup failed, inserted raw text: ${e.message?.take(80)}")
                        raw
                    }
                } else raw
                insertText(text)
            } catch (e: Exception) {
                toast(e.message ?: "Transcription failed")
            } finally {
                audio.delete()
                finishDictation()
            }
        }
    }

    /** Called by [RecordingService] when the mic couldn't be used. */
    fun onRecordingFailed(message: String?) {
        handler.post {
            toast(message ?: "Recording failed")
            finishDictation()
        }
    }

    private fun finishDictation() {
        target = null
        setState(State.IDLE)
        refreshBubble()
    }

    private fun setState(newState: State) {
        state = newState
        val mode = when (newState) {
            State.IDLE -> BubbleView.Mode.IDLE
            State.RECORDING -> BubbleView.Mode.RECORDING
            State.PROCESSING -> BubbleView.Mode.PROCESSING
        }
        bubble?.setMode(mode)
    }

    // ---- Text insertion ----

    private fun insertText(dictated: String) {
        if (Build.VERSION.SDK_INT >= 33 && commitThroughKeyboardSession(dictated)) return

        val node = target?.takeIf { it.refresh() && it.isEditable } ?: focusedEditable()
        if (node == null) {
            copyToClipboard(dictated)
            toast("No text field found. Copied to clipboard instead.")
            return
        }

        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start < 0 || end < 0 || start > current.length || end > current.length) {
            start = current.length
            end = current.length
        }
        if (start > end) start = end.also { end = start }

        // Add a space when dictating right after existing text.
        val needsSpace = start > 0 && !current[start - 1].isWhitespace() &&
            dictated.firstOrNull()?.let { it.isLetterOrDigit() } == true
        val insertion = if (needsSpace) " $dictated" else dictated
        val newText = current.substring(0, start) + insertion + current.substring(end)

        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) {
            val cursor = start + insertion.length
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
            return
        }

        // Some apps ignore SET_TEXT; fall back to pasting.
        copyToClipboard(insertion)
        if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            toast("Couldn't type into this field. Copied to clipboard instead.")
        }
    }

    /**
     * Type the text through the keyboard's input session, the same way a keyboard does.
     * Works in fields that ignore accessibility SET_TEXT.
     */
    @RequiresApi(33)
    private fun commitThroughKeyboardSession(dictated: String): Boolean {
        if (activeInputSession() == null) return false
        val connection = inputMethod?.currentInputConnection ?: return false
        val before = connection.getSurroundingText(1, 0, 0)
        val charBefore = before?.let { st ->
            val cursor = st.selectionStart - st.offset
            if (cursor > 0 && cursor <= st.text.length) st.text[cursor - 1] else null
        }
        val needsSpace = charBefore != null && !charBefore.isWhitespace() &&
            dictated.firstOrNull()?.isLetterOrDigit() == true
        connection.commitText(if (needsSpace) " $dictated" else dictated, 1, null)
        return true
    }

    private fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Dictation", text))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        @Volatile
        var instance: DictationAccessibilityService? = null
            private set

        private const val BUBBLE_DP = 52
        private const val MAX_NODES_SEARCHED = 400
    }
}
