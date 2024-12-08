#!/bin/bash

./package-server-ubuntu.sh

pushd ../extension
rm -r resources/license
rm LICENSE.md
cp -r ../license resources/license
cp ../LICENSE.md LICENSE.md

rm unblockt-0.0.1.vsix
npm install
npm i --save-dev esbuild
tsc
vsce package --target linux-x64