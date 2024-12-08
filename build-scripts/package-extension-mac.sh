#!/bin/zsh

./package-server-mac.sh

pushd ../extension
rm -r resources/license
rm LICENSE.md
cp -r ../license resources/license
cp ../LICENSE.md LICENSE.md

rm unblockt-0.0.1.vsix
npm install
tsc
vsce package --target darwin-x64