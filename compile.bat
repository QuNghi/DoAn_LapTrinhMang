@echo off
chcp 65001 > nul
echo ==========================================================
echo        BIÊN DỊCH DỰ ÁN BOMBERMAN MULTIPLAYER UDP
echo ==========================================================

if not exist bin mkdir bin

echo Dang bien dich cac file Java trong thu muc src/...
javac -encoding UTF-8 -d bin src/com/bomberman/common/*.java src/com/bomberman/server/model/*.java src/com/bomberman/server/network/*.java src/com/bomberman/server/logic/*.java src/com/bomberman/server/*.java src/com/bomberman/client/*.java

if %ERRORLEVEL% EQU 0 (
    echo [THANH CONG] Da bien dich xong toan bo project vao thu muc bin/!
) else (
    echo [THAT BAI] Co loi xay ra trong qua trinh bien dich!
)
pause
