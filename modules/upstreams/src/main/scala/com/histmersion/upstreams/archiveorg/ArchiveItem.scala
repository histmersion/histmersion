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
  type Identifier = com.histmersion.domain.library.ArchiveId
  val Identifier: _root_.com.histmersion.domain.library.ArchiveId.type = _root_.com.histmersion.domain.library.ArchiveId
  type Title = com.histmersion.domain.Title
  val Title: _root_.com.histmersion.domain.Title.type = _root_.com.histmersion.domain.Title
  type Creator = Creator.T
  object Creator extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = com.histmersion.Year
  val Year: _root_.com.histmersion.Year.type = _root_.com.histmersion.Year
  type Publisher = Publisher.T
  object Publisher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LendingOnly = LendingOnly.T
  object LendingOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
