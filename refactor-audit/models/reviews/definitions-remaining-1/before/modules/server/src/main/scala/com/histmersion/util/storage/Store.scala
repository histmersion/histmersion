package com.histmersion.util.storage

import com.histmersion.ByteCount

import java.time.Instant
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import java.io.InputStream
import java.security.{DigestOutputStream, MessageDigest}

/** One named place's files: what a supporting area or a domain is given instead of a path. Keys are relative and
  * checked ([[StoreKey]]); nothing outside the store can be named. A write either lands whole or not at all ([[put]],
  * [[reserve]]) unless it says otherwise ([[write]]). Messages name the file where an operation failed.
  */
trait Store:
  def location: Location

  /** Where `key` is, for messages and reports: the path on disk. */
  def where(key: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root): String

  /** The store below `prefix`, its keys relative to it. */
  def sub(prefix: com.histmersion.util.storage.Store.Key): Store

  def exists(key: com.histmersion.util.storage.Store.Key): Boolean
  def isFile(key: com.histmersion.util.storage.Store.Key): Boolean
  def isDirectory(key: com.histmersion.util.storage.Store.Key): Boolean

  /** Is `key` a symbolic link (whatever it points to)? */
  def isLink(key: com.histmersion.util.storage.Store.Key): Boolean

  /** Is `key` a file this process may run? */
  def isExecutable(key: com.histmersion.util.storage.Store.Key): Boolean

  /** `key` and what it is now, when it exists. */
  def stat(key: com.histmersion.util.storage.Store.Key): Option[Stored]

  /** The file at `key`, when there is one. */
  def get(key: com.histmersion.util.storage.Store.Key): Option[Blob]

  /** What `prefix` holds directly, files and directories, by name. */
  def list(prefix: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root): List[Stored]

  /** Every file below `prefix`, at any depth, by key. */
  def walk(prefix: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root): List[Stored]

  /** `bytes` as the file `key`: written beside it, then moved over it in one step. */
  def put(
      key: com.histmersion.util.storage.Store.Key,
      bytes: Array[Byte]
  ): Either[com.histmersion.util.storage.Store.Error, Stored]

  /** `bytes` written straight into `key`, created or truncated (a file whose readers check its size). */
  def write(
      key: com.histmersion.util.storage.Store.Key,
      bytes: Array[Byte]
  ): Either[com.histmersion.util.storage.Store.Error, Stored]

  /** A file to write beside `key`; with `throughLink`, beside the file a symbolic link at `key` points to. */
  def reserve(
      key: com.histmersion.util.storage.Store.Key,
      temp: TempName = TempName.Fixed,
      throughLink: Boolean = false
  ): Either[com.histmersion.util.storage.Store.Error, Reservation]

  /** `from` moved to `to`, replacing it. */
  def move(
      from: com.histmersion.util.storage.Store.Key,
      to: com.histmersion.util.storage.Store.Key
  ): Either[com.histmersion.util.storage.Store.Error, Unit]

  /** `from` copied to `to`, replacing it; with `keepAttributes`, its times and permissions too. */
  def copy(
      from: com.histmersion.util.storage.Store.Key,
      to: com.histmersion.util.storage.Store.Key,
      keepAttributes: Boolean = false
  ): Either[com.histmersion.util.storage.Store.Error, Unit]

  /** `prefix` made a directory, with its parents. */
  def makeDirectory(
      prefix: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root
  ): Either[com.histmersion.util.storage.Store.Error, Unit]

  /** The file `key` removed; `false` when there was none. */
  def delete(key: com.histmersion.util.storage.Store.Key): Boolean

  /** `prefix` and everything below it removed; the bytes its files held, `None` when it was not there. */
  def deleteTree(prefix: com.histmersion.util.storage.Store.Key): Option[ByteCount]

  /** The file's modification time set to `at` (a use, for a cache that drops the least recently used). */
  def touch(key: com.histmersion.util.storage.Store.Key, at: Instant): Unit

  /** The bytes every file of the store holds. */
  def size: ByteCount

  /** The space left on the disk the store is on. */
  def usableSpace: ByteCount

  /** What the store holds, counted by a walk `budget` bounds (shared by every walk of one measurement). The store's own
    * directory may be a symbolic link (a directory moved to another disk) and is followed; links below it are neither
    * followed nor counted, so a link to a big directory elsewhere is not this store's size and a loop cannot hang the
    * walk. An entry that cannot be read is skipped and counted. A store whose root is a file counts that file.
    */
  def measure(budget: MeasureBudget): com.histmersion.util.storage.Store.Measure

  /** The disk the store is (or, when its directory is not made yet, would be) on. */
  def volume: Option[com.histmersion.util.storage.Store.Volume]

  /** The names `prefix` holds directly, by name, without reading what they are; none when it is no directory. */
  def names(prefix: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root): List[String]

  /** The files `rule` names, removed. */
  def prune(rule: PruneRule): PruneReport

  /** The file `key` itself, for a program the supporting areas hand it to. */
  private[util] def onDisk(
      key: com.histmersion.util.storage.Store.Key
  ): Either[com.histmersion.util.storage.Store.Error, java.nio.file.Path]

  /** The same, as the process launcher takes it. */
  final private[util] def onDiskFile(
      key: com.histmersion.util.storage.Store.Key
  ): Either[com.histmersion.util.storage.Store.Error, HandedFile] = onDisk(key).map(_.toFile)

