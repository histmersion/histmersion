package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given

import scala.annotation.tailrec
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
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
  object Key extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Version = Version.T
  object Version extends Newtype[String, Pure]
  type File = File.T
  object File extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Licence = Licence.T
  object Licence extends Newtype[String, Pure]
  type Citation = Citation.T
  object Citation extends Newtype[String, Pure]
  type Places = Places.T
  object Places extends Newtype[Int, Pure]
  type Names = Names.T
  object Names extends Newtype[Int, Pure]
  type Links = Links.T
  object Links extends Newtype[Int, Pure]
  type ImportedAt = ImportedAt.T
  object ImportedAt extends Newtype[String, Pure]
