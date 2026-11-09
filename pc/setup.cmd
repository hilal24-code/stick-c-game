@echo off
REM Jalankan SEKALI di folder ini (Command Prompt). Membuat file .csproj dan solution.
dotnet new sln -n GamepadBridge || goto :err
dotnet new classlib -o GamepadBridge.Protocol -f net10.0 || goto :err
dotnet new classlib -o GamepadBridge.Core -f net10.0 || goto :err
dotnet new console  -o GamepadBridge.Simulator -f net10.0 || goto :err
dotnet new xunit    -o GamepadBridge.Protocol.Tests -f net10.0 || goto :err
del GamepadBridge.Protocol\Class1.cs GamepadBridge.Core\Class1.cs GamepadBridge.Simulator\Program.cs GamepadBridge.Protocol.Tests\UnitTest1.cs
dotnet add GamepadBridge.Core reference GamepadBridge.Protocol || goto :err
dotnet add GamepadBridge.Core package Nefarius.ViGEm.Client || goto :err
dotnet add GamepadBridge.Simulator reference GamepadBridge.Protocol GamepadBridge.Core || goto :err
dotnet add GamepadBridge.Protocol.Tests reference GamepadBridge.Protocol || goto :err
dotnet sln add GamepadBridge.Protocol GamepadBridge.Core GamepadBridge.Simulator GamepadBridge.Protocol.Tests || goto :err
echo.
echo Selesai. Lanjut: dotnet test   lalu   dotnet run --project GamepadBridge.Simulator
goto :eof
:err
echo GAGAL. Kirim pesan error di atas ke Claude.
