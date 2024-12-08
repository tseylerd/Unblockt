// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.parameters

import tse.unblockt.ls.protocol.Position
import tse.unblockt.ls.protocol.SignatureHelp
import tse.unblockt.ls.protocol.Uri

interface ParameterHintsService {
    fun analyzePosition(uri: Uri, position: Position): SignatureHelp?
}