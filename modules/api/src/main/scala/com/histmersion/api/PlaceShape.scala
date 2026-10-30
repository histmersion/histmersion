package com.histmersion.api

import com.histmersion.values.PlaceKind

object PlaceShape:
  def isArea(kind: String): Boolean = kind == PlaceKind.Region || kind == PlaceKind.Polity

  def pointOnlyArea(kind: String, hasBoundary: Boolean): Boolean = isArea(kind) && !hasBoundary

  val NoBoundaryNote: String = "point only — boundary not yet available"

  def isPeople(kind: String): Boolean = kind == PlaceKind.People

  val PeopleNote: String = "a people or tribe — placed at a representative point, not an area"
