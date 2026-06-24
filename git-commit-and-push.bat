@echo off
REM Use repository root as working dir
cd /d "%~dp0"

REM Check that git is available
git --version >nul 2>&1
if errorlevel 1 (
  echo Git not found in PATH.
  exit /b 1
)

REM Commit using existing COMMIT_EDITMSG if present, otherwise prompt for a message
if exist ".git\COMMIT_EDITMSG" (
  echo Committing using .git\COMMIT_EDITMSG
  git commit -F ".git\COMMIT_EDITMSG"
) else (
  set /p commitMsg=Enter commit message: 
  git commit -m "%commitMsg%"
)

if errorlevel 1 (
  echo Commit failed.
  exit /b 1
)

echo Pushing to origin
git push
if errorlevel 1 (
  echo Push failed.
  exit /b 1
)
echo Done.
