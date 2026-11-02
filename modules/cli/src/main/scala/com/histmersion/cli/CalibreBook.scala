package com.histmersion.cli

import com.histmersion.api.library.Acquire

import scala.jdk.CollectionConverters.*

/** One book as Calibre holds it — the shape both readers below produce (`calibredb list --for-machine` and the
  * `sqlite3 -readonly` fallback normalise to this same record).
  */
final case class CalibreBook(
    id: Int,
    title: String,
    authors: List[String],
    pubdate: Option[String],
    publisher: Option[String],
    tags: List[String],
    comments: Option[String],
    identifiers: Map[String, String],
    formats: List[String],
    /** The cover Calibre keeps beside the book (`<book dir>/cover.jpg`), absolute — `None` when the entry has none.
      * Read-only: `media covers` uploads a copy, the file stays as it is.
      */
    cover: Option[String] = None
):
  /** Calibre writes `0101-01-01` for "no date"; a year below [[Acquire.MinCitekeyYear]] is that placeholder, not a
    * publication year (#714).
    */
  def year: Option[Int] = pubdate
    .map(_.take(4))
    .filter(_.forall(_.isDigit))
    .flatMap(_.toIntOption)
    .filter(_ >= Acquire.MinCitekeyYear)
  def doi: Option[String] = identifiers.get("doi")
  def isbn: Option[String] = identifiers.get("isbn")

  /** The file to import: the requested format, else PDF, else EPUB (both carry a text layer). */
  def pick(format: Option[String]): Option[String] =
    val wanted = format.map(_.toLowerCase).toList ++ List("pdf", "epub")
    wanted.iterator.map(ext => formats.find(_.toLowerCase.endsWith("." + ext))).collectFirst { case Some(f) =>
      f
    }
