package com.maxrave.simpmusic.geras.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.maxrave.logger.Logger
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Window
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

// ---- Win32 surface, mapped by hand: x64/arm64 only, so WPARAM/LPARAM/LRESULT are Long ----

private object Msg {
    const val WM_SIZE = 0x0005
    const val WM_NCCALCSIZE = 0x0083
    const val WM_NCHITTEST = 0x0084
    const val WM_NCMOUSEMOVE = 0x00A0
    const val WM_NCLBUTTONDOWN = 0x00A1
    const val WM_NCLBUTTONUP = 0x00A2
    const val WM_NCLBUTTONDBLCLK = 0x00A3
    const val WM_NCMOUSELEAVE = 0x02A2
}

private object Hit {
    const val HTCLIENT = 1L
    const val HTCAPTION = 2L
    const val HTMINBUTTON = 8L
    const val HTMAXBUTTON = 9L
    const val HTTOP = 12L
    const val HTTOPLEFT = 13L
    const val HTTOPRIGHT = 14L
    const val HTCLOSE = 20L
}

private const val GWLP_WNDPROC = -4
private const val SM_CXFRAME = 32
private const val SM_CYFRAME = 33
private const val SM_CXPADDEDBORDER = 92
private const val SW_MAXIMIZE = 3
private const val SW_MINIMIZE = 6
private const val SW_RESTORE = 9
private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002
private const val SWP_NOZORDER = 0x0004
private const val SWP_NOACTIVATE = 0x0010
private const val SWP_FRAMECHANGED = 0x0020
private const val TME_LEAVE = 0x0002
private const val TME_NONCLIENT = 0x0010
private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

@Structure.FieldOrder("x", "y")
internal class WinPoint : Structure() {
    @JvmField var x: Int = 0

    @JvmField var y: Int = 0
}

@Structure.FieldOrder("left", "top", "right", "bottom")
internal class WinRect : Structure() {
    @JvmField var left: Int = 0

    @JvmField var top: Int = 0

    @JvmField var right: Int = 0

    @JvmField var bottom: Int = 0
}

@Structure.FieldOrder("cbSize", "dwFlags", "hwndTrack", "dwHoverTime")
internal class TrackMouse : Structure() {
    @JvmField var cbSize: Int = 0

    @JvmField var dwFlags: Int = 0

    @JvmField var hwndTrack: Pointer? = null

    @JvmField var dwHoverTime: Int = 0
}

@Structure.FieldOrder("cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight")
internal class DwmMargins : Structure() {
    @JvmField var cxLeftWidth: Int = 0

    @JvmField var cxRightWidth: Int = 0

    @JvmField var cyTopHeight: Int = 0

    @JvmField var cyBottomHeight: Int = 0
}

@Suppress("FunctionName")
private interface User32X : Library {
    fun GetWindowLongPtrW(hWnd: Pointer, nIndex: Int): Long

    fun SetWindowLongPtrW(hWnd: Pointer, nIndex: Int, dwNewLong: Long): Long

