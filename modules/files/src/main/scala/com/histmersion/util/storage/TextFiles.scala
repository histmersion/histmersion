package com.histmersion
package util.storage

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}

@atBoundary(BoundaryKind.Process, "reads text files")
trait TextFiles:
  def exists(path: Path): Boolean
  def read(path: Path): TextFiles.Text

object TextFiles:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
