package com.phuctran.photobooth.desktop

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

fun main() {
    println("Đang tìm cửa sổ EOS Utility...")
    
    var foundHwnd: WinDef.HWND? = null
    User32.INSTANCE.EnumWindows(object : WinUser.WNDENUMPROC {
        override fun callback(hwnd: WinDef.HWND, arg: com.sun.jna.Pointer?): Boolean {
            val windowText = CharArray(512)
            User32.INSTANCE.GetWindowText(hwnd, windowText, 512)
            val title = String(windowText).trim { it <= ' ' }
            if (title.contains("EOS Utility", ignoreCase = true) || title.contains("Live View", ignoreCase = true)) {
                foundHwnd = hwnd
                println("Đã tìm thấy cửa sổ: ${'$'}title")
                return false // Dừng tìm kiếm
            }
            return true
        }
    }, null)

    if (foundHwnd == null) {
        println("Không tìm thấy cửa sổ EOS Utility nào đang mở!")
        return
    }

    println("Bắt đầu test gửi phím Space NGẦM (PostMessage)...")
    val WM_KEYDOWN = 0x0100
    val WM_KEYUP = 0x0101
    val VK_SPACE = 0x20
    val wparam = WinDef.WPARAM(VK_SPACE.toLong())
    val lparam = WinDef.LPARAM(0)

    User32.INSTANCE.PostMessage(foundHwnd, WM_KEYDOWN, wparam, lparam)
    Thread.sleep(50)
    User32.INSTANCE.PostMessage(foundHwnd, WM_KEYUP, wparam, lparam)
    
    println("Đã gửi PostMessage. Kiểm tra xem máy ảnh có chụp không?")
    Thread.sleep(3000)

    println("\nBắt đầu test gửi phím Space bằng PowerShell (Sẽ nháy cửa sổ lên trên cùng)...")
    try {
        val script = """
            ${'$'}wshell = New-Object -ComObject wscript.shell;
            ${'$'}wshell.AppActivate('EOS Utility');
            Start-Sleep -Milliseconds 100;
            ${'$'}wshell.SendKeys(' ');
        """.trimIndent()
        
        val process = ProcessBuilder("powershell.exe", "-Command", script).start()
        process.waitFor()
        println("Đã chạy xong PowerShell. Kiểm tra xem máy ảnh có chụp không?")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
