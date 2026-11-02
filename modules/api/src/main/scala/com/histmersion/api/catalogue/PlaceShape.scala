package com.histmersion.api.catalogue
import com.histmersion.api.given

object PlaceShape:
  def isArea(kind: String): Boolean =
    kind == com.histmersion.domain.catalogue.Place.Kind.Region || kind == com.histmersion.domain.catalogue.Place.Kind.Polity

  def pointOnlyArea(kind: String, hasBoundary: Boolean): Boolean = isArea(kind) && !hasBoundary

  val NoBoundaryNote: String = "point only — boundary not yet available"

  def isPeople(kind: String): Boolean = kind == com.histmersion.domain.catalogue.Place.Kind.People

  val PeopleNote: String = "a people or tribe — placed at a representative point, not an area"
