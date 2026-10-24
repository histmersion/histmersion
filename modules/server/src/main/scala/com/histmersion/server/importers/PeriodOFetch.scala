package com.histmersion.server.importers

/** The PeriodO dump (https://perio.do, CC0) as a network source for `histmersion periods fetch
  * periodo` and `histmersion init --fetch`. The parsing is [[com.histmersion.api.PeriodO]] (pure);
  * this is only the download, through the same client the gazetteer importers use. */
object PeriodOFetch:

  val name        = "periodo"
  val description = "PeriodO period definitions (CC0) — the timeline's presets and their authorities"

  def download(url: String): Either[String, String] = ImportRunner.download(url)
