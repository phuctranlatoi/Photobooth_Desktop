package com.phuctran.photobooth.desktop.services

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

object WindowCaptureHelper {

    fun captureWindow(windowTitleSubstring: String): BufferedImage? {
        var foundHwnd: WinDef.HWND? = null
        User32.INSTANCE.EnumWindows(object : WinUser.WNDENUMPROC {
            override fun callback(hwnd: WinDef.HWND, arg: com.sun.jna.Pointer?): Boolean {
                val windowText = CharArray(512)
                User32.INSTANCE.GetWindowText(hwnd, windowText, 512)
                val title = String(windowText).trim { it <= ' ' }
                if (title.contains(windowTitleSubstring, ignoreCase = true)) {
                    foundHwnd = hwnd
                    return false // stop enumerating
                }
                return true
            }
        }, null)

        if (foundHwnd == null) return null
        
        return captureWindow(foundHwnd!!)
    }

    fun captureWindow(hwnd: WinDef.HWND): BufferedImage? {
        // Tìm child window lớn nhất (khả năng cao nhất là luồng Video Preview, bỏ qua các nút bấm)
        var largestChild: WinDef.HWND = hwnd
        var maxArea = 0
        
        User32.INSTANCE.EnumChildWindows(hwnd, { child, _ ->
            if (User32.INSTANCE.IsWindowVisible(child)) {
                val r = WinDef.RECT()
                User32.INSTANCE.GetClientRect(child, r)
                val area = (r.right - r.left) * (r.bottom - r.top)
                if (area > maxArea) {
                    maxArea = area
                    largestChild = child
                }
            }
            true
        }, null)

        val targetHwnd = largestChild

        val rect = WinDef.RECT()
        User32.INSTANCE.GetClientRect(targetHwnd, rect)
        val width = rect.right - rect.left
        val height = rect.bottom - rect.top

        if (width <= 0 || height <= 0) return null

        val hdcWindow = User32.INSTANCE.GetDC(targetHwnd)
        val hdcMemDC = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow)
        val hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, width, height)
        val hOld = GDI32.INSTANCE.SelectObject(hdcMemDC, hBitmap)

        // Capture window
        // PW_CLIENTONLY = 1, PW_RENDERFULLCONTENT = 2 -> 1 | 2 = 3
        val result = User32.INSTANCE.PrintWindow(targetHwnd, hdcMemDC, 3)

        var image: BufferedImage? = null

        if (result) {
            val bmi = WinGDI.BITMAPINFO()
            bmi.bmiHeader.biWidth = width
            bmi.bmiHeader.biHeight = -height // top-down
            bmi.bmiHeader.biPlanes = 1
            bmi.bmiHeader.biBitCount = 32
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB

            val mem = Memory((width * height * 4).toLong())
            GDI32.INSTANCE.GetDIBits(
                hdcWindow,
                hBitmap,
                0,
                height,
                mem,
                bmi,
                WinGDI.DIB_RGB_COLORS
            )

            image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val pixels = (image.raster.dataBuffer as DataBufferInt).data
            mem.read(0, pixels, 0, pixels.size)
        }

        GDI32.INSTANCE.SelectObject(hdcMemDC, hOld)
        GDI32.INSTANCE.DeleteObject(hBitmap)
        GDI32.INSTANCE.DeleteDC(hdcMemDC)
        User32.INSTANCE.ReleaseDC(targetHwnd, hdcWindow)

        return image
    }

    fun sendSpaceToWindow(windowTitleSubstring: String): Boolean {
        var foundHwnd: WinDef.HWND? = null
        User32.INSTANCE.EnumWindows(object : WinUser.WNDENUMPROC {
            override fun callback(hwnd: WinDef.HWND, arg: com.sun.jna.Pointer?): Boolean {
                if (!User32.INSTANCE.IsWindowVisible(hwnd)) return true
                val windowText = CharArray(512)
                User32.INSTANCE.GetWindowText(hwnd, windowText, 512)
                val title = String(windowText).trim { it <= ' ' }
                if (title.contains(windowTitleSubstring, ignoreCase = true)) {
                    foundHwnd = hwnd
                    return false // stop enumerating
                }
                return true
            }
        }, null)

        if (foundHwnd == null) return false

        try {
            // Nhớ lại cửa sổ hiện tại (Photobooth Kiosk)
            var myHwnd: WinDef.HWND? = null
            User32.INSTANCE.EnumWindows(object : WinUser.WNDENUMPROC {
                override fun callback(hwnd: WinDef.HWND, arg: com.sun.jna.Pointer?): Boolean {
                    if (!User32.INSTANCE.IsWindowVisible(hwnd)) return true
                    val windowText = CharArray(512)
                    User32.INSTANCE.GetWindowText(hwnd, windowText, 512)
                    val title = String(windowText).trim { it <= ' ' }
                    if (title.contains("Photobooth Kiosk", ignoreCase = true)) {
                        myHwnd = hwnd
                        return false
                    }
                    return true
                }
            }, null)

            // Đưa EOS Utility lên trên cùng
            User32.INSTANCE.SetForegroundWindow(foundHwnd)
            Thread.sleep(100) // Chờ một chút để OS kịp focus

            // Gửi phím cứng Space bằng Robot (Cách mạnh mẽ nhất, hoạt động 100%)
            val robot = java.awt.Robot()
            robot.keyPress(java.awt.event.KeyEvent.VK_SPACE)
            Thread.sleep(50)
            robot.keyRelease(java.awt.event.KeyEvent.VK_SPACE)
            Thread.sleep(100)

            // Trả lại focus cho Photobooth Kiosk
            if (myHwnd != null) {
                User32.INSTANCE.SetForegroundWindow(myHwnd)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        
        return true
    }
}
