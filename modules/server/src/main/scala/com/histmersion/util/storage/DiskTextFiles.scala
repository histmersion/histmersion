package com.histmersion
package util.storage

import java.nio.file.{Files, Path}
import com.histmersion.lint.{BoundaryKind, atBoundary}

@atBoundary(BoundaryKind.Process, "reads text files from disk")
final class DiskTextFiles extends TextFiles:
  def exists(path: Path): Boolean = Files.exists(path)
  def read(path: Path): TextFiles.Text = TextFiles.Text(Files.readString(path))
