package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given

import scala.annotation.tailrec
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One row of `gazetteer_sources` (V071). */
final case class GazetteerSourceRow(
    key: GazetteerSourceRow.Key,
    kind: GazetteerSourceRow.Kind,
    title: GazetteerSourceRow.Title,
    version: GazetteerSourceRow.Version,
    sha256: Sha256,
    file: GazetteerSourceRow.File,
    url: Option[GazetteerSourceRow.Url],
    licence: Option[GazetteerSourceRow.Licence],
    citation: Option[GazetteerSourceRow.Citation],
    periodStart: Option[Year],
    periodEnd: Option[Year],
    places: GazetteerSourceRow.Places,
    names: GazetteerSourceRow.Names,
    links: GazetteerSourceRow.Links,
    importedAt: GazetteerSourceRow.ImportedAt
) derives DbCodec

object GazetteerSourceRow:
  type Key = Key.T
  object Key extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type Version = Version.T
  object Version extends RefinedType[String, Pure]
  type File = File.T
  object File extends RefinedType[String, Pure]
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
  type Licence = Licence.T
  object Licence extends RefinedType[String, Pure]
  type Citation = Citation.T
  object Citation extends RefinedType[String, Pure]
  type Places = Places.T
  object Places extends RefinedType[Int, Pure]
  type Names = Names.T
  object Names extends RefinedType[Int, Pure]
  type Links = Links.T
  object Links extends RefinedType[Int, Pure]
  type ImportedAt = ImportedAt.T
  object ImportedAt extends RefinedType[String, Pure]
