package com.histmersion
package domain.library.workflows

import com.histmersion.util.json.JsonCodec.given
import com.histmersion.domain.automation.agents.Checkpointed
import com.histmersion.api.library.workflows as wire
import AcquisitionSweepPipeline.{Found, Ingested, Parked, Picked, Tried}

object AcquisitionStepTransformers extends AcquisitionRecords, TwinTransformers:
  override given found: Checkpointed[Found] = stored[Found, wire.DiscoveredWorks]
  override given picked: Checkpointed[List[Picked]] = stored[List[Picked], List[wire.PickedWork]]
  override given ingested: Checkpointed[List[Ingested]] = stored[List[Ingested], List[wire.IngestedWork]]
  override given tried: Checkpointed[Tried] = stored[Tried, wire.TriedIngests]
  override given parked: Checkpointed[Parked] = stored[Parked, wire.ParkedPicks]
