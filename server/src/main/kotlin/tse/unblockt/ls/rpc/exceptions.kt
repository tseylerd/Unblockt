// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.rpc

import kotlinx.coroutines.CancellationException

data class CancellationWithResult(val result: Any): CancellationException()