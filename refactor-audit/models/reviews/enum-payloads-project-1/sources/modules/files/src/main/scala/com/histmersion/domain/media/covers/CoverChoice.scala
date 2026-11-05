package com.histmersion.domain.media.covers

/** Where a book's cover comes from (#1446), in order: the cover the local Calibre library keeps for the entry (the
  * user's own copy's cover — it is the edition they hold), else Open Library's by the first usable ISBN (the record's,
  * then Calibre's), else by OCLC number; else none. Pure: whether the Calibre file exists is a parameter.
  */
object CoverChoice:
  type ChooseCalibreCover = ChooseCalibreCover.T
  object ChooseCalibreCover extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooseExistsArg1 = ChooseExistsArg1.T
  object ChooseExistsArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooseExistsResult = ChooseExistsResult.T
  object ChooseExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooseIsbns = ChooseIsbns.T
  object ChooseIsbns extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooseOclc = ChooseOclc.T
  object ChooseOclc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Pick:
    case Calibre(path: Calibre.Path)
    case OpenLibrary(kind: OpenLibrary.Kind, id: OpenLibrary.Id)

  object Pick:
    object Calibre:
      type Path = Path.T
      object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object OpenLibrary:
      type Kind = Kind.T
      object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def choose(
      calibreCover: Option[com.histmersion.domain.media.covers.CoverChoice.ChooseCalibreCover],
      exists: com.histmersion.domain.media.covers.CoverChoice.ChooseExistsArg1 => com.histmersion.domain.media.covers.CoverChoice.ChooseExistsResult,
      isbns: List[com.histmersion.domain.media.covers.CoverChoice.ChooseIsbns],
      oclc: Option[com.histmersion.domain.media.covers.CoverChoice.ChooseOclc]
  ): Option[Pick] =
    calibreCover
      .map(_.trim)
      .filter(p => p.nonEmpty && exists(p))
      .map(Pick.Calibre(_))
      .orElse(isbns.flatMap(OpenLibrary.isbn).headOption.map(Pick.OpenLibrary("isbn", _)))
      .orElse(oclc.flatMap(OpenLibrary.oclc).map(Pick.OpenLibrary("oclc", _)))
