package com.histmersion.api

import io.circe.syntax.*

object CorrectionStatus:
  val Active = "active"
  val Agreed = "agreed"
  val Conflict = "conflict"
  val all: List[String] = List(Active, Agreed, Conflict)

  val applied: Set[String] = Set(Active, Conflict)
