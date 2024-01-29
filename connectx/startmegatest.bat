@echo off
setlocal enabledelayedexpansion

REM Define the input file
set "players=BobPlayers.txt"

for /f "delims=" %%a in (%players%) do (
	hundredplay.bat L0 %%a f
	hundredplay.bat L1 %%a f
)