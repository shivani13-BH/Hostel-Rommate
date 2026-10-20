@echo off
rem Compile and run the Module 3 (matching engine) tests (Windows).
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac -encoding UTF-8 -d out module4-database\src\com\hostelmate\database\*.java module3-matching-engine\src\com\hostelmate\engine\*.java module2-backend-api\src\com\hostelmate\api\*.java module3-matching-engine\test\com\hostelmate\engine\*.java
if errorlevel 1 (pause & exit /b 1)
java -cp out com.hostelmate.engine.MatchingEngineTest
pause
