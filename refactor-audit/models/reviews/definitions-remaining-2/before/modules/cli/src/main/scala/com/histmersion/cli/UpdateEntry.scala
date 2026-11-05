package com.histmersion.cli

import scala.jdk.CollectionConverters.*

final case class UpdateEntry(calibreId: Int, citekeys: List[String], outcome: UpdateOutcome)
