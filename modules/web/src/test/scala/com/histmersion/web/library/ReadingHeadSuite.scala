package com.histmersion
package web.library

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.SearchHit
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.media.MediaRole
import com.histmersion.web.map.GeoBounds
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import com.histmersion.api.media.MediaItem

class ReadingHeadSuite extends munit.FunSuite:

  private val detail = Source.Detail(
    Citekey("cline2014_year"),
    Title("1177 B.C."),
    (List("Eric H. Cline")).map(AuthorName.apply),
    (Some(2014)).map(Year.apply),
    (Some("Princeton University Press")).map(Source.Venue.apply),
    Source.Status.Current,
    Nil,
    List(Source.Regions("levant"), Source.Regions("anatolia")),
    Nil,
    Some(Year(-1250)),
    Some(Year(-1100)),
    Some(Source.Summary(" Several pressures together. ")),
    None,
    None,
    provenance = Some(Source.Provenance("calibre"))
  )

  test("from the table's row at once: year, title, authors, status, period, regions, summary"):
    val h = ReadingHead.of(
      SearchHit(
        Citekey("cline2014_year"),
        Title("1177 B.C."),
        (Some(2014)).map(Year.apply),
        Source.Status.Current,
        Nil,
        List(Source.Regions("levant")),
        Some(Year(-1250)),
        Some(Year(-1100)),
        Some(Source.Summary("Sum.")),
        authors = (List("Eric H. Cline")).map(AuthorName.apply)
      )
    )
    assertEquals(
      h,
      ReadingHead(
        "2014",
        "1177 B.C.",
        "Eric H. Cline",
        Some(Source.Status.Current),
        "1250 BCE–1100 BCE",
        "levant",
        Some("Sum.")
      )
    )

  test("once read: type · venue · year, the regions in words, the summary trimmed"):
    val h = ReadingHead.of(Source.WithFindings(detail, Nil))
    assertEquals(h.what, "Book · Princeton University Press · 2014")
    assertEquals(h.regions, "levant · anatolia")
    assertEquals(h.summary, Some("Several pressures together."))

  test("an unknown type is left out, not called unknown"):
    val paperless = detail.modify(_.provenance).setTo(None).modify(_.venue).setTo(None)
    assertEquals(ReadingHead.of(Source.WithFindings(paperless, Nil)).what, "2014")
    val cover = MediaThumb(MediaItem.Uid("u"), MediaThumb.Role(MediaRole.Cover), MediaThumb.Url("/x.jpg"), MediaItem.Credit("credit"))
    assertEquals(
      ReadingHead.of(Source.WithFindings(paperless, Nil, illustration = Some(cover))).what,
      "Book · 2014"
    )

  test("the map shows the box around a source's located findings; none when none is located"):
    def f(lat: Option[Latitude], lon: Option[Longitude]) = Finding.Row(Finding.Claim("c"), None, lon, lat, None, None, None, Nil, None)
    assertEquals(
      ReadingPane.bounds(List(f(Some(Latitude(35.6)), Some(Longitude(35.8))), f(Some(Latitude(40.0)), Some(Longitude(34.6))), f(None, None))),
      Some(GeoBounds(GeoBounds.South(35.6), GeoBounds.West(34.6), GeoBounds.North(40.0), GeoBounds.East(35.8)))
    )
    assertEquals(ReadingPane.bounds(List(f(None, None))), None)
