package com.histmersion.api

/** What a SOURCE is — a book, a chapter of one, a paper, a thesis, a dataset — as far as its record
  * says, never a guess: the map draws a finding's marker with its source's kind's icon, and the source's
  * card leads with the same one (web `CardIcons`). A source none of the rules recognise is `unknown`, and
  * is drawn so (an exclamation mark), not as a generic document. Pure. */
object SourceKinds:
  val Book    = "book"
  val Chapter = "chapter"
  val Paper   = "paper"
  val Thesis  = "thesis"
  val Dataset = "dataset"
  val Unknown = "unknown"
  val all: List[String] = List(Book, Chapter, Paper, Thesis, Dataset, Unknown)

  /** DOI prefixes of data repositories (Dryad, figshare, Mendeley Data, Harvard Dataverse). Zenodo is
    * absent: it holds papers as often as data. */
  private val DataDoiPrefixes = List("10.5061/", "10.6084/", "10.17632/", "10.7910/")

  /** Acquisitions that bring in whole books (a Calibre entry, an archive.org download, a file the user
    * brought in from elsewhere — [[Provenance.ExternalFile]]). */
  private val BookProvenance = Set("calibre", Provenance.ExternalFile, "archive", "archive_org")

  /** Pure: what a source is — from its own words (a tag or topic naming a thesis or a dataset, a venue
    * naming a thesis), its DOI (a data repository's; a book's or a chapter's, which carries the ISBN),
    * any other DOI (a paper), how it came in or the picture it has (a Calibre entry, a cover: a book);
    * anything else is [[Unknown]]. */
  def of(doi: Option[String], venue: Option[String], provenance: Option[String], tags: List[String],
      topics: List[String], hasCover: Boolean): String =
    val words = (tags ++ topics).map(_.toLowerCase(java.util.Locale.ROOT))
    val where = venue.getOrElse("").toLowerCase(java.util.Locale.ROOT)
    val id    = doi.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    def says(w: String*) = words.exists(t => w.exists(t.contains)) || w.exists(where.contains)
    if says("thesis", "dissertation") then Thesis
    else if words.exists(_.contains("dataset")) || id.exists(d => DataDoiPrefixes.exists(d.startsWith)) then Dataset
    else id.flatMap(isbnDoi) match
      case Some(chapter) => if chapter then Chapter else Book
      case None if id.isDefined => Paper
      case None =>
        if hasCover || provenance.map(_.trim.toLowerCase(java.util.Locale.ROOT)).exists(BookProvenance.contains) then Book
        else Unknown

  /** Pure: a DOI that is a book's ISBN (`10.1007/978-3-030-12345-6`, `10.1017/CBO9780511607066`) —
    * `Some(true)` when something follows the 13 ISBN digits (a chapter: `…-6_7`, `…066.004`), `Some(false)`
    * for the book itself; `None` for any other DOI. */
  def isbnDoi(doi: String): Option[Boolean] =
    val suffix = doi.dropWhile(_ != '/').drop(1).toLowerCase(java.util.Locale.ROOT).stripPrefix("cbo")
    if !(suffix.startsWith("978") || suffix.startsWith("979")) then None
    else
      // the 13 ISBN digits (hyphens between them allowed): what is left names a part of the book
      val (digits, end) = suffix.iterator.zipWithIndex
        .takeWhile((c, _) => c.isDigit || c == '-')
        .foldLeft((0, 0)) { case ((n, at), (c, i)) => if n >= 13 then (n, at) else (if c.isDigit then n + 1 else n, i + 1) }
      if digits < 13 then None else Some(suffix.drop(end).exists(_.isLetterOrDigit))
