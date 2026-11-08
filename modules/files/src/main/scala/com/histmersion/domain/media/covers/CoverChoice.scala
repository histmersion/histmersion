package com.histmersion
package domain.media.covers

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Isbn

/** Where a book's cover comes from (#1446), in order: the cover the local Calibre library keeps for the entry (the
  * user's own copy's cover — it is the edition they hold), else Open Library's by the first usable ISBN (the record's,
  * then Calibre's), else by OCLC number; else none. Pure: whether the Calibre file exists is a parameter.
  */
object CoverChoice:

  enum Pick derives CanEqual:
    case Calibre(path: Pick.Calibre.Path)
    case OpenLibrary(kind: Pick.OpenLibrary.Kind, id: Pick.OpenLibrary.Id)

  object Pick:
    object Calibre:
      type Path = Path.T
      object Path extends Newtype[String, Pure]
    object OpenLibrary:
      type Kind = Kind.T
      object Kind extends Newtype[String, Pure]
      type Id = Id.T
      object Id extends Newtype[String, Pure]

  def choose(
      calibreCover: Option[Pick.Calibre.Path],
      exists: Pick.Calibre.Path => Boolean,
      isbns: List[Isbn],
      oclc: Option[OpenLibrary.Oclc]
  ): Option[Pick] =
    calibreCover
      .map(_.value.trim)
      .filter(p => p.nonEmpty && exists(Pick.Calibre.Path(p)))
      .map(p => Pick.Calibre(Pick.Calibre.Path(p)))
      .orElse(
        isbns
          .flatMap(OpenLibrary.isbn)
          .headOption
          .map(i => Pick.OpenLibrary(Pick.OpenLibrary.Kind("isbn"), Pick.OpenLibrary.Id(i.value)))
      )
      .orElse(
        oclc
          .flatMap(OpenLibrary.oclc)
          .map(o => Pick.OpenLibrary(Pick.OpenLibrary.Kind("oclc"), Pick.OpenLibrary.Id(o.value)))
      )
