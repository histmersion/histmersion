package com.histmersion.api

import io.circe.syntax.*

object CorrectionTarget:
  val Event = "event"
  val Place = "place"
  val all: List[String] = List(Event, Place)
