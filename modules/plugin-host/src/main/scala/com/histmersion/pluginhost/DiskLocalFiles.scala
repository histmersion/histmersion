package com.histmersion.pluginhost

import com.histmersion.plugin.LocalFiles
import java.nio.file.{Files, Path}
import scala.util.Try

final class DiskLocalFiles extends LocalFiles:
  def isFile(path: Path): com.histmersion.pluginhost.DiskLocalFiles.IsFileResult = Files.isRegularFile(path)
  def isDirectory(path: Path): com.histmersion.pluginhost.DiskLocalFiles.IsDirectoryResult = Files.isDirectory(path)
  def exists(path: Path): com.histmersion.pluginhost.DiskLocalFiles.ExistsResult = Files.exists(path)
  def isExecutable(path: Path): com.histmersion.pluginhost.DiskLocalFiles.IsExecutableResult = Files.isExecutable(path)
  def read(path: Path): Array[Byte] = Files.readAllBytes(path)
  def readPrefix(path: Path, length: com.histmersion.pluginhost.DiskLocalFiles.ReadPrefixLength): Array[Byte] =
    require(length >= 0)
    val in = Files.newInputStream(path)
    try in.readNBytes(length)
    finally in.close()

  def staged[A](name: com.histmersion.pluginhost.DiskLocalFiles.StagedName, bytes: Array[Byte])(use: Path => Either[com.histmersion.pluginhost.DiskLocalFiles.StagedUseResultPart1, A]): Either[com.histmersion.pluginhost.DiskLocalFiles.StagedResultPart1, A] =
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

object DiskLocalFiles:
  type IsFileResult = IsFileResult.T
  object IsFileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDirectoryResult = IsDirectoryResult.T
  object IsDirectoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistsResult = ExistsResult.T
  object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsExecutableResult = IsExecutableResult.T
  object IsExecutableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPrefixLength = ReadPrefixLength.T
  object ReadPrefixLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagedResultPart1 = StagedResultPart1.T
  object StagedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagedName = StagedName.T
  object StagedName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagedUseResultPart1 = StagedUseResultPart1.T
  object StagedUseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
