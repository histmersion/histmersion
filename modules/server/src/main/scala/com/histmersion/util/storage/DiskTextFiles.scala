package com.histmersion.util.storage

import java.nio.file.{Files, Path}

final class DiskTextFiles extends TextFiles:
  def exists(path: Path): Boolean = Files.exists(path)
  def read(path: Path): TextFiles.ReadResult = TextFiles.ReadResult(Files.readString(path))

