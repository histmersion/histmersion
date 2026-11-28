package com.histmersion
package domain.library.workflows

import scala.compiletime.deferred
import com.histmersion.domain.automation.agents.Checkpointed

trait EnrichRecords:
  given loaded: Checkpointed[Loaded] = deferred
  given metaChecked: Checkpointed[MetaChecked] = deferred
  given classified: Checkpointed[Classified] = deferred
  given findings: Checkpointed[Findings] = deferred
  given verifiedFindings: Checkpointed[VerifiedFindings] = deferred
  given geocoded: Checkpointed[Geocoded] = deferred
  given patched: Checkpointed[Patched] = deferred
  given upstreamed: Checkpointed[Upstreamed] = deferred
  given statusRouted: Checkpointed[StatusRouted] = deferred
  given closed: Checkpointed[Closed] = deferred
  given notesText: Checkpointed[String] = deferred
