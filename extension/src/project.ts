// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

import {randomUUID} from 'crypto';
import * as vscode from 'vscode';
import {LanguageClient, RequestType, WorkDoneProgress, WorkDoneProgressParams,} from 'vscode-languageclient/node';

const OUTPUT_CHANNEL_NAME = "Gradle by Unblockt"
const RELOAD_REQUEST_TYPE = new RequestType<ReloadParams, boolean, void>("buildSystem/reload")
export const RELOAD_GRADLE_PROJECT_COMMAND = "unblockt.reload.project.command"

export function initializeBuildSystem(context: vscode.ExtensionContext, client: LanguageClient): vscode.OutputChannel {
    const channel = vscode.window.createOutputChannel(OUTPUT_CHANNEL_NAME, {log:true})
    context.subscriptions.push(vscode.commands.registerCommand(RELOAD_GRADLE_PROJECT_COMMAND, () => {
        reloadProject(client, channel)
    }))
    const disposable = client.onNotification("unblockt/messages/gradle/message", (value) => {
        const type = value.type
        if (type == 'ERROR') {
            channel.error(value.value)
        } else {
            channel.info(value.value)
        }
    })
    context.subscriptions.push(disposable)
    return channel
}

function reloadProject(client: LanguageClient, channel: vscode.OutputChannel) {
    const token = randomUUID()
    channel.show(true)
    vscode.window.withProgress({
        location: vscode.ProgressLocation.Window
    }, async (progress) => {
        progress.report({ message: 'Reloading project...' })
        client.onProgress(WorkDoneProgress.type, token, (params) => {
            const message = params.message
            if (message) {
                progress.report({ message: message })
            }
        })
        await client.sendRequest(RELOAD_REQUEST_TYPE, {
            workDoneToken: token
        })
    })
}

interface ReloadParams extends WorkDoneProgressParams { }
