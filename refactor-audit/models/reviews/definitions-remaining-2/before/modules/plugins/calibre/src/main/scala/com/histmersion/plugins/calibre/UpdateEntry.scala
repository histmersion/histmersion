package com.histmersion.plugins.calibre

final case class UpdateEntry(calibreId: Int, citekeys: List[String], outcome: UpdateOutcome)
