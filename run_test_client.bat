@echo off
chcp 65001 > nul
echo ==========================================================
echo       KHOI DONG CLIENT KIEM THU (TEST CLIENT UDP)
echo ==========================================================
if not exist bin (
    echo Chua bien dich! Dang chay compile.bat...
    call compile.bat
)
set /p PlayerName=Nhap ten nguoi choi (vi du: P1, P2): 
if "%PlayerName%"=="" set PlayerName=Player_Test
java -cp bin com.bomberman.client.TestClient %PlayerName% 127.0.0.1 9876
pause
