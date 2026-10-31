package com.histmersion.util.storage

import com.histmersion.values.ByteCount

import java.time.Instant

/** One named place's files: what a supporting area or a domain is given instead of a path. Keys are relative and
  * checked ([[StoreKey]]); nothing outside the store can be named. A write either lands whole or not at all ([[put]],
  * [[reserve]]) unless it says otherwise ([[write]]). Messages name the file where an operation failed.
  */
trait Store:
  def location: Location

  /** Where `key` is, for messages and reports: the path on disk. */
  def where(key: StoreKey = StoreKey.Root): String

  /** The store below `prefix`, its keys relative to it. */
  def sub(prefix: StoreKey): Store

  def exists(key: StoreKey): Boolean
  def isFile(key: StoreKey): Boolean
  def isDirectory(key: StoreKey): Boolean

  /** Is `key` a symbolic link (whatever it points to)? */
  def isLink(key: StoreKey): Boolean

  /** Is `key` a file this process may run? */
  def isExecutable(key: StoreKey): Boolean

  /** `key` and what it is now, when it exists. */
  def stat(key: StoreKey): Option[Stored]

  /** The file at `key`, when there is one. */
  def get(key: StoreKey): Option[Blob]

  /** What `prefix` holds directly, files and directories, by name. */
  def list(prefix: StoreKey = StoreKey.Root): List[Stored]

  /** Every file below `prefix`, at any depth, by key. */
  def walk(prefix: StoreKey = StoreKey.Root): List[Stored]

  /** `bytes` as the file `key`: written beside it, then moved over it in one step. */
  def put(key: StoreKey, bytes: Array[Byte]): Either[StoreError, Stored]

  /** `bytes` written straight into `key`, created or truncated (a file whose readers check its size). */
  def write(key: StoreKey, bytes: Array[Byte]): Either[StoreError, Stored]

  /** A file to write beside `key`; with `throughLink`, beside the file a symbolic link at `key` points to. */
  def reserve(
      key: StoreKey,
      temp: TempName = TempName.Fixed,
      throughLink: Boolean = false
  ): Either[StoreError, Reservation]

  /** `from` moved to `to`, replacing it. */
  def move(from: StoreKey, to: StoreKey): Either[StoreError, Unit]

  /** `from` copied to `to`, replacing it; with `keepAttributes`, its times and permissions too. */
  def copy(from: StoreKey, to: StoreKey, keepAttributes: Boolean = false): Either[StoreError, Unit]

  /** `prefix` made a directory, with its parents. */
  def makeDirectory(prefix: StoreKey = StoreKey.Root): Either[StoreError, Unit]

  /** The file `key` removed; `false` when there was none. */
  def delete(key: StoreKey): Boolean

  /** `prefix` and everything below it removed; the bytes its files held, `None` when it was not there. */
  def deleteTree(prefix: StoreKey): Option[ByteCount]

  /** The file's modification time set to `at` (a use, for a cache that drops the least recently used). */
  def touch(key: StoreKey, at: Instant): Unit

  /** The bytes every file of the store holds. */
  def size: ByteCount

  /** The space left on the disk the store is on. */
  def usableSpace: ByteCount

  /** What the store holds, counted by a walk `budget` bounds (shared by every walk of one measurement). The store's own
    * directory may be a symbolic link (a directory moved to another disk) and is followed; links below it are neither
    * followed nor counted, so a link to a big directory elsewhere is not this store's size and a loop cannot hang the
    * walk. An entry that cannot be read is skipped and counted. A store whose root is a file counts that file.
    */
  def measure(budget: MeasureBudget): StoreMeasure

  /** The disk the store is (or, when its directory is not made yet, would be) on. */
  def volume: Option[StoreVolume]

  /** The names `prefix` holds directly, by name, without reading what they are; none when it is no directory. */
  def names(prefix: StoreKey = StoreKey.Root): List[String]

  /** The files `rule` names, removed. */
  def prune(rule: PruneRule): PruneReport

  /** The file `key` itself, for a program the supporting areas hand it to. */
  private[util] def onDisk(key: StoreKey): Either[StoreError, java.nio.file.Path]

  /** The same, as the process launcher takes it. */
  final private[util] def onDiskFile(key: StoreKey): Either[StoreError, HandedFile] = onDisk(key).map(_.toFile)
