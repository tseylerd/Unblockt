![Version Badge](https://img.shields.io/badge/version-0.0.3-red)
![License Badge](https://img.shields.io/badge/license-PolyForm_Perimeter_License_1.0.0-green)
## Kotlin Language Support for Visual Studio Code
**Note:** This extension is in its early stages and does not yet support all build systems or Kotlin features. It may also be unstable — please report any issues or unexpected behavior you experience.
For more details, see the [limitations](#limitations) section.

Your feedback is invaluable — please [report](https://github.com/tseylerd/Unblockt/issues) any issues you encounter.
## Getting started
1. Open any `.kt` or `.kts` file to activate the extension.

Upon activation, the extension will:
- Launch the language server
- Read the project structure using Gradle
- Index project files to enable code insight features

Once indexing is complete, you're ready to code!

## Basics
- **Memory Widget:** A widget in the bottom right corner displays current memory usage.  
  ![Memory widget](images/ui/memoryWidget.png)
- **Unblockt Actions:** Click the widget to access a list of actions.

## Requirements
- **Java**
- **Memory:** Minimum 4GB, 8GB recommended

## Settings
- **Memory:** Configure the language server's heap size (in MB). Restart required.  
  ![Memory settings](images/ui/memorySettings.png)

## Features
- **Semantic Highlighting**  
  ![Semantic highlighting](images/code/highlighting.png)
- **Code Completion**  
  ![Code completion](images/code/codeCompletion.gif)
- **Go to Definition**  
  ![Go to definition](images/code/goToDefinition.gif)

## Actions
- **Reload Gradle Project:** Updates project structure from Gradle and applies changes.
- **Rebuild Indexes:** Clears and rebuilds project indexes.

## Limitations
- Only Gradle projects are supported.
- Only default source code locations are supported for source sets.
- Code analysis for Gradle build scripts is not available.
- Gradle Kotlin plugin version 2.1.0 or later is required for non-JVM projects.

## Roadmap
Future features will depend on user feedback. Currently planned:
- Find Usages
- Rename Refactoring
- Standalone Language Server

## Feedback
Please, use [GitHub Issues](https://github.com/tseylerd/Unblockt/issues) to report any feedback.

## Our Goal
We want developers to enjoy working with Kotlin without having to change their habits.  
To achieve this, we aim to create yet another high-quality tool for Kotlin development, including a standalone Language Server implementation.

## Open source software used
- [Kotlin](https://github.com/JetBrains/kotlin)
- [IntelliJ IDEA Community Edition](https://github.com/JetBrains/intellij-community)
- [MapDB](https://github.com/jankotek/mapdb)
- [Caffeine](https://github.com/ben-manes/caffeine)
- [lz4java](https://github.com/lz4/lz4-java)
- [DefinitelyTyped](https://github.com/DefinitelyTyped/DefinitelyTyped)
- [esbuild](https://github.com/evanw/esbuild)
- [Gradle Tooling](https://github.com/gradle/gradle)
- [Log4j2](https://github.com/apache/logging-log4j2)
- [System Stubs](https://github.com/webcompere/system-stubs)
- [vscode-languageserver-node](https://github.com/Microsoft/vscode-languageserver-node)