package com.atvriders.wsprtxrx.data.source

import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.model.SpotQuery

/**
 * A network that supplies reception spots. Implementations must never throw: failures
 * are returned as [Result.failure] so one unreachable source cannot break the others.
 */
interface SpotSource {
    val id: SourceId
    suspend fun query(q: SpotQuery): Result<List<Spot>>
}
