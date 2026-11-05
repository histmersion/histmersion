package com.histmersion.domain.library.sources

import com.histmersion.domain.library.Source
import com.histmersion.util.database.ColumnProbe
import com.histmersion.util.database.given

class SourceColumnsSuite extends munit.FunSuite:
  test("stored source statuses decode trimmed and case-insensitive"):
    assertEquals(ColumnProbe.read[Source.Status](" CURRENT "), Source.Status.Current)
  test("unknown and null statuses remain unassessed"):
    assertEquals(ColumnProbe.read[Source.Status]("bogus"), Source.Status.Unassessed)
    assertEquals(ColumnProbe.read[Source.Status](null), Source.Status.Unassessed)
  test("source status writes its storage spelling"):
    assertEquals(ColumnProbe.write[Source.Status](Source.Status.Current), Some("current"))
