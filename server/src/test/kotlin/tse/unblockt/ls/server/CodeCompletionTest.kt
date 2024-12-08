// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server

import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.protocol.CompletionItem
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.selfPath
import tse.unblockt.ls.util.testProjectPath

class CodeCompletionTest {
    @Test
    fun completionPerformance(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            withTimeout(5000L) {
                complete {
                    it.find { i -> i.label == "File" }
                }
            }
        }
    }

    @Test
    fun qualifiedCompletion(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete {
                it.find { i -> i.insertText == "File" }
            }
        }
    }

    @Test
    fun keywordCompletion(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun fileCompletion(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete {
                it.find { i -> i.label == "File" }
            }
        }
    }

    @Test
    fun secondFileCompletion(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete {
                it.find { i -> i.label == "File" }
            }
        }
    }

    @Test
    fun valKeywordCompletion(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun importByDefault(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete {
                it.find { it.label == "Thread" }
            }
        }
    }

    @Test
    fun classReference(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun simpleFunction(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun functionWithImport(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun functionWithQualifier(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun javaFunction(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun javaGetterAsKotlinProperty(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            completionItems()
        }
    }

    @Test
    fun extensionFunction(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            completionItems()
        }
    }

    @Test
    fun extensionFunctionWithImport(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun functionReturnType(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun singleLambda(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun noParametersFunction(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun functionWithContextReceiver(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            completionItems()
        }
    }

    @Test
    fun functionWithParametersFromAnotherPackage(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun functionWithDefaultParametersAndLambda(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun stringCompletion(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            complete()
        }
    }

    @Test
    fun loggerCompletionItems(info: TestInfo) {
        rkTest {
            simulateClient(selfPath, info) {
                completionItems()
            }
        }
    }

    @Test
    fun loggerCompletion(info: TestInfo) {
        rkTest {
            simulateClient(selfPath, info) {
                complete { items ->
                    findLog4jLoggerProperty(items)
                }
            }
        }
    }

    @Test
    fun loggerAndManyImports(info: TestInfo) {
        rkTest {
            simulateClient(selfPath, info) {
                complete {
                    findLog4jLoggerProperty(it)
                }
            }
        }
    }

    @Test
    fun autoimportInTheBeginningOfTheFile(info: TestInfo) {
        rkTest {
            simulateClient(testProjectPath, info) {
                complete {
                    it.find { i -> i.label == "File" }
                }
            }
        }
    }

    private fun findLog4jLoggerProperty(items: List<CompletionItem>) = items.first {
        it.labelDetails?.detail == " org.apache.logging.log4j.kotlin" &&
                it.labelDetails?.description == "KotlinLogger" &&
                it.insertText == "logger"
    }
}