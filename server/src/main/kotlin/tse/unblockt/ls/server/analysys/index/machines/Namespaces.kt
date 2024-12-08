// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import tse.unblockt.ls.server.analysys.storage.PersistentStorage

object Namespaces {
    val ourKotlinNamespace = PersistentStorage.Namespace("kotlin")
    val ourJavaNamespace = PersistentStorage.Namespace("java")
    val ourNeutralNamespace = PersistentStorage.Namespace("neutral")
}