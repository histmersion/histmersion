package com.histmersion.plugins.calibre

/** The audit compares loosely where Calibre legitimately differs (subtitles, accents, "Last, First") and strictly where
  * it matters (no author in common, scraped junk, a missing entry).
  */
class CalibreAuditSuite extends munit.FunSuite:

  private def book(id: Int, title: String, authors: List[String], pubdate: Option[String] = None) =
    Calibre.Book(id, title, authors, pubdate, None, Nil, None, Map.empty, Nil)
  private def rec(ck: String, id: Int, title: String, authors: List[String], year: Option[Int] = None) =
    AuditRecord(ck, id, title, authors, year)

  test("subtitles, punctuation, accents and name order are not disagreements"):
    val r = rec(
      "guterbock1997_perspectives",
      1567,
      "Perspectives on Hittite Civilization: Selected Writings",
      List("Güterbock, Hans G."),
      Some(1997)
    )
    val b =
      book(1567, "Perspectives on Hittite Civilization", List("Hans G. Guterbock"), Some("1997-01-01T00:00:00+00:00"))
    assertEquals(Calibre.Audit.issues(r, b), Nil)

  test("a bracketed role or life span is not a surname"):
    assert(
      Calibre.Audit.authorsMatch(
        List("Ken Dowden (ed.)", "Niall Livingstone (ed.)"),
        List("Ken Dowden; Niall Livingstone")
      ) ||
        Calibre.Audit.authorsMatch(List("Ken Dowden (ed.)"), List("Ken Dowden"))
    )
    assertEquals(Calibre.Audit.surname("Gimbutienė, Marija (1921-1994)"), "gimbutiene")
    assertEquals(Calibre.Audit.surname("Cynthia W. Shelmerdine (ed.)"), "shelmerdine")

  test("a shared entry is judged against the record it matches — the volume, not the chapter"):
    val vol =
      rec("guterbock1997_perspectives", 1567, "Perspectives on Hittite Civilization", List("Güterbock, Hans G."))
    val ch =
      rec("guterbock1948_kumarbi", 1567, "The Hittite Version of the Hurrian Kumarbi Myths", List("Güterbock, Hans G."))
    val b = book(1567, "Perspectives on Hittite Civilization", List("Hans G. Güterbock"))
    assertEquals(Calibre.Audit.audit(List(ch, vol), Map(1567 -> b)), Nil)

  test("scraped junk and an 'Unknown' author are reported as fixable junk"):
    val junk = book(1541, "The Hittite Song", List("Erik van DongenUnknown"), Some("0101-01-01"))
    val f = Calibre.Audit.audit(
      List(rec("vandongen2012_song", 1541, "The Hittite Song", List("van Dongen, Erik"))),
      Map(1541 -> junk)
    )
    assertEquals(f.map(_.issues), List(List(AuditIssue.Junk)))
    assert(f.head.fixable)
    assertEquals(Calibre.Audit.year(junk), None, "Calibre's 0101 placeholder is not a year")

  test("a different work, a missing entry, and a year-only difference are told apart"):
    val recs = List(
      rec("a2000_x", 1, "Greek Religion", List("Burkert, Walter"), Some(1985)),
      rec("b2000_y", 2, "Anything", List("Someone")),
      rec("c2000_z", 3, "Homer and History", List("Page, Denys"), Some(1959))
    )
    val books = Map(
      1 -> book(1, "The Orientalizing Revolution", List("Martin West")),
      3 -> book(3, "History and the Homeric Iliad", List("Denys Page"), Some("1963-01-01"))
    )
    val f = Calibre.Audit.audit(recs, books)
    assertEquals(
      f.map(x => x.calibreId -> x.issues),
      List(
        1 -> List(AuditIssue.Title, AuditIssue.Authors),
        2 -> List(AuditIssue.Missing),
        3 -> List(AuditIssue.Title, AuditIssue.Year)
      )
    )
    assertEquals(f.map(_.fixable), List(true, false, true))
