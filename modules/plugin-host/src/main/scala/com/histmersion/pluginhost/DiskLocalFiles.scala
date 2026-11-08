package com.histmersion
package pluginhost

import com.histmersion.plugin.LocalFiles
import java.nio.file.{Files, Path}
import scala.util.Try

final class DiskLocalFiles extends LocalFiles:
  def isFile(path: Path): LocalFiles.IsFileResult = LocalFiles.IsFileResult(Files.isRegularFile(path))
  def isDirectory(path: Path): LocalFiles.IsDirectoryResult = LocalFiles.IsDirectoryResult(Files.isDirectory(path))
  def exists(path: Path): LocalFiles.ExistsResult = LocalFiles.ExistsResult(Files.exists(path))
  def isExecutable(path: Path): LocalFiles.IsExecutableResult = LocalFiles.IsExecutableResult(Files.isExecutable(path))
  def read(path: Path): LocalFiles.ReadResult = LocalFiles.ReadResult(Files.readAllBytes(path))
  def readPrefix(path: Path, length: LocalFiles.ReadPrefixLength): LocalFiles.ReadPrefixResult =
    require(length.value >= 0)
    val in = Files.newInputStream(path)
    try LocalFiles.ReadPrefixResult(in.readNBytes(length.value))
    finally in.close()

  def staged[A](name: LocalFiles.StagedName, bytes: LocalFiles.StagedBytes)(use: Path => Either[LocalFiles.StagedUseResultPart1, A]): Either[LocalFiles.StagedResultPart1, A] =
    val n = name.value
    if n.isEmpty || n == "." || n == ".." || n.contains('/') || n.contains('\\') || n.contains('\u0000') then
      Left(LocalFiles.StagedResultPart1("cannot stage the file: expected a single filename"))
    else
      Try(Files.createTempDirectory("histmersion-original-")).toEither.left
        .map(e => LocalFiles.StagedResultPart1(s"cannot stage the file: ${e.getMessage}"))
        .flatMap { dir =>
          try
            Try(Files.write(dir.resolve(n), bytes.value)).toEither.left
              .map(e => LocalFiles.StagedResultPart1(s"cannot stage the file: ${e.getMessage}"))
              .flatMap(p => use(p).left.map(e => LocalFiles.StagedResultPart1(e.value)))
          finally
            val entries = Files.list(dir)
            try entries.forEach(p => { val _ = Files.deleteIfExists(p) })
            finally entries.close()
            val _ = Files.deleteIfExists(dir)
        }
