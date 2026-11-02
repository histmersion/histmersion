package com.histmersion.api.catalogue
import io.circe.syntax.*
import com.histmersion.api.given

object CorrectionStatus:
  val Active = "active"
  val Agreed = "agreed"
  val Conflict = "conflict"
  val all: List[String] = List(Active, Agreed, Conflict)

  val applied: Set[String] = Set(Active, Conflict)
