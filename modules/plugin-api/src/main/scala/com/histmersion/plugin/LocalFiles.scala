package com.histmersion
package plugin

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure

/** Local filesystem operations supplied by the host. Path construction alone performs no I/O.
  * Read failures preserve the underlying IOException so a plugin can explain its own operation.
  */
trait LocalFiles:
  def isFile(path: Path): LocalFiles.IsFileResult
  def isDirectory(path: Path): LocalFiles.IsDirectoryResult
  def exists(path: Path): LocalFiles.ExistsResult
  def isExecutable(path: Path): LocalFiles.IsExecutableResult
  def read(path: Path): LocalFiles.ReadResult
  def readPrefix(path: Path, length: LocalFiles.ReadPrefixLength): LocalFiles.ReadPrefixResult

  /** The named file exists only while use runs. Cleanup also runs when use returns Left or throws.
    * name must be a single filename, never a path. The supplied path must not escape the callback.
    */
  def staged[A](name: LocalFiles.StagedName, bytes: LocalFiles.StagedBytes)(use: Path => Either[LocalFiles.StagedUseResultPart1, A]): Either[LocalFiles.StagedResultPart1, A]

object LocalFiles:
  type ReadResult = ReadResult.T
  object ReadResult extends Newtype[Array[Byte], Pure]
  type ReadPrefixResult = ReadPrefixResult.T
  object ReadPrefixResult extends Newtype[Array[Byte], Pure]
  type StagedBytes = StagedBytes.T
  object StagedBytes extends Newtype[Array[Byte], Pure]

  type IsFileResult = IsFileResult.T
  object IsFileResult extends Newtype[Boolean, Pure]
  type IsDirectoryResult = IsDirectoryResult.T
  object IsDirectoryResult extends Newtype[Boolean, Pure]
  type ExistsResult = ExistsResult.T
  object ExistsResult extends Newtype[Boolean, Pure]
  type IsExecutableResult = IsExecutableResult.T
  object IsExecutableResult extends Newtype[Boolean, Pure]
  type ReadPrefixLength = ReadPrefixLength.T
  object ReadPrefixLength extends Newtype[Int, Pure]
  type StagedResultPart1 = StagedResultPart1.T
  object StagedResultPart1 extends Newtype[String, Pure]
  type StagedName = StagedName.T
  object StagedName extends Newtype[String, Pure]
  type StagedUseResultPart1 = StagedUseResultPart1.T
  object StagedUseResultPart1 extends Newtype[String, Pure]

  object Unavailable extends LocalFiles:
    export LocalFiles.{ReadResult, ReadPrefixResult, StagedBytes, IsFileResult, IsDirectoryResult, ExistsResult, IsExecutableResult, ReadPrefixLength, StagedResultPart1, StagedName, StagedUseResultPart1}

    private def unavailable: Nothing = throw UnsupportedOperationException("this host supplies no local files")
    def isFile(path: Path): IsFileResult = unavailable
    def isDirectory(path: Path): IsDirectoryResult = unavailable
    def exists(path: Path): ExistsResult = unavailable
    def isExecutable(path: Path): IsExecutableResult = unavailable
    def read(path: Path): ReadResult = unavailable
    def readPrefix(path: Path, length: ReadPrefixLength): ReadPrefixResult = unavailable
    def staged[A](name: StagedName, bytes: StagedBytes)(use: Path => Either[StagedUseResultPart1, A]): Either[StagedResultPart1, A] =
      Left(StagedResultPart1("this host supplies no local files"))
