package com.histmersion
package domain.library.workflows

import scala.compiletime.deferred
import com.histmersion.domain.automation.agents.Stored

trait EnrichRecords:
  given loaded: Stored[Loaded] = deferred
  given metaChecked: Stored[MetaChecked] = deferred
  given classified: Stored[Classified] = deferred
  given findings: Stored[Findings] = deferred
  given verifiedFindings: Stored[VerifiedFindings] = deferred
  given geocoded: Stored[Geocoded] = deferred
  given patched: Stored[Patched] = deferred
  given upstreamed: Stored[Upstreamed] = deferred
  given statusRouted: Stored[StatusRouted] = deferred
  given closed: Stored[Closed] = deferred
  given notesText: Stored[String] = deferred
