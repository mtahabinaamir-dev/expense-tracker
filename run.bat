@echo off
REM Compiles and launches the Expense Tracker. Run from this folder.
REM Edit the line below so JAVAFX points at your JavaFX SDK lib folder.
set JAVAFX=C:\javafx-sdk-21\lib
if not exist out mkdir out
javac --module-path "%JAVAFX%" --add-modules javafx.controls -d out src\*.java
if errorlevel 1 exit /b 1
copy /Y src\style.css out\ >nul
java --module-path "%JAVAFX%" --add-modules javafx.controls -cp out ExpenseTracker
