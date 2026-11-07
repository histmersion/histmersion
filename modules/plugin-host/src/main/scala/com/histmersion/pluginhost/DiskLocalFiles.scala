package com.histmersion.pluginhost

import com.histmersion.plugin.LocalFiles
import java.nio.file.{Files, Path}
import scala.util.Try

final class DiskLocalFiles extends LocalFiles:
  def isFile(path: Path): Boolean = Files.isRegularFile(path)
  def isDirectory(path: Path): Boolean = Files.isDirectory(path)
  def exists(path: Path): Boolean = Files.exists(path)
  def isExecutable(path: Path): Boolean = Files.isExecutable(path)
  def read(path: Path): com.histmersion.pluginhost.DiskLocalFiles.FileBytes = Files.readAllBytes(path)
  def readPrefix(path: Path, length: com.histmersion.pluginhost.DiskLocalFiles.PrefixLength): com.histmersion.pluginhost.DiskLocalFiles.PrefixBytes =
    require(length >= 0)
    val in = Files.newInputStream(path)
    try in.readNBytes(length)
    finally in.close()

  def staged[A](name: com.histmersion.pluginhost.DiskLocalFiles.FileName, bytes: com.histmersion.pluginhost.DiskLocalFiles.FileBytes)(use: Path => Either[com.histmersion.pluginhost.DiskLocalFiles.Problem, A]): Either[com.histmersion.pluginhost.DiskLocalFiles.Problem, A] =
    if name.isEmpty || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.contains('\u0000') then
      Left(com.histmersion.pluginhost.DiskLocalFiles.Problem("cannot stage the file: expected a single filename"))
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

object DiskLocalFiles:
  type FileBytes = FileBytes.T
  object FileBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileName = FileName.T
  object FileName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PrefixBytes = PrefixBytes.T
  object PrefixBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PrefixLength = PrefixLength.T
  object PrefixLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

