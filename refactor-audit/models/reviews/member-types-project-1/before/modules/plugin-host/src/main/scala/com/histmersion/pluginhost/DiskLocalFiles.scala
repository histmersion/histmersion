package com.histmersion.pluginhost

import com.histmersion.plugin.LocalFiles
import java.nio.file.{Files, Path}
import scala.util.Try

final class DiskLocalFiles extends LocalFiles:
  def isFile(path: Path): Boolean = Files.isRegularFile(path)
  def isDirectory(path: Path): Boolean = Files.isDirectory(path)
  def exists(path: Path): Boolean = Files.exists(path)
  def isExecutable(path: Path): Boolean = Files.isExecutable(path)
  def read(path: Path): Array[Byte] = Files.readAllBytes(path)
  def readPrefix(path: Path, length: Int): Array[Byte] =
    require(length >= 0)
    val in = Files.newInputStream(path)
    try in.readNBytes(length)
    finally in.close()

  def staged[A](name: String, bytes: Array[Byte])(use: Path => Either[String, A]): Either[String, A] =
    if name.isEmpty || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.contains('\u0000') then
      Left("cannot stage the file: expected a single filename")
    else
      Try(Files.createTempDirectory("histmersion-original-")).toEither.left
        .map(e => s"cannot stage the file: ${e.getMessage}")
        .flatMap { dir =>
          try
            Try(Files.write(dir.resolve(name), bytes)).toEither.left
              .map(e => s"cannot stage the file: ${e.getMessage}").flatMap(use)
          finally
            val entries = Files.list(dir)
            try entries.forEach(p => { val _ = Files.deleteIfExists(p) })
            finally entries.close()
            val _ = Files.deleteIfExists(dir)
        }
