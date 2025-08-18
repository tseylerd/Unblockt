// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import tse.unblockt.ls.server.framework.simulateClient
import tse.unblockt.ls.util.rkTest
import tse.unblockt.ls.util.testProjectPath

class SignatureHelpTest {
    @Test
    fun simpleSignature(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            signatureHelp()
        }
    }

    @Test
    fun chooseSimpleSignature(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            signatureHelp()
        }
    }

    @Test
    fun simpleNamedArgument(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            signatureHelp()
        }
    }

    @Test
    fun simpleNamedArgumentWithoutOrder(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            signatureHelp()
        }
    }

    @Test
    fun nextNamedArgumentWithoutOrder(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            signatureHelp()
        }
    }

    @Test
    fun unnamedArgumentAfterNamedArgument(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            signatureHelp()
        }
    }

    @Test
    fun simpleLambdaArgument(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            signatureHelp()
        }
    }

    @Test
    fun genericSignature(info: TestInfo) = rkTest {
        simulateClient(testProjectPath, info) {
            signatureHelp()
        }
    }
}