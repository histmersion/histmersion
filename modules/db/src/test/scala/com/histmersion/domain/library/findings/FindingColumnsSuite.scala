package com.histmersion.domain.library.findings

import com.histmersion.domain.library.findings.FindingColumns.given

import com.histmersion.domain.library.Confidence
import com.histmersion.util.database.ColumnProbe
import com.histmersion.util.database.given

class FindingColumnsSuite extends munit.FunSuite:
  test("stored confidence decodes trimmed and case-insensitive"):
    assertEquals(ColumnProbe.read[Option[Confidence]](" LOW "), Some(Confidence.Low))
  test("unknown and null confidence remain absent"):
    assertEquals(ColumnProbe.read[Option[Confidence]]("certain"), None)
    assertEquals(ColumnProbe.read[Option[Confidence]](null), None)
  test("confidence writes its storage spelling or SQL null"):
    assertEquals(ColumnProbe.write[Option[Confidence]](Some(Confidence.Low)), Some("low"))
    assertEquals(ColumnProbe.write[Option[Confidence]](None), None)
