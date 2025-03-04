#!/bin/zsh

pushd ..
echo "Building language server..."
./gradlew clean jar gatherDependencies
rm -r server.app/
rm extension/resources/server.txt
rm -r extension/resources/server
rm -r jre
mkdir extension/resources/server
echo "Running jlink..."
jlink --no-header-files --no-man-pages --add-modules=java.base,java.management,java.desktop,java.sql,jdk.unsupported,jdk.zipfs --output=jre
JRE_PATH="$(pwd)"/jre
echo "$JRE_PATH"

echo "Packaging language server..."
jpackage --input ./server/build/jars \
  --name "server" \
  --main-jar "server.jar" \
  --main-class "tse.unblockt.ls.server.UnblocktLanguageServer" \
  --runtime-image "$JRE_PATH" \
  --type app-image \
  --dest extension/resources/server \
  --java-options "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" \
  --java-options "--enable-preview"
python3 build-scripts/modify-classpath-mac.py extension/resources/server/server.app/Contents/app/server.cfg
echo -n "resources/server/server.app/Contents/MacOS/server" > extension/resources/server.txt
popd