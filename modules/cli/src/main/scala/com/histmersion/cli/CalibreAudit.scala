package com.histmersion.cli

import java.text.Normalizer

/** A corpus source as the audit sees it: the Calibre entry it names and what the corpus says. */
final case class AuditRecord(citekey: String, calibreId: Int, title: String, authors: List[String], year: Option[Int])

/** What can be wrong with a linked Calibre entry, worst first. */
enum AuditIssue(val label: String):
  case Missing extends AuditIssue("no such Calibre entry")
  case Junk extends AuditIssue("Calibre's title or author is blank, Unknown or scraped")
  case Title extends AuditIssue("titles differ")
  case Authors extends AuditIssue("no author in common")
  case Year extends AuditIssue("years differ")

/** One Calibre entry that disagrees with the corpus, judged against the record it matches best (an entry shared by a
  * chapter and its volume is fine when it matches either).
  */
final case class AuditFinding(
    calibreId: Int,
    citekeys: List[String],
    issues: List[AuditIssue],
    calibre: Option[CalibreBook],
    record: AuditRecord
):
  /** What `calibre update` fixes: everything but a missing entry. The year included — Calibre shows the record's year,
    * not the printing on the shelf (the user's decision of 2026-10-03, #831).
    */
  def fixable: Boolean = !issues.contains(AuditIssue.Missing)

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

  private def cell(s: String): String = s.replace("|", "\\|").replace("\n", " ").take(90)

  def markdown(findings: List[AuditFinding], checked: Int): String =
    if findings.isEmpty then s"_All $checked linked Calibre entries agree with the corpus._"
    else
      val table =
        "| id | citekey(s) | issue | corpus | Calibre | fix |\n|---:|---|---|---|---|---|\n" +
          findings
            .map { f =>
              val corpus =
                s"${f.record.title} — ${f.record.authors.mkString("; ")}${f.record.year.fold("")(y => s" ($y)")}"
              val cal =
                f.calibre.fold("—")(b => s"${b.title} — ${b.authors.mkString("; ")}${year(b).fold("")(y => s" ($y)")}")
              val todo =
                if f.fixable then s"`histmersion calibre update ${f.record.citekey}`"
                else "re-push, or clear the source's calibre id"
              s"| ${f.calibreId} | ${f.citekeys.mkString(", ")} | ${f.issues.map(_.label).mkString("; ")} | ${cell(corpus)} | ${cell(cal)} | $todo |"
            }
            .mkString("\n")
      s"### ${findings.size} of $checked linked Calibre entries disagree with the corpus\n\n$table\n\n" +
        "_`histmersion calibre update --all` lists every change it would make; `--apply` writes them._"
