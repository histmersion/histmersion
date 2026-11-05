package com.histmersion.util.storage

import java.nio.file.Path

trait TextFiles:
  def exists(path: Path): com.histmersion.util.storage.TextFiles.ExistsResult
  def read(path: Path): com.histmersion.util.storage.TextFiles.ReadResult

object TextFiles:
  type ExistsResult = ExistsResult.T
  object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadResult = ReadResult.T
  object ReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
