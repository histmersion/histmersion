package com.histmersion
package upstreams.archiveorg

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class ArchiveFile(name: ArchiveFile.Name, format: Option[ArchiveFile.Format], megabytes: ArchiveFile.Megabytes)

object ArchiveFile:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Format = Format.T
  object Format extends RefinedType[String, Pure]
  type Megabytes = Megabytes.T
  object Megabytes extends RefinedType[Double, Pure]
