// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.parameters

import tse.unblockt.ls.protocol.Position
import tse.unblockt.ls.protocol.SignatureHelp
import tse.unblockt.ls.protocol.Uri

interface ParameterHintsService {
    fun analyzePosition(uri: Uri, position: Position): SignatureHelp?
}