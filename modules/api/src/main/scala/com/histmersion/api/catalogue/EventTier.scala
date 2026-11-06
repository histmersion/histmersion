package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventTier:

  val Attested = "attested"
  val Catalogue = "catalogue"
  val all: List[com.histmersion.api.catalogue.EventSummary.Tier] = List(_root_.com.histmersion.api.catalogue.EventSummary.Tier(Attested), _root_.com.histmersion.api.catalogue.EventSummary.Tier(Catalogue))
