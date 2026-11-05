package com.histmersion.plugin.testkit.internal

import java.nio.file.Path

/** Paths a test names relative to its project. A build tool starts the test JVM in the directory it was itself started
  * in, which need not be the project's (`scala-cli test /path/to/plugin`); a path relative to the working directory
  * would then point elsewhere, and a recorded case would be skipped as having no recordings.
  */
object ProjectPaths:
  /** `dir` as given when it is absolute; else under the project directory when the build names one, else under the
    * working directory.
    */
  type ResolveProjectDir = ResolveProjectDir.T
  object ResolveProjectDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def resolve(dir: Path, projectDir: Option[com.histmersion.plugin.testkit.internal.ProjectPaths.ResolveProjectDir]): Path =
    if dir.isAbsolute then dir
    else projectDir.map(_.trim).filter(_.nonEmpty).fold(dir.toAbsolutePath)(p => Path.of(p).resolve(dir)).normalize
