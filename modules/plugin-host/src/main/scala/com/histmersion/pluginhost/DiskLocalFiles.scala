package com.histmersion
package pluginhost
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.plugin.LocalFiles
import java.nio.file.{Files, Path}
import scala.util.Try

final class DiskLocalFiles extends LocalFiles:
  def isFile(path: Path): Boolean = Files.isRegularFile(path)
  def isDirectory(path: Path): Boolean = Files.isDirectory(path)
  def exists(path: Path): Boolean = Files.exists(path)
  def isExecutable(path: Path): Boolean = Files.isExecutable(path)
  def read(path: Path): LocalFiles.Content = LocalFiles.Content(Files.readAllBytes(path))
  def readPrefix(path: Path, length: LocalFiles.ReadPrefixLength): LocalFiles.Content =
    require(length.value >= 0)
    val in = Files.newInputStream(path)
    try LocalFiles.Content(in.readNBytes(length.value))
    finally in.close()

  def staged[A](name: LocalFiles.StagedName, bytes: LocalFiles.Content)(
      use: Path => Either[Problem, A]
  ): Either[Problem, A] =
    val n = name.value
    if n.isEmpty || n == "." || n == ".." || n.contains('/') || n.contains('\\') || n.contains('\u0000') then
      Left(Problem.Unclassified(Legacy.Text("cannot stage the file: expected a single filename")))
    else
      Try(Files.createTempDirectory("histmersion-original-")).toEither.left
        .map(e => Problem.Unclassified(Legacy.Text(s"cannot stage the file: ${e.getMessage}")))
        .flatMap { dir =>
          try
            Try(Files.write(dir.resolve(n), bytes.value)).toEither.left
              .map(e => Problem.Unclassified(Legacy.Text(s"cannot stage the file: ${e.getMessage}")))
              .flatMap(p => use(p))
          finally
            val entries = Files.list(dir)
            try
              entries.forEach { p =>
                val _ = Files.deleteIfExists(p)
              }
            finally entries.close()
            val _ = Files.deleteIfExists(dir)
        }
