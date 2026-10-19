@echo off
chcp 65001 > nul
echo ==========================================================
echo        KHOI DONG SERVER BOMBERMAN UDP (THANH VIEN 1)
echo ==========================================================
if not exist bin (
    echo Chua bien dich! Dang chay compile.bat...
    call compile.bat
)
java -cp bin com.bomberman.server.ServerMain
pause
