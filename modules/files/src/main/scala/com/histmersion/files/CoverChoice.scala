package com.histmersion.files

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** The Open Library Covers API (#1446): a book's cover by ISBN or OCLC number, at
  * `<base>/b/<isbn|oclc>/<id>-<S|M|L>.jpg`; `?default=false` makes a missing cover a 404 instead of a blank picture.
  * Documented at https://openlibrary.org/dev/docs/api/covers — 100 lookups by ISBN or OCLC per IP per 5 minutes, which
  * `media.covers.request-interval-ms` keeps under. Pure.
  */
object OpenLibrary:

  /** Pure: an ISBN as the API takes it — digits (and a final X), hyphens and spaces dropped; `None` unless it is 10 or
    * 13 long.
    */
  def isbn(raw: String): Option[String] =
    val s = raw.trim.toUpperCase(Locale.ROOT).filter(c => c.isDigit || c == 'X')
    Option(s).filter(x => (x.length == 10 && x.init.forall(_.isDigit)) || (x.length == 13 && x.forall(_.isDigit)))

  /** Pure: an OCLC number — its digits (`ocm00012345`, `(OCoLC)12345` → `12345`). */
  def oclc(raw: String): Option[String] =
    Option(raw.trim.dropWhile(!_.isDigit).takeWhile(_.isDigit).dropWhile(_ == '0')).filter(_.nonEmpty)

  /** Pure: what to ask for — by ISBN first (the API's own preference), else by OCLC number. */
  def key(isbnRaw: Option[String], oclcRaw: Option[String]): Option[(String, String)] =
    isbnRaw.flatMap(isbn).map("isbn" -> _).orElse(oclcRaw.flatMap(oclc).map("oclc" -> _))

  /** Pure: a cover's URL; a missing cover answers 404. */
  def coverUrl(base: String, kind: String, id: String, size: String): String =
    s"${base.stripSuffix("/")}/b/$kind/${URLEncoder.encode(id, UTF_8)}-${size.trim.toUpperCase(Locale.ROOT).take(1)}.jpg?default=false"

  /** Pure: the book's page, for the credit line's link. */
  def pageUrl(base: String, kind: String, id: String): String =
    if kind == "isbn" then s"${base.stripSuffix("/")}/isbn/$id"
    else s"${base.stripSuffix("/")}/search?q=oclc%3A${URLEncoder.encode(id, UTF_8)}"

/** Where a book's cover comes from (#1446), in order: the cover the local Calibre library keeps for the entry (the
  * user's own copy's cover — it is the edition they hold), else Open Library's by the first usable ISBN (the record's,
  * then Calibre's), else by OCLC number; else none. Pure: whether the Calibre file exists is a parameter.
  */
object CoverChoice:
  enum Pick:
    case Calibre(path: String)
    case OpenLibrary(kind: String, id: String)

  def choose(
      calibreCover: Option[String],
      exists: String => Boolean,
      isbns: List[String],
      oclc: Option[String]
  ): Option[Pick] =
    calibreCover
      .map(_.trim)
      .filter(p => p.nonEmpty && exists(p))
      .map(Pick.Calibre(_))
      .orElse(isbns.flatMap(OpenLibrary.isbn).headOption.map(Pick.OpenLibrary("isbn", _)))
      .orElse(oclc.flatMap(OpenLibrary.oclc).map(Pick.OpenLibrary("oclc", _)))
