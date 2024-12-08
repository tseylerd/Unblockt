// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.declaration

import tse.unblockt.ls.protocol.Location
import tse.unblockt.ls.protocol.Position
import tse.unblockt.ls.protocol.Uri

interface LsGoToDeclarationProvider {
    fun resolve(uri: Uri, location: Position): Location?
}