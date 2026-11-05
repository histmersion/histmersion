package com.histmersion.plugin

import java.nio.file.Path

/** Local filesystem operations supplied by the host. Path construction alone performs no I/O.
  * Read failures preserve the underlying IOException so a plugin can explain its own operation.
  */
trait LocalFiles:
  def isFile(path: Path): com.histmersion.plugin.LocalFiles.IsFileResult
  def isDirectory(path: Path): com.histmersion.plugin.LocalFiles.IsDirectoryResult
  def exists(path: Path): com.histmersion.plugin.LocalFiles.ExistsResult
  def isExecutable(path: Path): com.histmersion.plugin.LocalFiles.IsExecutableResult
  def read(path: Path): Array[Byte]
  def readPrefix(path: Path, length: com.histmersion.plugin.LocalFiles.ReadPrefixLength): Array[Byte]

  /** The named file exists only while use runs. Cleanup also runs when use returns Left or throws.
    * name must be a single filename, never a path. The supplied path must not escape the callback.
    */
  def staged[A](name: com.histmersion.plugin.LocalFiles.StagedName, bytes: Array[Byte])(use: Path => Either[com.histmersion.plugin.LocalFiles.StagedUseResultPart1, A]): Either[com.histmersion.plugin.LocalFiles.StagedResultPart1, A]

object LocalFiles:
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

  object Unavailable extends LocalFiles:
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

    private def unavailable: Nothing = throw UnsupportedOperationException("this host supplies no local files")
    def isFile(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.IsFileResult = unavailable
    def isDirectory(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.IsDirectoryResult = unavailable
    def exists(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.ExistsResult = unavailable
    def isExecutable(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.IsExecutableResult = unavailable
    def read(path: Path): Array[Byte] = unavailable
    def readPrefix(path: Path, length: com.histmersion.plugin.LocalFiles.Unavailable.ReadPrefixLength): Array[Byte] = unavailable
    def staged[A](name: com.histmersion.plugin.LocalFiles.Unavailable.StagedName, bytes: Array[Byte])(use: Path => Either[com.histmersion.plugin.LocalFiles.Unavailable.StagedUseResultPart1, A]): Either[com.histmersion.plugin.LocalFiles.Unavailable.StagedResultPart1, A] =
      Left("this host supplies no local files")
