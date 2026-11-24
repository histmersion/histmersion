package com.histmersion
package pluginhost
import com.histmersion.util.problem.{External, Problem, Problems}

import com.histmersion.plugin.LocalFiles
import java.nio.file.{Files, Path}
import scala.util.Try
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

@atBoundary(BoundaryKind.Process, "gives plugins the local file operations on disk")
final class DiskLocalFiles extends LocalFiles:
  def isFile(path: Path): Boolean = Files.isRegularFile(path)
  def isDirectory(path: Path): Boolean = Files.isDirectory(path)
  def exists(path: Path): Boolean = Files.exists(path)
  def isExecutable(path: Path): Boolean = Files.isExecutable(path)
  def read(path: Path): LocalFiles.Content = LocalFiles.Content(Files.readAllBytes(path))
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def readPrefix(path: Path, length: LocalFiles.ReadPrefixLength): LocalFiles.Content =
    require(length.value >= 0)
    val in = Files.newInputStream(path)
    try LocalFiles.Content(in.readNBytes(length.value))
    finally in.close()

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
  def staged[A](name: LocalFiles.StagedName, bytes: LocalFiles.Content)(
      use: Path => Either[Problems, A]
  ): Either[Problems, A] =
    val n = name.value
    if n.isEmpty || n == "." || n == ".." || n.contains('/') || n.contains('\\') || n.contains('\u0000') then
      Left(Problem.Io.Wrong(Problem.Io.Path(n), Problem.Io.Kind.File))
    else
      Try(Files.createTempDirectory("histmersion-original-")).toEither.left
        .map(e =>
          Problem.Io.File(Problem.Io.Access.Create, Problem.Io.Path("histmersion-original-"), External.Reported.of(e))
        )
        .flatMap { dir =>
          try
            Try(Files.write(dir.resolve(n), bytes.value)).toEither.left
              .map(e =>
                Problem.Io.File(Problem.Io.Access.Write, Problem.Io.Path(s"${dir.resolve(n)}"), External.Reported.of(e))
              )
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
