using System;
using Microsoft.Win32;

namespace GamepadBridge.Desktop;

/// <summary>Mengatur "jalankan saat Windows menyala" lewat registry pengguna (tanpa hak admin).</summary>
internal static class StartupManager
{
    private const string KeyPath = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string ValueName = "GamepadBridge";

    public static bool IsEnabled()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(KeyPath);
            return key?.GetValue(ValueName) != null;
        }
        catch
        {
            return false;
        }
    }

    public static void Set(bool enabled)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(KeyPath, true)
                            ?? Registry.CurrentUser.CreateSubKey(KeyPath);
            if (enabled)
            {
                string? exe = Environment.ProcessPath;
                if (exe != null) key.SetValue(ValueName, "\"" + exe + "\" --minimized");
            }
            else
            {
                key.DeleteValue(ValueName, false);
            }
        }
        catch
        {
            // abaikan: pengaturan tidak tersimpan
        }
    }
}
