.\package-server-windows.ps1

Push-Location ..\extension
if (Test-Path .\resources\license) {
    Remove-Item -Force -Recurse .\resources\license
}
if (Test-Path .\LICENSE.md) {
    Remove-Item -Force .\LICENSE.md
}
if (Test-Path unblockt-0.0.1.vsix) {
    Remove-Item -Force unblockt-0.0.1.vsix
}
Copy-Item ..\license -Destination .\resources\license -Recurse
Copy-Item ..\LICENSE.md -Destination .\LICENSE.md
npm install
tsc
vsce package --target win32-x64