// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.declaration

import tse.unblockt.ls.protocol.Location
import tse.unblockt.ls.protocol.Position
import tse.unblockt.ls.protocol.Uri

interface LsGoToDeclarationProvider {
    fun resolve(uri: Uri, location: Position): Location?
}