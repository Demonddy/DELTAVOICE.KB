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
  REM clear any previous value
  set "firstLine="
  REM get first non-comment, non-empty line via PowerShell
  for /f "usebackq delims=" %%L in (`powershell -NoProfile -Command "Get-Content '.git/COMMIT_EDITMSG' | Where-Object {$_ -notmatch '^\s*#' -and $_.Trim() -ne ''} | Select-Object -First 1"`) do set "firstLine=%%L"
  if defined firstLine (
    echo Committing using .git\COMMIT_EDITMSG
    git commit -F ".git\COMMIT_EDITMSG"
  ) else (
    set /p commitMsg=COMMIT_EDITMSG is empty or has only comments. Enter commit message: 
    git commit -m "%commitMsg%"
  )
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
