package com.histmersion
package util.storage

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import scala.util.Try

final class LineFiles(dir: () => Option[Path]):

  def names: List[String] =
    dir().filter(Files.isDirectory(_)).toList.flatMap { d =>
      val stream = Files.list(d)
      try stream.iterator().asScala.map(_.getFileName.toString).toList
      finally stream.close()
    }

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

  def lines(name: String): Vector[String] =
    dir()
      .flatMap(d => Try(Files.readAllLines(d.resolve(name), UTF_8).asScala.toVector).toOption)
      .getOrElse(Vector.empty)

  def size(name: String): Long = dir().flatMap(d => Try(Files.size(d.resolve(name))).toOption).getOrElse(0L)

  def delete(name: String): Boolean = dir().exists(d => Try(Files.deleteIfExists(d.resolve(name))).getOrElse(false))
