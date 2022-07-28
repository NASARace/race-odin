@REM script to run odin under Windows

@echo off

set "SCRIPT_DIR=%~dp0..\target\universal\stage\bin"

"%SCRIPT_DIR%\race-odin.bat" %*
