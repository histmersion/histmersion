package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given

import scala.annotation.tailrec
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given

/** One row of `gazetteer_sources` (V071). */
final case class GazetteerSourceRow(
    key: GazetteerSourceRow.Key,
    kind: GazetteerSourceRow.Kind,
    title: GazetteerSourceRow.Title,
    version: GazetteerSourceRow.Version,
    sha256: com.histmersion.Sha256,
    file: GazetteerSourceRow.File,
    url: Option[GazetteerSourceRow.Url],
    licence: Option[GazetteerSourceRow.Licence],
    citation: Option[GazetteerSourceRow.Citation],
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    places: GazetteerSourceRow.Places,
    names: GazetteerSourceRow.Names,
    links: GazetteerSourceRow.Links,
    importedAt: GazetteerSourceRow.ImportedAt
) derives DbCodec

object GazetteerSourceRow:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Version = Version.T
  object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type File = File.T
  object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licence = Licence.T
  object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citation = Citation.T
  object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Places = Places.T
  object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Links = Links.T
  object Links extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportedAt = ImportedAt.T
  object ImportedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
