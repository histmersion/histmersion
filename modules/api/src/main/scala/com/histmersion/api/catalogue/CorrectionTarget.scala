package com.histmersion.api.catalogue
import io.circe.syntax.*
import com.histmersion.api.given

object CorrectionTarget:
  val Event = "event"
  val Place = "place"
  val all: List[String] = List(Event, Place)
