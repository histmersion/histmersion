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

  /** Pure: an ISBN as the API takes it — digits (and a final X), hyphens and spaces dropped; `None` unless it is 10 or
    * 13 long.
    */
  type IsbnResult = IsbnResult.T
  object IsbnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsbnRaw = IsbnRaw.T
  object IsbnRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OclcResult = OclcResult.T
  object OclcResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OclcRaw = OclcRaw.T
  object OclcRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyResultPart1 = KeyResultPart1.T
  object KeyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyResultPart2 = KeyResultPart2.T
  object KeyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyIsbnRaw = KeyIsbnRaw.T
  object KeyIsbnRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyOclcRaw = KeyOclcRaw.T
  object KeyOclcRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverUrlResult = CoverUrlResult.T
  object CoverUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverUrlBase = CoverUrlBase.T
  object CoverUrlBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverUrlKind = CoverUrlKind.T
  object CoverUrlKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverUrlId = CoverUrlId.T
  object CoverUrlId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverUrlSize = CoverUrlSize.T
  object CoverUrlSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUrlResult = PageUrlResult.T
  object PageUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUrlBase = PageUrlBase.T
  object PageUrlBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUrlKind = PageUrlKind.T
  object PageUrlKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUrlId = PageUrlId.T
  object PageUrlId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def isbn(raw: com.histmersion.domain.media.covers.OpenLibrary.IsbnRaw): Option[com.histmersion.domain.media.covers.OpenLibrary.IsbnResult] =
    val s = raw.trim.toUpperCase(Locale.ROOT).filter(c => c.isDigit || c == 'X')
    Option(s).filter(x => (x.length == 10 && x.init.forall(_.isDigit)) || (x.length == 13 && x.forall(_.isDigit)))

  /** Pure: an OCLC number — its digits (`ocm00012345`, `(OCoLC)12345` → `12345`). */
  def oclc(raw: com.histmersion.domain.media.covers.OpenLibrary.OclcRaw): Option[com.histmersion.domain.media.covers.OpenLibrary.OclcResult] =
    Option(raw.trim.dropWhile(!_.isDigit).takeWhile(_.isDigit).dropWhile(_ == '0')).filter(_.nonEmpty)

  /** Pure: what to ask for — by ISBN first (the API's own preference), else by OCLC number. */
  def key(isbnRaw: Option[com.histmersion.domain.media.covers.OpenLibrary.KeyIsbnRaw], oclcRaw: Option[com.histmersion.domain.media.covers.OpenLibrary.KeyOclcRaw]): Option[(com.histmersion.domain.media.covers.OpenLibrary.KeyResultPart1, com.histmersion.domain.media.covers.OpenLibrary.KeyResultPart2)] =
    isbnRaw.flatMap(isbn).map("isbn" -> _).orElse(oclcRaw.flatMap(oclc).map("oclc" -> _))

  /** Pure: a cover's URL; a missing cover answers 404. */
  def coverUrl(base: com.histmersion.domain.media.covers.OpenLibrary.CoverUrlBase, kind: com.histmersion.domain.media.covers.OpenLibrary.CoverUrlKind, id: com.histmersion.domain.media.covers.OpenLibrary.CoverUrlId, size: com.histmersion.domain.media.covers.OpenLibrary.CoverUrlSize): com.histmersion.domain.media.covers.OpenLibrary.CoverUrlResult =
    s"${base.stripSuffix("/")}/b/$kind/${URLEncoder.encode(id, UTF_8)}-${size.trim.toUpperCase(Locale.ROOT).take(1)}.jpg?default=false"

  /** Pure: the book's page, for the credit line's link. */
  def pageUrl(base: com.histmersion.domain.media.covers.OpenLibrary.PageUrlBase, kind: com.histmersion.domain.media.covers.OpenLibrary.PageUrlKind, id: com.histmersion.domain.media.covers.OpenLibrary.PageUrlId): com.histmersion.domain.media.covers.OpenLibrary.PageUrlResult =
    if kind == "isbn" then s"${base.stripSuffix("/")}/isbn/$id"
    else s"${base.stripSuffix("/")}/search?q=oclc%3A${URLEncoder.encode(id, UTF_8)}"
