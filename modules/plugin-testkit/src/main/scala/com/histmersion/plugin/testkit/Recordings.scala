package com.histmersion.plugin.testkit

import com.histmersion.plugin.testkit.internal.RecordedExchange

import java.nio.file.{Files, Path}

/** Paths of a test's recordings. */
object Recordings:
  /** Whether `dir` holds at least one recorded exchange. */
  def exist(dir: Path): Boolean =
    Files.isDirectory(dir) && {
      val files = Files.list(dir)
      try files.anyMatch(_.getFileName.toString.endsWith(RecordedExchange.Suffix))
      finally files.close()
    }
