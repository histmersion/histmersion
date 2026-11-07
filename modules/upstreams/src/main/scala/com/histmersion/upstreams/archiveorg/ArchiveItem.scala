package com.histmersion.upstreams.archiveorg
final case class ArchiveItem(
    identifier: ArchiveItem.Identifier,
    title: Option[ArchiveItem.Title] = None,
    creator: List[ArchiveItem.Creator] = Nil,
    year: Option[ArchiveItem.Year] = None,
    publisher: Option[ArchiveItem.Publisher] = None,
    description: Option[ArchiveItem.Description] = None,
    lendingOnly: ArchiveItem.LendingOnly = _root_.com.histmersion.upstreams.archiveorg.ArchiveItem.LendingOnly(false),
    files: List[ArchiveFile] = Nil
)

object ArchiveItem:
  type IdentifierText = IdentifierText.T
  object IdentifierText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Identifier = com.histmersion.upstreams.ArchiveId
  val Identifier: _root_.com.histmersion.upstreams.ArchiveId.type = _root_.com.histmersion.upstreams.ArchiveId
  type Title = com.histmersion.plugin.WorkKey.Title
  val Title: _root_.com.histmersion.plugin.WorkKey.Title.type = _root_.com.histmersion.plugin.WorkKey.Title
  type Creator = Creator.T
  object Creator extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = com.histmersion.plugin.Year
  val Year: _root_.com.histmersion.plugin.Year.type = _root_.com.histmersion.plugin.Year
  type Publisher = Publisher.T
  object Publisher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LendingOnly = LendingOnly.T
  object LendingOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
