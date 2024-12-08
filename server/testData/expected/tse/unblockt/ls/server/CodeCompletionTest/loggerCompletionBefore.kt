package tse.unblockt.ls.server.analysys

import kotlinx.coroutines.coroutineScope
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.analysys.AnalysisEntrypoint
import tse.unblockt.ls.server.analysys.files.isKotlin
import tse.unblockt.ls.server.analysys.files.isSupportedByLanguageServer
import tse.unblockt.ls.server.threading.Cancellable
import java.nio.file.Paths
import kotlin.system.exitProcess

class MyTestClass {
    fun test() {
        <start>logge<caret>
    }
}

//server/src/main/kotlin/tse/unblockt/ls/server/analysys