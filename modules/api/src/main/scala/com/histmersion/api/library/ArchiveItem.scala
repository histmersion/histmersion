package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

/** @param lendingOnly
  *   the item must be borrowed on archive.org; it is never downloaded (no DRM circumvention)
  */
final case class ArchiveItem(
    identifier: ArchiveItem.Identifier,
    title: Option[ArchiveItem.Title] = None,
    creator: List[ArchiveItem.Creator] = Nil,
    year: Option[Year] = None,
    publisher: Option[ArchiveItem.Publisher] = None,
    description: Option[ArchiveItem.Description] = None,
    lendingOnly: ArchiveItem.LendingOnly = ArchiveItem.LendingOnly(false),
    files: List[ArchiveFile] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object ArchiveItem:
  export com.histmersion.api.library.ArchiveId as Identifier
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Creator = Creator.T
  object Creator extends Newtype[String, Pure]
  type Publisher = Publisher.T
  object Publisher extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type LendingOnly = LendingOnly.T
  object LendingOnly extends Newtype[Boolean, Pure]
