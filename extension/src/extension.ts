// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

import {ExtensionContext, ExtensionMode, workspace} from 'vscode';

import {LanguageClient, LanguageClientOptions, ServerOptions, TransportKind} from 'vscode-languageclient/node';
import {initializeBuildSystem} from './project';
import {initializeIndexes} from './indexes';
import {createStatusBar} from './status';
import {readFileSync} from 'node:fs';
import * as os from "node:os";

let client: LanguageClient;
const defaultMemory = '-Xmx4096m'

export function activate(context: ExtensionContext) {
    const isProduction = context.extensionMode == ExtensionMode.Production
    const relativeExecutablePath = isProduction ? readProductionFile(context) : debugPathToServer();
    const serverExecutable = context.asAbsolutePath(relativeExecutablePath)
    const logPath = context.logUri.path
    const configuration = workspace.getConfiguration("unblockt")
    const memory = configuration ? configuration.get("memory") : undefined
    const xmx = memory ? '-Xmx' + memory + "m" : defaultMemory
    const serverOptions: ServerOptions = {
        run: {
            command: serverExecutable,
            args: ['"' + logPath + '"'],
            transport: TransportKind.stdio,
            options: {
                shell: true,
                env: {
                    JAVA_TOOL_OPTIONS: xmx
                }
            }
        },
        debug: {
            command: serverExecutable,
            args: ['"' + logPath + '"'],
            transport: TransportKind.stdio,
            options: {
                shell: true,
                env: {
                    JAVA_OPTS: xmx
                }
            }
        } 
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'kotlin' }],
        progressOnInitialization: true,
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/*.kt')
        },
        initializationOptions: {
            storagePath: context.storageUri.path,
            globalStoragePath: context.extensionPath
        }
    };

    client = new LanguageClient(
        'unblockt',
        'Unblockt',
        serverOptions,
        clientOptions
    );
    
    initializeBuildSystem(context, client)
    initializeIndexes(context, client)
    createStatusBar(context, client)

    client.start();
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}

function debugPathToServer(): string {
    const platform = os.platform()
    return platform == 'win32' ? "../server/build/install/server/bin/server.bat" : "../server/build/install/server/bin/server"
}

function readProductionFile(context: ExtensionContext): string {
    const serverConfig = context.asAbsolutePath("resources/server.txt")
    return readFileSync(serverConfig, 'utf8')
}
