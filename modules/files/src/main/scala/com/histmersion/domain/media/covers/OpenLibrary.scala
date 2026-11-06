package com.histmersion.domain.media.covers

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** The Open Library Covers API (#1446): a book's cover by ISBN or OCLC number, at
  * `<base>/b/<isbn|oclc>/<id>-<S|M|L>.jpg`; `?default=false` makes a missing cover a 404 instead of a blank picture.
  * Documented at https://openlibrary.org/dev/docs/api/covers — 100 lookups by ISBN or OCLC per IP per 5 minutes, which
  * `media.covers.request-interval-ms` keeps under. Pure.
  */
object OpenLibrary:
  type BaseUrl = BaseUrl.T
  object BaseUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverSize = CoverSize.T
  object CoverSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverUrl = CoverUrl.T
  object CoverUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Oclc = Oclc.T
  object Oclc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUrl = PageUrl.T
  object PageUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: an ISBN as the API takes it — digits (and a final X), hyphens and spaces dropped; `None` unless it is 10 or
    * 13 long.
    */

  def isbn(raw: com.histmersion.domain.library.Isbn): Option[com.histmersion.domain.library.Isbn] =
    val s = raw.trim.toUpperCase(Locale.ROOT).filter(c => c.isDigit || c == 'X')
    Option(s).filter(x => (x.length == 10 && x.init.forall(_.isDigit)) || (x.length == 13 && x.forall(_.isDigit)))

  /** Pure: an OCLC number — its digits (`ocm00012345`, `(OCoLC)12345` → `12345`). */
  def oclc(raw: com.histmersion.domain.media.covers.OpenLibrary.Oclc): Option[com.histmersion.domain.media.covers.OpenLibrary.Oclc] =
    Option(raw.trim.dropWhile(!_.isDigit).takeWhile(_.isDigit).dropWhile(_ == '0')).filter(_.nonEmpty)

  /** Pure: what to ask for — by ISBN first (the API's own preference), else by OCLC number. */
  def key(isbnRaw: Option[com.histmersion.domain.library.Isbn], oclcRaw: Option[com.histmersion.domain.media.covers.OpenLibrary.Oclc]): Option[(com.histmersion.domain.media.covers.OpenLibrary.Key, com.histmersion.domain.media.covers.OpenLibrary.Key)] =
    isbnRaw.flatMap(isbn).map("isbn" -> _).orElse(oclcRaw.flatMap(oclc).map("oclc" -> _))

  /** Pure: a cover's URL; a missing cover answers 404. */
  def coverUrl(base: com.histmersion.domain.media.covers.OpenLibrary.BaseUrl, kind: com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Kind, id: com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Id, size: com.histmersion.domain.media.covers.OpenLibrary.CoverSize): com.histmersion.domain.media.covers.OpenLibrary.CoverUrl =
    s"${base.stripSuffix("/")}/b/$kind/${URLEncoder.encode(id, UTF_8)}-${size.trim.toUpperCase(Locale.ROOT).take(1)}.jpg?default=false"

  /** Pure: the book's page, for the credit line's link. */
  def pageUrl(base: com.histmersion.domain.media.covers.OpenLibrary.BaseUrl, kind: com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Kind, id: com.histmersion.domain.media.covers.CoverChoice.Pick.OpenLibrary.Id): com.histmersion.domain.media.covers.OpenLibrary.PageUrl =
    if kind == "isbn" then s"${base.stripSuffix("/")}/isbn/$id"
    else s"${base.stripSuffix("/")}/search?q=oclc%3A${URLEncoder.encode(id, UTF_8)}"
