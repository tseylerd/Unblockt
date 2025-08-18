// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.index.machines

import tse.unblockt.ls.server.analysys.storage.PersistentStorage

object Namespaces {
    val ourKotlinNamespace = PersistentStorage.Namespace("kotlin")
    val ourJavaNamespace = PersistentStorage.Namespace("java")
    val ourNeutralNamespace = PersistentStorage.Namespace("neutral")
}