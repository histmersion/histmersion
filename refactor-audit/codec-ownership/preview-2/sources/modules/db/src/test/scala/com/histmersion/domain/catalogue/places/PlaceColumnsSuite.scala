package com.histmersion.domain.catalogue.places

import com.histmersion.domain.catalogue.places.PlaceColumns.given

import com.histmersion.domain.catalogue.Place
import com.histmersion.util.database.ColumnProbe
import com.histmersion.util.database.given

class PlaceColumnsSuite extends munit.FunSuite:
  test("stored precision decodes trimmed and case-insensitive"):
    assertEquals(ColumnProbe.read[Place.Precision](" GRID-CELL "), Place.Precision.GridCell)
  test("null means stated while an unknown precision stays conservative"):
    assertEquals(ColumnProbe.read[Place.Precision](null), Place.Precision.Stated)
    assertEquals(ColumnProbe.read[Place.Precision]("unknown"), Place.Precision.GridCell)
  test("precision writes grid-cell or SQL null for stated"):
    assertEquals(ColumnProbe.write[Place.Precision](Place.Precision.GridCell), Some("grid-cell"))
    assertEquals(ColumnProbe.write[Place.Precision](Place.Precision.Stated), None)
