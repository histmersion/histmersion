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
  def where(key: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root): com.histmersion.util.storage.Store.WhereResult

  /** The store below `prefix`, its keys relative to it. */
  def sub(prefix: com.histmersion.util.storage.Store.Key): Store

  def exists(key: com.histmersion.util.storage.Store.Key): com.histmersion.util.storage.Store.ExistsResult
  def isFile(key: com.histmersion.util.storage.Store.Key): com.histmersion.util.storage.Store.IsFileResult
  def isDirectory(key: com.histmersion.util.storage.Store.Key): com.histmersion.util.storage.Store.IsDirectoryResult

  /** Is `key` a symbolic link (whatever it points to)? */
  def isLink(key: com.histmersion.util.storage.Store.Key): com.histmersion.util.storage.Store.IsLinkResult

  /** Is `key` a file this process may run? */
  def isExecutable(key: com.histmersion.util.storage.Store.Key): com.histmersion.util.storage.Store.IsExecutableResult

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
      bytes: com.histmersion.util.storage.Store.PutBytes
  ): Either[com.histmersion.util.storage.Store.Error, Stored]

  /** `bytes` written straight into `key`, created or truncated (a file whose readers check its size). */
  def write(
      key: com.histmersion.util.storage.Store.Key,
      bytes: com.histmersion.util.storage.Store.WriteBytes
  ): Either[com.histmersion.util.storage.Store.Error, Stored]

  /** A file to write beside `key`; with `throughLink`, beside the file a symbolic link at `key` points to. */
  def reserve(
      key: com.histmersion.util.storage.Store.Key,
      temp: TempName = TempName.Fixed,
      throughLink: com.histmersion.util.storage.Store.ReserveThroughLink = false
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
      keepAttributes: com.histmersion.util.storage.Store.CopyKeepAttributes = false
  ): Either[com.histmersion.util.storage.Store.Error, Unit]

  /** `prefix` made a directory, with its parents. */
  def makeDirectory(
      prefix: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root
  ): Either[com.histmersion.util.storage.Store.Error, Unit]

  /** The file `key` removed; `false` when there was none. */
  def delete(key: com.histmersion.util.storage.Store.Key): com.histmersion.util.storage.Store.DeleteResult

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
  def names(prefix: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root): List[com.histmersion.util.storage.Store.NamesResult]

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
  type PutBytes = PutBytes.T
  object PutBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteBytes = WriteBytes.T
  object WriteBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type WhereResult = WhereResult.T
  object WhereResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistsResult = ExistsResult.T
  object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsFileResult = IsFileResult.T
  object IsFileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDirectoryResult = IsDirectoryResult.T
  object IsDirectoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsLinkResult = IsLinkResult.T
  object IsLinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsExecutableResult = IsExecutableResult.T
  object IsExecutableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReserveThroughLink = ReserveThroughLink.T
  object ReserveThroughLink extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CopyKeepAttributes = CopyKeepAttributes.T
  object CopyKeepAttributes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesResult = NamesResult.T
  object NamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final class Contents(store: Store) extends ContentStore:

    def find(sha256: com.histmersion.util.storage.Store.Contents.FindSha256): Option[(ByteSource, com.histmersion.util.storage.Store.Contents.FindResultPart2)] =
      store
        .list()
        .find(s => s.isFile && s.key.name.startsWith(sha256 + "."))
        .flatMap(s => store.get(s.key))
        .map(b => b.asSource -> b.key.name.drop(sha256.length + 1))

    /** Written beside its name, hashed, and moved into place only when the content IS `sha256`: the store is
      * content-addressed, and whoever reads it trusts the name.
      */
    def store(sha256: com.histmersion.util.storage.Store.Contents.StoreSha256, ext: com.histmersion.util.storage.Store.Contents.StoreExt, in: InputStream): Either[com.histmersion.util.storage.Store.Contents.StoreResultPart1, Unit] =
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

  object Contents:
    type FindResultPart2 = FindResultPart2.T
    object FindResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindSha256 = FindSha256.T
    object FindSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoreResultPart1 = StoreResultPart1.T
    object StoreResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoreSha256 = StoreSha256.T
    object StoreSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoreExt = StoreExt.T
    object StoreExt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Why a store operation did not happen. `message` is what callers show; it names the path where the operation
    * failed, as the file system said.
    */
  enum Error(val message: Error.Message):
    case NotFound(where: NotFound.Where) extends Error(s"$where: no such file")
    case NotAFile(where: NotAFile.Where) extends Error(s"$where is not a file")
    case Failed(reason: Failed.Reason) extends Error(reason)

    /** An in-memory store has no file to hand to another program. */
    case NotOnDisk(where: NotOnDisk.Where) extends Error(s"$where is not on disk")

  object Error:
    object NotFound:
      type Where = Where.T
      object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object NotAFile:
      type Where = Where.T
      object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Failed:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object NotOnDisk:
      type Where = Where.T
      object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Message = Message.T
    object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A name inside a store: `/`-separated segments, relative, never `.` or `..`, no empty segment, no backslash or NUL.
    * The empty key is the store itself.
    */
  type StoreKeyRule = Match["|(?!\\.{1,2}(/|$))[^/\\\\\\x00]+(/(?!\\.{1,2}(/|$))[^/\\\\\\x00]+)*"]

  type Key = Key.T

  object Key extends RefinedType[String, StoreKeyRule]:
    type OfResultPart1 = OfResultPart1.T
    object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsRootResult = IsRootResult.T
    object IsRootResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChildName = ChildName.T
    object ChildName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SegmentsResult = SegmentsResult.T
    object SegmentsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NameResult = NameResult.T
    object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WithSuffixSuffix = WithSuffixSuffix.T
    object WithSuffixSuffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WithinResult = WithinResult.T
    object WithinResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Root: Key = applyUnsafe("")

    /** A name made of one segment or several, each checked. */
    def of(segments: String*): Either[com.histmersion.util.storage.Store.Key.OfResultPart1, Key] = either(segments.filter(_.nonEmpty).mkString("/"))

    extension (key: Key)
      def isRoot: com.histmersion.util.storage.Store.Key.IsRootResult = key.value.isEmpty

      /** `key/child`; `child` may itself hold several segments. */
      def /(child: String): Either[String, Key] = of(key.value, child)

      /** `key/child` for a child the caller built from checked parts. */
      def child(name: com.histmersion.util.storage.Store.Key.ChildName): Key = (key / name).fold(e => throw IllegalArgumentException(e), identity)

      def segments: List[com.histmersion.util.storage.Store.Key.SegmentsResult] = if key.isRoot then Nil else key.value.split('/').toList

      /** The last segment. */
      def name: com.histmersion.util.storage.Store.Key.NameResult = segments.lastOption.getOrElse("")

      def parent: Key = applyUnsafe(segments.dropRight(1).mkString("/"))

      /** The key `name` with `suffix` added to its last segment. */
      def withSuffix(suffix: com.histmersion.util.storage.Store.Key.WithSuffixSuffix): Key = parent.child(name + suffix)

      /** Is `key` this key or below it? */
      def within(prefix: Key): com.histmersion.util.storage.Store.Key.WithinResult =
        prefix.isRoot || key.value == prefix.value || key.value.startsWith(prefix.value + "/")

      /** This key relative to `prefix` (which it must be within). */
      def relativeTo(prefix: Key): Key =
        if prefix.isRoot then key else applyUnsafe(key.value.drop(prefix.value.length).stripPrefix("/"))

  /** What a bounded walk of a store counted: the bytes and files below it, whether its directory is there, whether the
    * budget stopped the walk first (the counts are then a lower bound), and how many entries could not be read.
    */
  final case class Measure(
      bytes: ByteCount,
      files: Measure.Files,
      exists: Measure.Exists,
      partial: Measure.Partial = false,
      unreadable: Measure.Unreadable = 0
  )

  object Measure:
    type Files = Files.T
    object Files extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Exists = Exists.T
    object Exists extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Partial = Partial.T
    object Partial extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unreadable = Unreadable.T
    object Unreadable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val absent: Measure = Measure(ByteCount.zero, 0L, exists = false)

  /** The disk a store is (or would be) on: its name, its size and the space this process may still use. */
  final case class Volume(id: Volume.Id, total: ByteCount, usable: ByteCount)

  object Volume:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
