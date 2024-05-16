@echo off

:: check if a phase name has been provided as an argument
if "%~1"=="" (
    :: folder with tests MUST have the exact same name as the phase
    echo No phase name provided. Please provide the name of the phase you want to test.
    exit /b
)

:: store the folder name in a variable
set "PHASE_NAME=%~1"

:: run the Python script to convert Windows' newline characters to Linux's newline characters
echo Converting Windows' newline characters to Linux's in folder %PHASE_NAME%.
python convert_new_line.py "%PHASE_NAME%"
echo. 

:run_test
bash --login -i -c "./test_runner.sh %PHASE_NAME% %PHASE_NAME%"

set /p choice=Do you want to run the test again? (y/n): 
if /i "%choice%"=="y" goto run_test
if /i "%choice%"=="n" goto end

:end
echo Exiting program...
pause