object Store:

  /** A [[ContentStore]] over a store: `<sha256>.<ext>` at its top. */
  final class Contents(store: Store) extends ContentStore:

    def find(sha256: String): Option[(ByteSource, String)] =
      store
        .list()
        .find(s => s.isFile && s.key.name.startsWith(sha256 + "."))
        .flatMap(s => store.get(s.key))
        .map(b => b.asSource -> b.key.name.drop(sha256.length + 1))

    /** Written beside its name, hashed, and moved into place only when the content IS `sha256`: the store is
      * content-addressed, and whoever reads it trusts the name.
      */
    def store(sha256: String, ext: String, in: InputStream): Either[String, Unit] =
      if find(sha256).isDefined then
        val _ = in.transferTo(java.io.OutputStream.nullOutputStream())
        Right(())
      else
        scala.util
          .Try {
            Key.of(s"$sha256.${ContentStore.safeExt(ext)}").flatMap { key =>
              store.reserve(key, TempName.Unique).left.map(_.message).flatMap { r =>
                try
                  val md = MessageDigest.getInstance("SHA-256")
                  val out = DigestOutputStream(r.output(), md)
                  try { val _ = in.transferTo(out) }
                  finally out.close()
                  val actual = ContentStore.hex(md.digest())
                  if actual != sha256 then Left(s"its content hashes to $actual")
                  else r.commit().left.map(_.message).map(_ => ())
                finally r.close()
              }
            }
          }
          .toEither
          .left
          .map(e => s"cannot store it in ${store.where()}: ${e.getMessage}")
          .flatten

  /** Why a store operation did not happen. `message` is what callers show; it names the path where the operation
    * failed, as the file system said.
    */
  enum Error(val message: String):
    case NotFound(where: String) extends Error(s"$where: no such file")
    case NotAFile(where: String) extends Error(s"$where is not a file")
    case Failed(reason: String) extends Error(reason)

    /** An in-memory store has no file to hand to another program. */
    case NotOnDisk(where: String) extends Error(s"$where is not on disk")

  /** A name inside a store: `/`-separated segments, relative, never `.` or `..`, no empty segment, no backslash or NUL.
    * The empty key is the store itself.
    */
  type StoreKeyRule = Match["|(?!\\.{1,2}(/|$))[^/\\\\\\x00]+(/(?!\\.{1,2}(/|$))[^/\\\\\\x00]+)*"]

  type Key = Key.T

  object Key extends RefinedType[String, StoreKeyRule]:
    val Root: Key = applyUnsafe("")

    /** A name made of one segment or several, each checked. */
    def of(segments: String*): Either[String, Key] = either(segments.filter(_.nonEmpty).mkString("/"))

    extension (key: Key)
      def isRoot: Boolean = key.value.isEmpty

      /** `key/child`; `child` may itself hold several segments. */
      def /(child: String): Either[String, Key] = of(key.value, child)

      /** `key/child` for a child the caller built from checked parts. */
      def child(name: String): Key = (key / name).fold(e => throw IllegalArgumentException(e), identity)

      def segments: List[String] = if key.isRoot then Nil else key.value.split('/').toList

      /** The last segment. */
      def name: String = segments.lastOption.getOrElse("")

      def parent: Key = applyUnsafe(segments.dropRight(1).mkString("/"))

      /** The key `name` with `suffix` added to its last segment. */
      def withSuffix(suffix: String): Key = parent.child(name + suffix)

      /** Is `key` this key or below it? */
      def within(prefix: Key): Boolean =
        prefix.isRoot || key.value == prefix.value || key.value.startsWith(prefix.value + "/")

      /** This key relative to `prefix` (which it must be within). */
      def relativeTo(prefix: Key): Key =
        if prefix.isRoot then key else applyUnsafe(key.value.drop(prefix.value.length).stripPrefix("/"))

  /** What a bounded walk of a store counted: the bytes and files below it, whether its directory is there, whether the
    * budget stopped the walk first (the counts are then a lower bound), and how many entries could not be read.
    */
  final case class Measure(
      bytes: ByteCount,
      files: Long,
      exists: Boolean,
      partial: Boolean = false,
      unreadable: Int = 0
  )

  object Measure:
    val absent: Measure = Measure(ByteCount.zero, 0L, exists = false)

  /** The disk a store is (or would be) on: its name, its size and the space this process may still use. */
  final case class Volume(id: String, total: ByteCount, usable: ByteCount)