    fun CallWindowProcW(prev: Pointer, hWnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long

    fun IsZoomed(hWnd: Pointer): Boolean

    fun ShowWindow(hWnd: Pointer, nCmdShow: Int): Boolean

    fun GetDpiForWindow(hWnd: Pointer): Int

    fun GetSystemMetricsForDpi(nIndex: Int, dpi: Int): Int

    fun ScreenToClient(hWnd: Pointer, lpPoint: WinPoint): Boolean

    fun GetClientRect(hWnd: Pointer, lpRect: WinRect): Boolean

    fun SetWindowPos(hWnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean

    fun TrackMouseEvent(track: TrackMouse): Boolean
}

@Suppress("FunctionName")
private interface DwmApi : Library {
    fun DwmExtendFrameIntoClientArea(hWnd: Pointer, margins: DwmMargins): Int

    fun DwmSetWindowAttribute(hWnd: Pointer, attr: Int, value: IntByReference, size: Int): Int
}

private interface WndProc : Callback {
    fun callback(hWnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long
}

private val user32: User32X by lazy { Native.load("user32", User32X::class.java) }
private val dwm: DwmApi by lazy { Native.load("dwmapi", DwmApi::class.java) }

enum class CaptionButton { Minimize, Maximize, Close }

/** Rect in client pixels. */
private data class PxRect(val l: Int, val t: Int, val r: Int, val b: Int) {
    fun contains(x: Int, y: Int) = x in l until r && y in t until b
}

/**
 * Windows custom caption. Keeps the native (decorated) window so DWM still provides the shadow,
 * rounded corners, resize borders, Aero Snap and the Windows 11 snap layouts flyout, and removes
 * only the caption by subclassing the window procedure:
 *  - WM_NCCALCSIZE drops the top non-client strip (the caption) and keeps the other borders;
 *  - WM_NCHITTEST maps the app-drawn strip to HTCAPTION, the maximize button to HTMAXBUTTON
 *    (which is what makes Windows show snap layouts) and the top edge to HTTOP;
 *  - the WM_NC*BUTTON* messages for the three buttons are handled here and fed to Compose as
 *    hover/press state, since the buttons no longer receive client mouse input.
 */
class WindowsCaption private constructor(
    private val window: Window,
    private val hwnd: Pointer,
) : WindowCaption {
    private val hovered = MutableStateFlow<CaptionButton?>(null)
    private val pressed = MutableStateFlow<CaptionButton?>(null)
    private val maximized = MutableStateFlow(false)

    @Volatile private var caption: PxRect? = null
    private val interactive = ConcurrentHashMap<Any, PxRect>()
    private val buttons = ConcurrentHashMap<CaptionButton, PxRect>()

    private var original: Pointer? = null

    // Held strongly: JNA frees the native thunk once the callback object is collected.
    private val proc =
        object : WndProc {
            override fun callback(hWnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long =
                try {
                    handle(hWnd, msg, wParam, lParam)
                } catch (t: Throwable) {
                    Logger.e("WindowsCaption", "wndproc failed on msg=$msg", t)
                    callOriginal(hWnd, msg, wParam, lParam)
                }
        }

    private fun callOriginal(hWnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long =
        user32.CallWindowProcW(original!!, hWnd, msg, wParam, lParam)

    private fun install() {
        original = Pointer(user32.GetWindowLongPtrW(hwnd, GWLP_WNDPROC))
        val thunk = Pointer.nativeValue(CallbackReference.getFunctionPointer(proc))
        user32.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, thunk)
        // A 1px top margin keeps the DWM shadow and border once the caption is gone.
        dwm.DwmExtendFrameIntoClientArea(hwnd, DwmMargins().apply { cyTopHeight = 1 })
        maximized.value = user32.IsZoomed(hwnd)
        user32.SetWindowPos(
            hwnd, null, 0, 0, 0, 0,
            SWP_FRAMECHANGED or SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE,
        )
        // AWT recomputes its insets on a size change, not on SWP_FRAMECHANGED alone.
        SwingUtilities.invokeLater {
            val size = window.size
            window.setSize(size.width, size.height + 1)
            window.setSize(size.width, size.height)
        }
        Logger.i("WindowsCaption", "custom caption installed")
    }

    fun setDarkFrame(dark: Boolean) {
        dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(if (dark) 1 else 0), 4)
    }

    private fun frameX(): Int {
        val dpi = user32.GetDpiForWindow(hwnd)
        return user32.GetSystemMetricsForDpi(SM_CXFRAME, dpi) + user32.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
    }

    private fun frameY(): Int {
        val dpi = user32.GetDpiForWindow(hwnd)
        return user32.GetSystemMetricsForDpi(SM_CYFRAME, dpi) + user32.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
    }

    private fun buttonFor(hit: Long): CaptionButton? =
        when (hit) {
            Hit.HTMINBUTTON -> CaptionButton.Minimize
            Hit.HTMAXBUTTON -> CaptionButton.Maximize
            Hit.HTCLOSE -> CaptionButton.Close
            else -> null
        }

    private fun handle(hWnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long {
        when (msg) {
            Msg.WM_NCCALCSIZE -> {
                if (wParam == 0L) return callOriginal(hWnd, msg, wParam, lParam)
                // lParam -> NCCALCSIZE_PARAMS; rgrc[0] is the first RECT, top at +4.
                val params = Pointer(lParam)
                val originalTop = params.getInt(4)
                val result = callOriginal(hWnd, msg, wParam, lParam)
                // A maximized window hangs its frame off-screen; keep the content on-screen.
                val top = if (user32.IsZoomed(hWnd)) originalTop + frameY() else originalTop
                params.setInt(4, top)
                return result
            }

            Msg.WM_NCHITTEST -> {
                val base = callOriginal(hWnd, msg, wParam, lParam)
                if (base != Hit.HTCLIENT) return base
                val pt =
                    WinPoint().apply {
                        x = (lParam and 0xFFFF).toShort().toInt()
                        y = ((lParam shr 16) and 0xFFFF).toShort().toInt()
                    }
                user32.ScreenToClient(hWnd, pt)
                val zoomed = user32.IsZoomed(hWnd)
                if (!zoomed && pt.y < frameY()) {
                    val client = WinRect().also { user32.GetClientRect(hWnd, it) }
                    val corner = frameX() * 2
                    return when {
                        pt.x < corner -> Hit.HTTOPLEFT
                        pt.x >= client.right - corner -> Hit.HTTOPRIGHT
                        else -> Hit.HTTOP
                    }
                }
                buttons.forEach { (button, rect) ->
                    if (rect.contains(pt.x, pt.y)) {
                        return when (button) {
                            CaptionButton.Minimize -> Hit.HTMINBUTTON
                            CaptionButton.Maximize -> Hit.HTMAXBUTTON
                            CaptionButton.Close -> Hit.HTCLOSE
                        }
                    }
                }
                val strip = caption ?: return Hit.HTCLIENT
                if (strip.contains(pt.x, pt.y) && interactive.values.none { it.contains(pt.x, pt.y) }) {
                    return Hit.HTCAPTION
                }
                return Hit.HTCLIENT
            }

            Msg.WM_NCMOUSEMOVE -> {
                val button = buttonFor(wParam)
                if (hovered.value != button) {
                    hovered.value = button
                    if (button != null) {
                        user32.TrackMouseEvent(
                            TrackMouse().apply {
                                cbSize = size()
                                dwFlags = TME_LEAVE or TME_NONCLIENT
                                hwndTrack = hWnd
                            },
                        )
                    }
                }
                return callOriginal(hWnd, msg, wParam, lParam)
            }

            Msg.WM_NCMOUSELEAVE -> {
                hovered.value = null
                pressed.value = null
                return callOriginal(hWnd, msg, wParam, lParam)
            }

            Msg.WM_NCLBUTTONDOWN, Msg.WM_NCLBUTTONDBLCLK -> {
                val button = buttonFor(wParam) ?: return callOriginal(hWnd, msg, wParam, lParam)
                pressed.value = button
                return 0L
            }

            Msg.WM_NCLBUTTONUP -> {
                val button = buttonFor(wParam) ?: return callOriginal(hWnd, msg, wParam, lParam)
                if (pressed.value == button) perform(button)
                pressed.value = null
                return 0L
            }

            Msg.WM_SIZE -> {
                val result = callOriginal(hWnd, msg, wParam, lParam)
                maximized.value = user32.IsZoomed(hWnd)
                return result
            }
        }
        return callOriginal(hWnd, msg, wParam, lParam)
    }

    private fun perform(button: CaptionButton) {
        when (button) {
            CaptionButton.Minimize -> user32.ShowWindow(hwnd, SW_MINIMIZE)
            CaptionButton.Maximize -> user32.ShowWindow(hwnd, if (user32.IsZoomed(hwnd)) SW_RESTORE else SW_MAXIMIZE)
            CaptionButton.Close ->
                SwingUtilities.invokeLater {
                    window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
                }
        }
    }

    private fun androidx.compose.ui.geometry.Rect.toPx() =
        PxRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    @Composable
    override fun Modifier.captionArea(enabled: Boolean): Modifier {
        DisposableEffect(enabled) {
            if (!enabled) caption = null
            onDispose { caption = null }
        }
        return if (enabled) onGloballyPositioned { caption = it.boundsInWindow().toPx() } else this
    }

    @Composable
    override fun Modifier.captionInteractive(): Modifier {
        val key = remember { Any() }
        DisposableEffect(key) { onDispose { interactive.remove(key) } }
        return onGloballyPositioned { interactive[key] = it.boundsInWindow().toPx() }
    }

    @Composable
    override fun CaptionButtons(modifier: Modifier) {
        val hover by hovered.collectAsState()
        val press by pressed.collectAsState()
        val isMax by maximized.collectAsState()
        DisposableEffect(Unit) { onDispose { buttons.clear() } }
        Row(modifier.fillMaxHeight()) {
            CaptionButton.entries.forEach { button ->
                val isClose = button == CaptionButton.Close
                val active = hover == button || press == button
                val bg =
                    when {
                        isClose && active -> Color(0xFFC42B1C).copy(alpha = if (press == button) 0.85f else 1f)
                        active -> MaterialTheme.colorScheme.onSurface.copy(alpha = if (press == button) 0.06f else 0.1f)
                        else -> Color.Transparent
                    }
                val ink = if (isClose && active) Color.White else MaterialTheme.colorScheme.onSurface
                Box(
                    Modifier
                        .width(46.dp)
                        .fillMaxHeight()
                        .background(bg)
                        .onGloballyPositioned { buttons[button] = it.boundsInWindow().toPx() },
                    contentAlignment = Alignment.Center,
                ) {
                    CaptionGlyph(button, isMax, ink)
                }
            }
        }
    }

    companion object {
        val isSupported: Boolean =
            System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true) &&
                System.getProperty("os.version", "0").substringBefore('.').toIntOrNull()?.let { it >= 10 } == true

        /** Installs on a shown, displayable window. Null when unsupported or the install fails. */
        fun install(window: Window, windowHandle: Long): WindowsCaption? {
            if (!isSupported || windowHandle == 0L) return null
            return try {
                WindowsCaption(window, Pointer(windowHandle)).also { it.install() }
            } catch (t: Throwable) {
                Logger.e("WindowsCaption", "falling back to the native title bar", t)
                null
            }
        }
    }
}

@Composable
private fun CaptionGlyph(
    button: CaptionButton,
    maximized: Boolean,
    color: Color,
) {
    Canvas(Modifier.size(10.dp)) {
        val s = size.width
        val stroke = Stroke(width = 1.dp.toPx())
        when (button) {
            CaptionButton.Minimize ->
                drawLine(color, Offset(0f, s / 2), Offset(s, s / 2), stroke.width)
            CaptionButton.Maximize ->
                if (maximized) {
                    val o = s * 0.2f
                    drawRect(color, topLeft = Offset(0f, o), size = androidx.compose.ui.geometry.Size(s - o, s - o), style = stroke)
                    drawLine(color, Offset(o, o), Offset(o, 0f), stroke.width)
                    drawLine(color, Offset(o, 0f), Offset(s, 0f), stroke.width)
                    drawLine(color, Offset(s, 0f), Offset(s, s - o), stroke.width)
                    drawLine(color, Offset(s, s - o), Offset(s - o, s - o), stroke.width)
                } else {
                    drawRect(color, size = androidx.compose.ui.geometry.Size(s, s), style = stroke)
                }
            CaptionButton.Close -> {
                drawLine(color, Offset(0f, 0f), Offset(s, s), stroke.width)
                drawLine(color, Offset(s, 0f), Offset(0f, s), stroke.width)
            }
        }
    }
}
