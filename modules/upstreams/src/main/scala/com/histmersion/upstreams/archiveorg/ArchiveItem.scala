package com.histmersion
package upstreams.archiveorg

import com.histmersion.upstreams.ArchiveId
import com.histmersion.plugin.WorkKey
import io.github.iltotore.iron.constraint.any.Pure
final case class ArchiveItem(
    identifier: ArchiveItem.Identifier,
    title: Option[ArchiveItem.Title] = None,
    creator: List[ArchiveItem.Creator] = Nil,
    year: Option[ArchiveItem.Year] = None,
    publisher: Option[ArchiveItem.Publisher] = None,
    description: Option[ArchiveItem.Description] = None,
    lendingOnly: ArchiveItem.LendingOnly = ArchiveItem.LendingOnly(false),
    files: List[ArchiveFile] = Nil
)

object ArchiveItem:
  type Identifier = ArchiveId
  val Identifier: ArchiveId.type = ArchiveId
  export WorkKey.Title
  type Creator = Creator.T
  object Creator extends Newtype[String, Pure]
  type Year = plugin.Year
  val Year: plugin.Year.type = plugin.Year
  type Publisher = Publisher.T
  object Publisher extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type LendingOnly = LendingOnly.T
  object LendingOnly extends Newtype[Boolean, Pure]
