package com.histmersion.cli

import com.histmersion.api.library.AuthorName

/** The audit compares loosely where Calibre legitimately differs (subtitles, accents, "Last, First") and strictly where
  * it matters (no author in common, scraped junk, a missing entry).
  */
class CalibreAuditSuite extends munit.FunSuite:

  private def book(
      id: Calibre.Book.Id,
      title: Calibre.Book.Title,
      authors: List[Calibre.Book.Authors],
      pubdate: Option[Calibre.Book.Pubdate] = None
  ) =
    Calibre.Book(id, title, authors, pubdate, None, Nil, None, Map.empty, Nil)
  private def rec(
      ck: AuditRecord.Citekey,
      id: AuditRecord.CalibreId,
      title: AuditRecord.Title,
      authors: List[AuditRecord.Authors],
      year: Option[AuditRecord.Year] = None
  ) =
    AuditRecord(ck, id, title, authors, year)

  test("subtitles, punctuation, accents and name order are not disagreements"):
    val r = rec(
      AuditRecord.Citekey("guterbock1997_perspectives"),
      AuditRecord.CalibreId(1567),
      AuditRecord.Title("Perspectives on Hittite Civilization: Selected Writings"),
      List(AuditRecord.Authors("Güterbock, Hans G.")),
      Some(AuditRecord.Year(1997))
    )
    val b =
      book(
        Calibre.Book.Id(1567),
        Calibre.Book.Title("Perspectives on Hittite Civilization"),
        List(Calibre.Book.Authors("Hans G. Guterbock")),
        Some(Calibre.Book.Pubdate("1997-01-01T00:00:00+00:00"))
      )
    assertEquals(Calibre.Audit.issues(r, b), Nil)

  test("a bracketed role or life span is not a surname"):
    assert(
      Calibre.Audit.authorsMatch(
        List(AuthorName("Ken Dowden (ed.)"), AuthorName("Niall Livingstone (ed.)")),
        List(AuthorName("Ken Dowden; Niall Livingstone"))
      ) ||
        Calibre.Audit.authorsMatch(List(AuthorName("Ken Dowden (ed.)")), List(AuthorName("Ken Dowden")))
    )
    assertEquals(Calibre.Audit.surname(AuthorName("Gimbutienė, Marija (1921-1994)")).value, "gimbutiene")
    assertEquals(Calibre.Audit.surname(AuthorName("Cynthia W. Shelmerdine (ed.)")).value, "shelmerdine")

  test("a shared entry is judged against the record it matches — the volume, not the chapter"):
    val vol =
      rec(
        AuditRecord.Citekey("guterbock1997_perspectives"),
        AuditRecord.CalibreId(1567),
        AuditRecord.Title("Perspectives on Hittite Civilization"),
        List(AuditRecord.Authors("Güterbock, Hans G."))
      )
    val ch =
      rec(
        AuditRecord.Citekey("guterbock1948_kumarbi"),
        AuditRecord.CalibreId(1567),
        AuditRecord.Title("The Hittite Version of the Hurrian Kumarbi Myths"),
        List(AuditRecord.Authors("Güterbock, Hans G."))
      )
    val b = book(
      Calibre.Book.Id(1567),
      Calibre.Book.Title("Perspectives on Hittite Civilization"),
      List(Calibre.Book.Authors("Hans G. Güterbock"))
    )
    assertEquals(Calibre.Audit.audit(List(ch, vol), Map(Calibre.Book.Id(1567) -> b)), Nil)

  test("scraped junk and an 'Unknown' author are reported as fixable junk"):
    val junk = book(
      Calibre.Book.Id(1541),
      Calibre.Book.Title("The Hittite Song"),
      List(Calibre.Book.Authors("Erik van DongenUnknown")),
      Some(Calibre.Book.Pubdate("0101-01-01"))
    )
    val f = Calibre.Audit.audit(
      List(
        rec(
          AuditRecord.Citekey("vandongen2012_song"),
          AuditRecord.CalibreId(1541),
          AuditRecord.Title("The Hittite Song"),
          List(AuditRecord.Authors("van Dongen, Erik"))
        )
      ),
      Map(Calibre.Book.Id(1541) -> junk)
    )
    assertEquals(f.map(_.issues), List(List(AuditIssue.Junk)))
    assert(f.head.fixable)
    assertEquals(Calibre.Audit.year(junk), None, "Calibre's 0101 placeholder is not a year")

  test("a different work, a missing entry, and a year-only difference are told apart"):
    val recs = List(
      rec(
        AuditRecord.Citekey("a2000_x"),
        AuditRecord.CalibreId(1),
        AuditRecord.Title("Greek Religion"),
        List(AuditRecord.Authors("Burkert, Walter")),
        Some(AuditRecord.Year(1985))
      ),
      rec(
        AuditRecord.Citekey("b2000_y"),
        AuditRecord.CalibreId(2),
        AuditRecord.Title("Anything"),
        List(AuditRecord.Authors("Someone"))
      ),
      rec(
        AuditRecord.Citekey("c2000_z"),
        AuditRecord.CalibreId(3),
        AuditRecord.Title("Homer and History"),
        List(AuditRecord.Authors("Page, Denys")),
        Some(AuditRecord.Year(1959))
      )
    )
    val books = Map(
      Calibre.Book.Id(1) -> book(
        Calibre.Book.Id(1),
        Calibre.Book.Title("The Orientalizing Revolution"),
        List(Calibre.Book.Authors("Martin West"))
      ),
      Calibre.Book.Id(3) -> book(
        Calibre.Book.Id(3),
        Calibre.Book.Title("History and the Homeric Iliad"),
        List(Calibre.Book.Authors("Denys Page")),
        Some(Calibre.Book.Pubdate("1963-01-01"))
      )
    )
    val f = Calibre.Audit.audit(recs, books)
    assertEquals(
      f.map(x => x.calibreId -> x.issues),
      List(
        AuditFinding.CalibreId(1) -> List(AuditIssue.Title, AuditIssue.Authors),
        AuditFinding.CalibreId(2) -> List(AuditIssue.Missing),
        AuditFinding.CalibreId(3) -> List(AuditIssue.Title, AuditIssue.Year)
      )
    )
    val md = Calibre.Audit.markdown(f, com.histmersion.api.library.CollectionAudit.Checked(3))
    assert(md.contains("`histmersion calibre update a2000_x`"), md)
    assert(md.contains("re-push, or clear"), md)
