package com.histmersion.util.storage

import java.nio.file.Path

trait TextFiles:
  def exists(path: Path): Boolean
  def read(path: Path): String
