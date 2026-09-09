@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"

echo.
echo ================================================================
echo   SUBIR INVENTARIO ANDROID A GITHUB (todo automatico)
echo ================================================================
echo.
echo Este script hara todo por ti:
echo   1) Instala Git y GitHub CLI si te faltan (con winget)
echo   2) Te pide autenticarte con GitHub (abre navegador)
echo   3) Crea el repositorio privado
echo   4) Sube todos los archivos
echo   5) GitHub compila el APK solo, tu solo esperas 5 minutos
echo.

set /p GHUSER=Escribe tu nombre de usuario de GitHub y ENTER:
if "!GHUSER!"=="" (
    echo Cancelado, no escribiste usuario.
    pause & exit /b 1
)

REM -----------------------------------------------------------------
REM  1. GIT
REM -----------------------------------------------------------------
where git >nul 2>&1
if errorlevel 1 (
    echo.
    echo [Paso 1/5] Git no esta instalado. Instalando...
    winget install --id Git.Git -e --silent --accept-source-agreements --accept-package-agreements
    if errorlevel 1 (
        echo.
        echo ERROR: instala Git manualmente desde https://git-scm.com/download/win
        echo y vuelve a ejecutar este .bat despues.
        pause & exit /b 1
    )
    echo.
    echo Git instalado. CIERRA esta ventana y vuelve a hacer doble clic al .bat.
    pause & exit /b 0
)

REM -----------------------------------------------------------------
REM  2. GITHUB CLI
REM -----------------------------------------------------------------
where gh >nul 2>&1
if errorlevel 1 (
    echo.
    echo [Paso 2/5] GitHub CLI no esta instalado. Instalando...
    winget install --id GitHub.cli -e --silent --accept-source-agreements --accept-package-agreements
    if errorlevel 1 (
        echo.
        echo ERROR: instala GitHub CLI desde https://cli.github.com/
        echo y vuelve a ejecutar este .bat despues.
        pause & exit /b 1
    )
    echo.
    echo GitHub CLI instalado. CIERRA esta ventana y vuelve a hacer doble clic al .bat.
    pause & exit /b 0
)

REM -----------------------------------------------------------------
REM  3. LOGIN
REM -----------------------------------------------------------------
gh auth status >nul 2>&1
if errorlevel 1 (
    echo.
    echo [Paso 3/5] Autenticacion con GitHub.
    echo Se abrira el navegador. Elige:
    echo   - GitHub.com
    echo   - HTTPS
    echo   - Autenticar con navegador (Y)
    echo   - Copia el codigo que te muestra, pegalo en el navegador, y autoriza.
    echo.
    pause
    gh auth login -h github.com -p https -w
    if errorlevel 1 (
        echo ERROR en autenticacion. Reintenta.
        pause & exit /b 1
    )
)

REM -----------------------------------------------------------------
REM  4. CONFIG GIT LOCAL
REM -----------------------------------------------------------------
git config --global user.name >nul 2>&1
if errorlevel 1 git config --global user.name "!GHUSER!"
git config --global user.email >nul 2>&1
if errorlevel 1 git config --global user.email "!GHUSER!@users.noreply.github.com"

REM -----------------------------------------------------------------
REM  5. INIT + COMMIT + PUSH
REM -----------------------------------------------------------------
echo.
echo [Paso 4/5] Preparando archivos...
if not exist ".git" (
    git init -b main >nul
)
git add .
git diff --cached --quiet
if errorlevel 1 (
    git commit -m "primer push" >nul
) else (
    echo   (no hay cambios nuevos)
)

echo.
echo [Paso 5/5] Creando repositorio en GitHub y subiendo archivos...
gh repo create inventario-android --private --source=. --push --remote=origin 2>nul
if errorlevel 1 (
    echo   (repo ya existe, actualizando)
    git remote remove origin 2>nul
    git remote add origin https://github.com/!GHUSER!/inventario-android.git
    git branch -M main
    gh auth setup-git >nul 2>&1
    git push -u origin main --force
    if errorlevel 1 (
        echo ERROR al subir. Revisa que el usuario "!GHUSER!" sea correcto.
        pause & exit /b 1
    )
)

echo.
echo ================================================================
echo  LISTO! Abriendo GitHub Actions en el navegador...
echo  Espera 4-6 minutos hasta ver el check verde.
echo  El APK aparece en la seccion "Releases" (columna derecha).
echo ================================================================
start https://github.com/!GHUSER!/inventario-android/actions
echo.
pause
