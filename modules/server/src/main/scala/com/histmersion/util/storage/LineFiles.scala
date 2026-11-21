package com.histmersion
package util.storage

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** A directory of text files written a line at a time and read back whole (the structured log's rolling files), its
  * files named by their names in it. A directory not set (`dir` gives none) holds nothing and takes nothing; a file
  * that cannot be read reads as empty.
  */
final class LineFiles(dir: () => Option[Path]):

  /** The names of what the directory holds; none when it is not set or not there. */
  def names: List[String] =
    dir().filter(Files.isDirectory(_)).toList.flatMap { d =>
      val stream = Files.list(d)
      try stream.iterator().asScala.map(_.getFileName.toString).toList
      finally stream.close()
    }

  /** Each of `files` (a name and its lines) appended to, created with the directory when missing: `false` when the
    * directory is not set or a file could not be written.
    */
  def append(files: Iterable[(String, Iterable[String])]): Boolean =
    dir().exists { d =>
      Try {
        val _ = Files.createDirectories(d)
        files.foreach { (name, lines) =>
          val w = Files.newBufferedWriter(
            d.resolve(name),
            UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE
          )
          try
            lines.foreach { line =>
              w.write(line)
              w.newLine()
            }
          finally w.close()
        }
      }.isSuccess
    }

  /** The lines of the file `name`. */
  def lines(name: String): Vector[String] =
    dir()
      .flatMap(d => Try(Files.readAllLines(d.resolve(name), UTF_8).asScala.toVector).toOption)
      .getOrElse(Vector.empty)

  /** The size of the file `name` in bytes (zero when it cannot be read). */
  def size(name: String): Long = dir().flatMap(d => Try(Files.size(d.resolve(name))).toOption).getOrElse(0L)

  /** The file `name` removed: `false` when it was not there or could not be. */
  def delete(name: String): Boolean = dir().exists(d => Try(Files.deleteIfExists(d.resolve(name))).getOrElse(false))
