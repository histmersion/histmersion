package com.histmersion
package plugin

import com.histmersion.util.problem.{Legacy, Problem}
import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure

/** Local filesystem operations supplied by the host. Path construction alone performs no I/O. Read failures preserve
  * the underlying IOException so a plugin can explain its own operation.
  */
trait LocalFiles:
  def isFile(path: Path): Boolean
  def isDirectory(path: Path): Boolean
  def exists(path: Path): Boolean
  def isExecutable(path: Path): Boolean
  def read(path: Path): LocalFiles.Content
  def readPrefix(path: Path, length: LocalFiles.ReadPrefixLength): LocalFiles.Content

  /** The named file exists only while use runs. Cleanup also runs when use returns Left or throws. name must be a
    * single filename, never a path. The supplied path must not escape the callback.
    */
  def staged[A](name: LocalFiles.StagedName, bytes: LocalFiles.Content)(
      use: Path => Either[Problem, A]
  ): Either[Problem, A]

object LocalFiles:
  type Content = Content.T
  object Content extends Newtype[Array[Byte], Pure]

  type ReadPrefixLength = ReadPrefixLength.T
  object ReadPrefixLength extends Newtype[Int, Pure]
  type StagedName = StagedName.T
  object StagedName extends Newtype[String, Pure]

  object Unavailable extends LocalFiles:
    export LocalFiles.{Content, ReadPrefixLength, StagedName}

    private def unavailable: Nothing = throw UnsupportedOperationException("this host supplies no local files")
    def isFile(path: Path): Boolean = unavailable
    def isDirectory(path: Path): Boolean = unavailable
    def exists(path: Path): Boolean = unavailable
    def isExecutable(path: Path): Boolean = unavailable
    def read(path: Path): Content = unavailable
    def readPrefix(path: Path, length: ReadPrefixLength): Content = unavailable
    def staged[A](name: StagedName, bytes: Content)(
        use: Path => Either[Problem, A]
    ): Either[Problem, A] =
      Left(Problem.Unclassified(Legacy.Text("this host supplies no local files")))
