import ctypes
import time
from ctypes import wintypes

user32 = ctypes.windll.user32

VK_SPACE = 0x20
KEYEVENTF_KEYUP = 0x0002

def find_eos_window():
    hwnd = None
    def enum_windows_proc(h, lParam):
        nonlocal hwnd
        if not user32.IsWindowVisible(h):
            return True
        length = user32.GetWindowTextLengthW(h)
        buff = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(h, buff, length + 1)
        title = buff.value.lower()
        if 'eos utility' in title or 'live view' in title:
            hwnd = h
            return False
        return True
    
    EnumWindowsProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wintypes.HWND, wintypes.LPARAM)
    user32.EnumWindows(EnumWindowsProc(enum_windows_proc), 0)
    return hwnd

def main():
    print("--- TEST CÁCH 3: Dùng SetForegroundWindow + Hardware Key Press ---")
    hwnd = find_eos_window()
    
    if not hwnd:
        print("Lỗi: Bạn chưa bật EOS Utility hoặc Live View!")
        return

    print(f"Đã tìm thấy cửa sổ EOS (HWND: {hwnd})")
    
    # Kéo cửa sổ lên trên cùng
    user32.ShowWindow(hwnd, 9) # SW_RESTORE
    user32.SetForegroundWindow(hwnd)
    time.sleep(0.5) # Chờ xíu cho cửa sổ thực sự hiện lên
    
    print("Đang bấm phím Space...")
    # Bấm phím Space (mô phỏng phím cứng)
    user32.keybd_event(VK_SPACE, 0, 0, 0)
    time.sleep(0.1)
    user32.keybd_event(VK_SPACE, 0, KEYEVENTF_KEYUP, 0)
    
    print("Đã bấm xong! Xem máy có kêu tạch không?")
    
if __name__ == '__main__':
    main()
