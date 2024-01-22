@echo off
setlocal enabledelayedexpansion

REM Define the input file
set "input=board_configurations.txt"

REM Check the number of arguments
if "%~3"=="" (
	echo Wrong number of arguments. Please insert at least two players!
	exit /b
)

REM Define the players
set "PLAYER0=%~1"
set "PLAYER1=%~2"

set verbose=false
if "%~3"=="true" (
	set verbose=true
)

set /a score0=0
set /a score1=0
set /a won0=0
set /a won1=0
set /a lost0=0
set /a lost1=0
set /a draw0=0
set /a draw1=0
set /a error0=0
set /a error1=0

::Bob2 Score: 18 Won: 5 Lost: 0 Draw: 3 Error: 2
::L1 Score: 9 Won: 0 Lost: 5 Draw: 3 Error: 0
REM Read from the input file
for /f "delims=" %%a in (%input%) do (
	echo.
	echo [36mTesting %%a[0m
	REM Run the game PLAYER0 vs PLAYER1
	echo|set /p= "- !PLAYER0! vs !PLAYER1! - "
	FOR /F "tokens=1,3,5,7,9,11 delims=: " %%F IN ('java -cp ".." connectx.CXPlayerTester %%a connectx.!PLAYER0!.!PLAYER0! connectx.!PLAYER1!.!PLAYER1! -r 1 -t 1') DO (
		:: F = Player, G = Score, H = Won, I = Lost, J = Draw, K = Error
		if "!verbose!"=="true" (
			echo %%F 
		)
        if "%%F" == "!PLAYER0!" (
            set /a score0=!score0!+%%G
            set /a won0=!won0!+%%H
            set /a lost0=!lost0!+%%I
            set /a draw0=!draw0!+%%J
            set /a error0=!error0!+%%K
            if %%H == 1 (
                echo [93m!PLAYER0! WIN[0m
            ) else if %%J == 1 (
                echo [92mDRAW[0m
            ) else (
                echo [91m!PLAYER1! WIN[0m
            )
        )
        if "%%F" == "!PLAYER1!" (
            set /a score1=!score1!+%%G
            set /a won1=!won1!+%%H
            set /a lost1=!lost1!+%%I
            set /a draw1=!draw1!+%%J
            set /a error1=!error1!+%%K
		)
	)
	REM Run the game PLAYER1! vs PLAYER0
	echo|set /p= "- !PLAYER1! vs !PLAYER0! - "
	FOR /F "tokens=1,3,5,7,9,11 delims=: " %%F IN ('java -cp ".." connectx.CXPlayerTester %%a connectx.!PLAYER1!.!PLAYER1! connectx.!PLAYER0!.!PLAYER0! -r 1 -t 1') DO (
		:: F = Player, G = Score, H = Won, I = Lost, J = Draw, K = Error
		if "!verbose!"=="true" (
			echo %%F 
		)
        if "%%F" == "!PLAYER0!" (
            set /a score0=!score0!+%%G
            set /a won0=!won0!+%%H
            set /a lost0=!lost0!+%%I
            set /a draw0=!draw0!+%%J
            set /a error0=!error0!+%%K
            if %%H == 1 (
                echo [93m!PLAYER0! WIN[0m
            ) else if %%J == 1 (
                echo [92mDRAW[0m
            ) else (
                echo [91m!PLAYER1! WIN[0m
            )
        )
        if "%%F" == "!PLAYER1!" (
            set /a score1=!score1!+%%G
            set /a won1=!won1!+%%H
            set /a lost1=!lost1!+%%I
            set /a draw1=!draw1!+%%J
            set /a error1=!error1!+%%K
		)
	)
)
REM Print the final result
echo ==============================
echo [92mFinal results:[0m
echo !PLAYER0!	 Score: !score0!, Won: !won0!, Lost: !lost0!, Draw: !draw0!, Error: !error0! 
echo !PLAYER1!	 Score: !score1!, Won: !won1!, Lost: !lost1!, Draw: !draw1!, Error: !error1! 

::bell sound for hearing end of batch file
@echo off
PowerShell -Command "Write-Host `a"

echo ============================== >> results.txt
echo !PLAYER0! vs !PLAYER1! Final results: >> results.txt
echo !PLAYER0!	 Score: !score0!, Won: !won0!, Lost: !lost0!, Draw: !draw0!, Error: !error0!  >> results.txt
echo !PLAYER1!	 Score: !score1!, Won: !won1!, Lost: !lost1!, Draw: !draw1!, Error: !error1!  >> results.txt
