package dev.lamsa.client;

import com.sun.jna.Callback;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

/**
 * لمس متعدد الأصابع حقيقي على ويندوز: نستمع لرسائل WM_TOUCH ونمررها لمدير اللمس.
 * (GLFW لا يدعم اللمس المتعدد، وتطبيقات البث مثل Sunshine/Moonlight ترسله كلمس ويندوز.)
 * إذا فشل أي شيء يرجع المود تلقائياً للماوس (إصبع واحد) دون أي أعطال.
 */
public final class NativeTouch {
    private NativeTouch() {}

    public interface U32 extends StdCallLibrary {
        boolean RegisterTouchWindow(long hwnd, int flags);
        boolean UnregisterTouchWindow(long hwnd);
        boolean GetTouchInputInfo(long hTouchInput, int cInputs, Pointer pInputs, int cbSize);
        boolean CloseTouchInputHandle(long hTouchInput);
        long SetWindowLongPtrW(long hwnd, int index, Callback proc);
        long CallWindowProcW(long prev, long hwnd, int msg, long wParam, long lParam);
    }

    public interface Proc extends StdCallLibrary.StdCallCallback {
        long callback(long hwnd, int msg, long wParam, long lParam);
    }

    static final int GWLP_WNDPROC = -4;
    static final int WM_TOUCH = 0x0240;

    static U32 u32;
    static long hwnd, oldProc, glfwHandle;
    static Proc proc; // مرجع قوي كي لا يجمعه جامع القمامة
    static boolean installed, registered, failed;
    static String error = "";

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static void install() {
        if (installed || failed) return;
        if (!isWindows() || Native.POINTER_SIZE != 8) return;
        try {
            glfwHandle = Minecraft.getInstance().getWindow().handle();
            hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
            if (hwnd == 0) { failed = true; return; }
            u32 = Native.load("user32", U32.class);
            proc = new WndProc();
            oldProc = u32.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, proc);
            installed = oldProc != 0;
            if (!installed) failed = true;
        } catch (Throwable t) {
            failed = true;
            error = String.valueOf(t);
            System.err.println("[Lamsa] native touch unavailable: " + t);
        }
    }

    public static void setActive(boolean on) {
        try {
            if (on && !installed) install();
            if (!installed) return;
            if (on && !registered) registered = u32.RegisterTouchWindow(hwnd, 0);
            else if (!on && registered) { u32.UnregisterTouchWindow(hwnd); registered = false; }
        } catch (Throwable t) {
            error = String.valueOf(t);
            registered = false;
        }
    }

    static final class WndProc implements Proc {
        @Override
        public long callback(long h, int msg, long w, long l) {
            try {
                if (msg == WM_TOUCH && registered && Cfg.I.enabled && Cfg.I.nativeTouch && handleTouch(w, l)) return 0;
            } catch (Throwable t) {
                error = String.valueOf(t);
            }
            return u32.CallWindowProcW(oldProc, h, msg, w, l);
        }
    }

    static boolean handleTouch(long wParam, long lParam) {
        int n = (int) (wParam & 0xFFFF);
        if (n <= 0) return false;
        final int SZ = 48; // حجم TOUCHINPUT على 64 بت
        Memory m = new Memory((long) SZ * n);
        if (!u32.GetTouchInputInfo(lParam, n, m, SZ)) return false;
        int[] wx = new int[1], wy = new int[1];
        GLFW.glfwGetWindowPos(glfwHandle, wx, wy);
        for (int i = 0; i < n; i++) {
            long off = (long) i * SZ;
            double x = m.getInt(off) / 100.0 - wx[0];
            double y = m.getInt(off + 4) / 100.0 - wy[0];
            int id = m.getInt(off + 16);
            int flags = m.getInt(off + 20);
            int phase = (flags & 0x2) != 0 ? 0 : ((flags & 0x4) != 0 ? 2 : 1);
            Touch.pointer(id & 0x7FFFFFFF, phase, x, y);
        }
        u32.CloseTouchInputHandle(lParam);
        return true;
    }

    public static String status() {
        if (!isWindows()) return "اللمس المتعدد الأصلي لويندوز فقط — هنا يعمل بإصبع واحد (ماوس)";
        if (failed) return "تعذر تفعيل اللمس المتعدد — يعمل بإصبع واحد. " + error;
        if (registered) return Touch.nativeSeen ? "اللمس المتعدد: نشط ✔ (تم رصد أصابع)" : "اللمس المتعدد: جاهز — المس الشاشة لتجربته";
        return "اللمس المتعدد: متوقف";
    }
}
