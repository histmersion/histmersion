package com.histmersion.api.catalogue
import com.histmersion.values.PlaceKind
import com.histmersion.api.given

object PlaceShape:
  def isArea(kind: String): Boolean = kind == PlaceKind.Region || kind == PlaceKind.Polity

  def pointOnlyArea(kind: String, hasBoundary: Boolean): Boolean = isArea(kind) && !hasBoundary

  val NoBoundaryNote: String = "point only — boundary not yet available"

  def isPeople(kind: String): Boolean = kind == PlaceKind.People

  val PeopleNote: String = "a people or tribe — placed at a representative point, not an area"
