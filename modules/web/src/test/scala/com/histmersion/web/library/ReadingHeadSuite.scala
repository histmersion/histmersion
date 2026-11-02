package com.histmersion.web.library

import com.histmersion.api.library.{FindingRow, SearchHit, SourceDetail, SourceWithFindings}
import com.histmersion.api.media.MediaThumb
import com.histmersion.values.SourceStatus
import com.histmersion.domain.media.MediaRole
import com.histmersion.web.map.GeoBounds

class ReadingHeadSuite extends munit.FunSuite:

  private val detail = SourceDetail(
    "cline2014_year",
    "1177 B.C.",
    List("Eric H. Cline"),
    Some(2014),
    Some("Princeton University Press"),
    SourceStatus.Current,
    Nil,
    List("levant", "anatolia"),
    Nil,
    Some(-1250),
    Some(-1100),
    Some(" Several pressures together. "),
    None,
    None,
    provenance = Some("calibre")
  )

  test("from the table's row at once: year, title, authors, status, period, regions, summary"):
    val h = ReadingHead.of(
      SearchHit(
        "cline2014_year",
        "1177 B.C.",
        Some(2014),
        SourceStatus.Current,
        Nil,
        List("levant"),
        Some(-1250),
        Some(-1100),
        Some("Sum."),
        authors = List("Eric H. Cline")
      )
    )
    assertEquals(
      h,
      ReadingHead(
        "2014",
        "1177 B.C.",
        "Eric H. Cline",
        Some(SourceStatus.Current),
        "1250 BCE–1100 BCE",
        "levant",
        Some("Sum.")
      )
    )

  test("once read: type · venue · year, the regions in words, the summary trimmed"):
    val h = ReadingHead.of(SourceWithFindings(detail, Nil))
    assertEquals(h.what, "Book · Princeton University Press · 2014")
    assertEquals(h.regions, "levant · anatolia")
    assertEquals(h.summary, Some("Several pressures together."))

  test("an unknown type is left out, not called unknown"):
    val paperless = detail.copy(provenance = None, venue = None)
    assertEquals(ReadingHead.of(SourceWithFindings(paperless, Nil)).what, "2014")
    val cover = MediaThumb("u", MediaRole.Cover, "/x.jpg", "credit")
    assertEquals(ReadingHead.of(SourceWithFindings(paperless, Nil, illustration = Some(cover))).what, "Book · 2014")

  test("the map shows the box around a source's located findings; none when none is located"):
    def f(lat: Option[Double], lon: Option[Double]) = FindingRow("c", None, lon, lat, None, None, None, Nil, None)
    assertEquals(
      ReadingPane.bounds(List(f(Some(35.6), Some(35.8)), f(Some(40.0), Some(34.6)), f(None, None))),
      Some(GeoBounds(35.6, 34.6, 40.0, 35.8))
    )
    assertEquals(ReadingPane.bounds(List(f(None, None))), None)
