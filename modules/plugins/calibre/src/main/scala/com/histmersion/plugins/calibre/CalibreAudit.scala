package com.histmersion.plugins.calibre

import java.text.Normalizer

/** The PURE half of `histmersion calibre audit`: compare every linked entry with its record. */
object CalibreAudit:

  /** Lower-case ASCII words: accents, punctuation and spacing are not disagreements. */
  def norm(s: String): String =
    Normalizer
      .normalize(s, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toLowerCase(java.util.Locale.ROOT)
      .replaceAll("[^a-z0-9]+", " ")
      .trim

  /** Equal after [[norm]], or one extends the other: Calibre often keeps or drops a subtitle. */
  def titleMatches(a: String, b: String): Boolean =
    val (x, y) = (norm(a), norm(b))
    x.nonEmpty && y.nonEmpty && (x.startsWith(y) || y.startsWith(x))

  /** "Güterbock, Hans G." and "Hans G. Güterbock" are the same surname; a bracketed role or life span ("Ken Dowden
    * (ed.)", "Gimbutienė, Marija (1921-1994)") is not part of the name.
    */
  def surname(author: String): String =
    val name = author.replaceAll("\\([^)]*\\)", " ").trim
    norm(if name.contains(",") then name.takeWhile(_ != ',') else name.split("\\s+").last)

  def authorsMatch(a: List[String], b: List[String]): Boolean =
    (a.map(surname).toSet & b.map(surname).toSet).exists(_.nonEmpty)

  /** Calibre's placeholder year for "unknown" is 101; anything before 1000 is not a real pubdate. */
  def year(b: CalibreBook): Option[Int] =
    b.pubdate.flatMap(_.take(4).toIntOption).filter(_ >= 1000)

  /** The metadata a scrape or a bad import leaves: blank/Unknown, or a title that is a web page. */
  def junk(b: CalibreBook): Boolean =
    Calibre.isBlank(b.title) || b.title.length > 300 || b.authors.isEmpty ||
      b.authors.exists(a => Calibre.isBlank(a) || a.toLowerCase(java.util.Locale.ROOT).contains("unknown"))

  def issues(r: AuditRecord, b: CalibreBook): List[AuditIssue] =
    List(
      Option.when(junk(b))(AuditIssue.Junk),
      Option.when(!junk(b) && !titleMatches(r.title, b.title))(AuditIssue.Title),
      Option.when(!junk(b) && !authorsMatch(r.authors, b.authors))(AuditIssue.Authors),
      Option.when(r.year.isDefined && year(b).isDefined && r.year != year(b))(AuditIssue.Year)
    ).flatten

  def audit(records: List[AuditRecord], books: Map[Int, CalibreBook]): List[AuditFinding] =
    records.groupBy(_.calibreId).toList.sortBy(_._1).flatMap { (id, rs) =>
      val keys = rs.map(_.citekey).sorted
      books.get(id) match
        case None    => List(AuditFinding(id, keys, List(AuditIssue.Missing), None, rs.minBy(_.citekey)))
        case Some(b) =>
          val (best, found) = rs.map(r => r -> issues(r, b)).minBy((r, is) => (is.size, r.citekey))
          if found.isEmpty then Nil else List(AuditFinding(id, keys, found, Some(b), best))
    }
