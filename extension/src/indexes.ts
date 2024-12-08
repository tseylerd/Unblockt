// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

import {randomUUID} from 'crypto';
import * as vscode from 'vscode';
import {LanguageClient, RequestType, WorkDoneProgress, WorkDoneProgressParams,} from 'vscode-languageclient/node';

const REBUILD_REQUEST_TYPE = new RequestType<RebuildParams, boolean, void>("workspace/rebuildIndexes")

export const REBUILD_INDEXES_COMMAND = "unblockt.rebuild.indexes.command"

export function initializeIndexes(context: vscode.ExtensionContext, client: LanguageClient) {
    context.subscriptions.push(vscode.commands.registerCommand(REBUILD_INDEXES_COMMAND, () => {
        rebuildIndexes(client)
    }))
}

function rebuildIndexes(client: LanguageClient) {
    const token = randomUUID()
    vscode.window.withProgress({
        location: vscode.ProgressLocation.Window
    }, async (progress) => {
        progress.report({ message: 'Rebuilding indexes...' })
        client.onProgress(WorkDoneProgress.type, token, (params) => {
            const message = params.message
            if (message) {
                progress.report({ message: message })
            }
        })
        await client.sendRequest(REBUILD_REQUEST_TYPE, {
            workDoneToken: token
        })
    })
}

interface RebuildParams extends WorkDoneProgressParams { }