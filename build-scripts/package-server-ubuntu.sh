#!/bin/bash

pushd ..
echo "Building language server..."
./gradlew clean jar gatherDependencies

rm extension/resources/server.txt
rm -r jre

rm -r extension/resources/server

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
  --dest extension/resources \
  --java-options "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.io=ALL-UNNAMED" \
  --java-options "--enable-preview"
python3 build-scripts/modify-classpath-mac.py extension/resources/server/lib/app/server.cfg
echo -n "resources/server/bin/server" > extension/resources/server.txt
popd