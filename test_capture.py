import ctypes
import time
import subprocess
from ctypes import wintypes

user32 = ctypes.windll.user32

# Constants
WM_KEYDOWN = 0x0100
WM_KEYUP = 0x0101
VK_SPACE = 0x20

def find_window(substring):
    hwnd = None
    def enum_windows_proc(h, lParam):
        nonlocal hwnd
        length = user32.GetWindowTextLengthW(h)
        buff = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(h, buff, length + 1)
        title = buff.value
        if substring.lower() in title.lower():
            hwnd = h
            return False
        return True
    
    EnumWindowsProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wintypes.HWND, wintypes.LPARAM)
    user32.EnumWindows(EnumWindowsProc(enum_windows_proc), 0)
    return hwnd

def main():
    print("Searching for EOS Utility window...")
    hwnd = find_window("EOS Utility") or find_window("Live View")
    
    if not hwnd:
        print("EOS Utility window not found!")
        return

    print(f"Window found (HWND: {hwnd})")
    print("\n--- METHOD 1: Send SPACE silently via PostMessage ---")
    user32.PostMessageW(hwnd, WM_KEYDOWN, VK_SPACE, 0)
    time.sleep(0.05)
    user32.PostMessageW(hwnd, WM_KEYUP, VK_SPACE, 0)
    
    print("Sent PostMessage. Waiting 3 seconds to see if camera shoots...")
    time.sleep(3)
    
    print("\n--- METHOD 2: Send SPACE via PowerShell (Will flash window) ---")
    script = """
$wshell = New-Object -ComObject wscript.shell;
$wshell.AppActivate('EOS Utility');
Start-Sleep -Milliseconds 100;
$wshell.SendKeys(' ');
    """
    subprocess.run(["powershell", "-Command", script])
    print("PowerShell finished. Did the camera shoot?")

if __name__ == '__main__':
    main()
