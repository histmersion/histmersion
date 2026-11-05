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
  def read(path: Path): com.histmersion.plugin.LocalFiles.ReadResult
  def readPrefix(path: Path, length: com.histmersion.plugin.LocalFiles.ReadPrefixLength): com.histmersion.plugin.LocalFiles.ReadPrefixResult

  /** The named file exists only while use runs. Cleanup also runs when use returns Left or throws.
    * name must be a single filename, never a path. The supplied path must not escape the callback.
    */
  def staged[A](name: com.histmersion.plugin.LocalFiles.StagedName, bytes: com.histmersion.plugin.LocalFiles.StagedBytes)(use: Path => Either[com.histmersion.plugin.LocalFiles.StagedUseResultPart1, A]): Either[com.histmersion.plugin.LocalFiles.StagedResultPart1, A]

object LocalFiles:
  type ReadResult = ReadResult.T
  object ReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPrefixResult = ReadPrefixResult.T
  object ReadPrefixResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagedBytes = StagedBytes.T
  object StagedBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    type ReadResult = _root_.com.histmersion.plugin.LocalFiles.ReadResult
    val ReadResult: _root_.com.histmersion.plugin.LocalFiles.ReadResult.type = _root_.com.histmersion.plugin.LocalFiles.ReadResult
    type ReadPrefixResult = _root_.com.histmersion.plugin.LocalFiles.ReadPrefixResult
    val ReadPrefixResult: _root_.com.histmersion.plugin.LocalFiles.ReadPrefixResult.type = _root_.com.histmersion.plugin.LocalFiles.ReadPrefixResult
    type StagedBytes = _root_.com.histmersion.plugin.LocalFiles.StagedBytes
    val StagedBytes: _root_.com.histmersion.plugin.LocalFiles.StagedBytes.type = _root_.com.histmersion.plugin.LocalFiles.StagedBytes

    type IsFileResult = _root_.com.histmersion.plugin.LocalFiles.IsFileResult
    val IsFileResult: _root_.com.histmersion.plugin.LocalFiles.IsFileResult.type = _root_.com.histmersion.plugin.LocalFiles.IsFileResult
    type IsDirectoryResult = _root_.com.histmersion.plugin.LocalFiles.IsDirectoryResult
    val IsDirectoryResult: _root_.com.histmersion.plugin.LocalFiles.IsDirectoryResult.type = _root_.com.histmersion.plugin.LocalFiles.IsDirectoryResult
    type ExistsResult = _root_.com.histmersion.plugin.LocalFiles.ExistsResult
    val ExistsResult: _root_.com.histmersion.plugin.LocalFiles.ExistsResult.type = _root_.com.histmersion.plugin.LocalFiles.ExistsResult
    type IsExecutableResult = _root_.com.histmersion.plugin.LocalFiles.IsExecutableResult
    val IsExecutableResult: _root_.com.histmersion.plugin.LocalFiles.IsExecutableResult.type = _root_.com.histmersion.plugin.LocalFiles.IsExecutableResult
    type ReadPrefixLength = _root_.com.histmersion.plugin.LocalFiles.ReadPrefixLength
    val ReadPrefixLength: _root_.com.histmersion.plugin.LocalFiles.ReadPrefixLength.type = _root_.com.histmersion.plugin.LocalFiles.ReadPrefixLength
    type StagedResultPart1 = _root_.com.histmersion.plugin.LocalFiles.StagedResultPart1
    val StagedResultPart1: _root_.com.histmersion.plugin.LocalFiles.StagedResultPart1.type = _root_.com.histmersion.plugin.LocalFiles.StagedResultPart1
    type StagedName = _root_.com.histmersion.plugin.LocalFiles.StagedName
    val StagedName: _root_.com.histmersion.plugin.LocalFiles.StagedName.type = _root_.com.histmersion.plugin.LocalFiles.StagedName
    type StagedUseResultPart1 = _root_.com.histmersion.plugin.LocalFiles.StagedUseResultPart1
    val StagedUseResultPart1: _root_.com.histmersion.plugin.LocalFiles.StagedUseResultPart1.type = _root_.com.histmersion.plugin.LocalFiles.StagedUseResultPart1

    private def unavailable: Nothing = throw UnsupportedOperationException("this host supplies no local files")
    def isFile(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.IsFileResult = unavailable
    def isDirectory(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.IsDirectoryResult = unavailable
    def exists(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.ExistsResult = unavailable
    def isExecutable(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.IsExecutableResult = unavailable
    def read(path: Path): com.histmersion.plugin.LocalFiles.Unavailable.ReadResult = unavailable
    def readPrefix(path: Path, length: com.histmersion.plugin.LocalFiles.Unavailable.ReadPrefixLength): com.histmersion.plugin.LocalFiles.Unavailable.ReadPrefixResult = unavailable
    def staged[A](name: com.histmersion.plugin.LocalFiles.Unavailable.StagedName, bytes: com.histmersion.plugin.LocalFiles.Unavailable.StagedBytes)(use: Path => Either[com.histmersion.plugin.LocalFiles.Unavailable.StagedUseResultPart1, A]): Either[com.histmersion.plugin.LocalFiles.Unavailable.StagedResultPart1, A] =
      Left(_root_.com.histmersion.plugin.LocalFiles.Unavailable.StagedResultPart1("this host supplies no local files"))
