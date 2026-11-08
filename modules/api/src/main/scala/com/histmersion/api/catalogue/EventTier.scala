package com.histmersion
package api.catalogue
import com.histmersion.api.given
object EventTier:

  val Attested = "attested"
  val Catalogue = "catalogue"
  val all: List[EventSummary.Tier] = List(EventSummary.Tier(Attested), EventSummary.Tier(Catalogue))
