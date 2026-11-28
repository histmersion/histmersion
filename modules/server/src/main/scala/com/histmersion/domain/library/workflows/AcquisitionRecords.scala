package com.histmersion
package domain.library.workflows

import scala.compiletime.deferred
import com.histmersion.domain.automation.agents.Checkpointed
import AcquisitionSweepPipeline.{Found, Ingested, Parked, Picked, Tried}

trait AcquisitionRecords:
  given found: Checkpointed[Found] = deferred
  given picked: Checkpointed[List[Picked]] = deferred
  given ingested: Checkpointed[List[Ingested]] = deferred
  given tried: Checkpointed[Tried] = deferred
  given parked: Checkpointed[Parked] = deferred
