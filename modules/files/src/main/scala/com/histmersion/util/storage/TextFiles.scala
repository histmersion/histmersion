package com.histmersion
package util.storage

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure

trait TextFiles:
  def exists(path: Path): Boolean
  def read(path: Path): TextFiles.ReadResult

object TextFiles:
  type ReadResult = ReadResult.T
  object ReadResult extends Newtype[String, Pure]
