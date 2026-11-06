package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** @param lendingOnly
  *   the item must be borrowed on archive.org; it is never downloaded (no DRM circumvention)
  */
final case class ArchiveItem(
    identifier: ArchiveItem.Identifier,
    title: Option[ArchiveItem.Title] = None,
    creator: List[ArchiveItem.Creator] = Nil,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    publisher: Option[ArchiveItem.Publisher] = None,
    description: Option[ArchiveItem.Description] = None,
    lendingOnly: ArchiveItem.LendingOnly = _root_.com.histmersion.api.library.ArchiveItem.LendingOnly(false),
    files: List[ArchiveFile] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ArchiveItem:
  type Identifier = Identifier.T
  object Identifier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Creator = Creator.T
  object Creator extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Publisher = Publisher.T
  object Publisher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LendingOnly = LendingOnly.T
  object LendingOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
