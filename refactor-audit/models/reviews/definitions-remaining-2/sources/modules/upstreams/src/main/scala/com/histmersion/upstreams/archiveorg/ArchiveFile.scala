package com.histmersion.upstreams.archiveorg
final case class ArchiveFile(name: ArchiveFile.Name, format: Option[ArchiveFile.Format], megabytes: ArchiveFile.Megabytes)

object ArchiveFile:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Format = Format.T
  object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Megabytes = Megabytes.T
  object Megabytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
