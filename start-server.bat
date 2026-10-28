@echo off
cd /d "%~dp0"
echo Starting OpenRune server (Ctrl+C to stop)...
echo Note: --no-configuration-cache is required so newly added content modules are picked up.
call gradlew.bat run --console=plain --no-configuration-cache
pause
