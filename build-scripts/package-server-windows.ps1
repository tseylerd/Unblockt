Push-Location ..
Write-Output "Building language server..."
& .\gradlew clean jar gatherDependencies

if (Test-Path .\extension\resources\server.txt) {
    Remove-Item -Force .\extension\resources\server.txt
}
if (Test-Path .\extension\resources\server) {
    Remove-Item -Recurse -Force .\extension\resources\server
}
if (Test-Path .\jre) {
    Remove-Item -Recurse -Force .\jre
}

Write-Output "Creating jre..."
& jlink --no-header-files --no-man-pages --add-modules=java.base,java.management,java.desktop,java.sql,jdk.unsupported,jdk.zipfs --output=jre

Write-Output "Packaging language server..."
& jpackage --input .\server\build\jars --name "server" --main-jar "server.jar" --main-class "tse.unblockt.ls.server.UnblocktLanguageServer" --runtime-image jre --type app-image --dest extension\resources --java-options "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" --java-options "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED" --java-options "--enable-preview"

& python build-scripts\modify-classpath-mac.py extension\resources\server\app\server.cfg

Set-Content -Path .\extension\resources\server.txt -Value "resources\server\server.exe"

Pop-Location