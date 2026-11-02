package com.histmersion.api.library
import scala.annotation.tailrec
import com.histmersion.api.Dataset
import com.histmersion.api.given

object SourceKinds:
  val Book = "book"
  val Chapter = "chapter"
  val Paper = "paper"
  val Thesis = "thesis"
  val Dataset = "dataset"
  val Unknown = "unknown"
  val all: List[String] = List(Book, Chapter, Paper, Thesis, Dataset, Unknown)

  /** DOI prefixes of data repositories (Dryad, figshare, Mendeley Data, Harvard Dataverse). Zenodo is absent: it holds
    * papers as often as data.
    */
  private val DataDoiPrefixes = List("10.5061/", "10.6084/", "10.17632/", "10.7910/")

  private val BookProvenance = Set("calibre", Provenance.ExternalFile, "archive", "archive_org")

  def of(
      doi: Option[String],
      venue: Option[String],
      provenance: Option[String],
      tags: List[String],
      topics: List[String],
      hasCover: Boolean
  ): String =
    val words = (tags ++ topics).map(_.toLowerCase(java.util.Locale.ROOT))
    val where = venue.getOrElse("").toLowerCase(java.util.Locale.ROOT)
    val id = doi.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    def says(w: String*) = words.exists(t => w.exists(t.contains)) || w.exists(where.contains)
    if says("thesis", "dissertation") then Thesis
    else if words.exists(_.contains("dataset")) || id.exists(d => DataDoiPrefixes.exists(d.startsWith)) then Dataset
    else
      id.flatMap(isbnDoi) match
        case Some(chapter)        => if chapter then Chapter else Book
        case None if id.isDefined => Paper
        case None                 =>
          if hasCover || provenance.map(_.trim.toLowerCase(java.util.Locale.ROOT)).exists(BookProvenance.contains) then
            Book
          else Unknown

  def isbnDoi(doi: String): Option[Boolean] =
    val suffix = doi.dropWhile(_ != '/').drop(1).toLowerCase(java.util.Locale.ROOT).stripPrefix("cbo")
    if !(suffix.startsWith("978") || suffix.startsWith("979")) then None
    else
      @tailrec def isbnEnd(i: Int, digits: Int): Option[Int] =
        if digits >= 13 then Some(i)
        else if i < suffix.length && suffix(i).isDigit then isbnEnd(i + 1, digits + 1)
        else if i < suffix.length && suffix(i) == '-' then isbnEnd(i + 1, digits)
        else None
      isbnEnd(0, 0).map(end => suffix.drop(end).exists(_.isLetterOrDigit))
