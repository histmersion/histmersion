package com.histmersion.util.storage

import java.nio.file.{Files, Path}

final class DiskTextFiles extends TextFiles:
  def exists(path: Path): com.histmersion.util.storage.DiskTextFiles.ExistsResult = Files.exists(path)
  def read(path: Path): com.histmersion.util.storage.DiskTextFiles.ReadResult = Files.readString(path)

object DiskTextFiles:
  type ExistsResult = ExistsResult.T
  object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadResult = ReadResult.T
  object ReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
