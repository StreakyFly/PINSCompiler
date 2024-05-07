@echo off

:run_test
bash --login -i -c "./test_runner.sh SynAn SynAn"

set /p choice=Do you want to run the test again? (y/n): 
if /i "%choice%"=="y" goto run_test
if /i "%choice%"=="n" goto end

:end
echo Exiting program...
pause
