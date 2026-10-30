package com.histmersion.api

import com.histmersion.values.PlaceKind

/** What an area-kind place's shape is: a region or a polity is a SHAPE, and one with only a point has no boundary yet
  * (all 1 613 Pleiades regions and the 75 curated ones, 2026-10-09). Drawn as a label, never as an area-like dot, and
  * its card says so. A site, city or landmark is a point by nature.
  */
object PlaceShape:
  /** Is this kind an AREA (region, polity)? */
  def isArea(kind: String): Boolean = kind == PlaceKind.Region || kind == PlaceKind.Polity

  /** An area-kind place drawn from a point only. */
  def pointOnlyArea(kind: String, hasBoundary: Boolean): Boolean = isArea(kind) && !hasBoundary

  /** The card's line for it. */
  val NoBoundaryNote: String = "point only — boundary not yet available"

  /** A people's point is where a source puts the group, not an area it held ([[PlaceKind.People]]). */
  def isPeople(kind: String): Boolean = kind == PlaceKind.People

  /** The card's line for a people. */
  val PeopleNote: String = "a people or tribe — placed at a representative point, not an area"
