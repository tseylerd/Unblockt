// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

import * as vscode from 'vscode';
import {LanguageClient,} from 'vscode-languageclient/node';
import {RELOAD_GRADLE_PROJECT_COMMAND} from './project';
import {REBUILD_INDEXES_COMMAND} from './indexes';

const SHOW_ACTIONS_COMMAND_ID = "unblockt.show.actions.command"
const RELOAD_GRADLE_PROJECT_LABEL = "Reload Gradle Project";
const RELOAD_INDEXES_LABEL = "Rebuild Indexes";

const unblocktStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
let isLoading = false

export function createStatusBar(context: vscode.ExtensionContext, client: LanguageClient): vscode.StatusBarItem {
    unblocktStatusBar.command = SHOW_ACTIONS_COMMAND_ID
    context.subscriptions.push(vscode.commands.registerCommand(SHOW_ACTIONS_COMMAND_ID, () => {
        if (!isLoading) {
            showActionChooser()
        }
	}));
    unblocktStatusBar.show()
    context.subscriptions.push(unblocktStatusBar)

    loading()
    subscribeOnHealthStatus(context, client)
    return unblocktStatusBar
}

export function subscribeOnHealthStatus(context: vscode.ExtensionContext, client: LanguageClient) {
    const updateStatusDisposable = client.onNotification("unblockt/status", (value) => {
        updateStatusBarItem(unblocktStatusBar, value)
    })  
	context.subscriptions.push(updateStatusDisposable);
}

function updateStatusBarItem(statusBarItem: vscode.StatusBarItem, info: HealthStatusInfo): void {
    const background = info.status === HealthStatus.error ? new vscode.ThemeColor('statusBarItem.errorBackground') : 
                          info.status === HealthStatus.warning ? new vscode.ThemeColor('statusBarItem.warningBackground') :
                          new vscode.ThemeColor('statusBarItem.prominentBackground');
    const foreground = info.status === HealthStatus.error ? new vscode.ThemeColor('statusBarItem.errorForeground') : 
                          info.status === HealthStatus.warning ? new vscode.ThemeColor('statusBarItem.warningForeground') :
                          new vscode.ThemeColor('statusBarItem.prominentForeground');
    statusBarItem.backgroundColor = background
    statusBarItem.color = foreground
    statusBarItem.text = info.text
    statusBarItem.tooltip = info.message
    isLoading = false
}

function showActionChooser() {
    const options = [] 
	options.push({ label: RELOAD_GRADLE_PROJECT_LABEL, description: "Reloads Gradle project" });
	options.push({ label: RELOAD_INDEXES_LABEL, description: "Deletes all indexes and builds them from scratch" });

	vscode.window.showQuickPick(options).then((item) => {
        if (item) {
			switch (item.label) {
				case RELOAD_GRADLE_PROJECT_LABEL:
					vscode.commands.executeCommand(RELOAD_GRADLE_PROJECT_COMMAND).then(() => {
                        isLoading = false
                    });
					break;
				case RELOAD_INDEXES_LABEL:
					vscode.commands.executeCommand(REBUILD_INDEXES_COMMAND).then(() => {
                        isLoading = false
                    });
					break;
			}
		}
    })
}

function loading() {
    isLoading = true
    unblocktStatusBar.color = new vscode.ThemeColor('statusBarItem.prominentForeground')
    unblocktStatusBar.backgroundColor = new vscode.ThemeColor('statusBarItem.prominentBackground')
    unblocktStatusBar.text = "$(loading) Loading..."
    unblocktStatusBar.tooltip = "Loading project..."
}

interface HealthStatusInfo {
    text: string
    message: string
    status: HealthStatus
}

enum HealthStatus {
    healthy = "HEALTHY",
    warning = "WARNING",
    error = "ERROR",
    message = "MESSAGE"
}