package com.histmersion.domain.catalogue.periods

/** The PeriodO dump (https://perio.do, CC0) as a network source for `histmersion periods fetch periodo` and
  * `histmersion init --fetch`. The parsing is [[com.histmersion.api.catalogue.PeriodO]] (pure); the download is the
  * composition root's, the same client the gazetteer importers use.
  */
object PeriodOFetch:

  val name = "periodo"
  val description = "PeriodO period definitions (CC0) — the timeline's presets and their authorities"
