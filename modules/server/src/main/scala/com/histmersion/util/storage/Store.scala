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
  def where(key: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root): com.histmersion.util.storage.Blob.Location

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
      bytes: com.histmersion.util.storage.ByteSource.Content
  ): Either[com.histmersion.util.storage.Store.Error, Stored]

  /** `bytes` written straight into `key`, created or truncated (a file whose readers check its size). */
  def write(
      key: com.histmersion.util.storage.Store.Key,
      bytes: com.histmersion.util.storage.ByteSource.Content
  ): Either[com.histmersion.util.storage.Store.Error, Stored]

  /** A file to write beside `key`; with `throughLink`, beside the file a symbolic link at `key` points to. */
  def reserve(
      key: com.histmersion.util.storage.Store.Key,
      temp: TempName = TempName.Fixed,
      throughLink: com.histmersion.util.storage.Store.ThroughLink = com.histmersion.util.storage.Store.ThroughLink(false)
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
      keepAttributes: com.histmersion.util.storage.Store.KeepPermissions = com.histmersion.util.storage.Store.KeepPermissions(false)
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
  def names(prefix: com.histmersion.util.storage.Store.Key = com.histmersion.util.storage.Store.Key.Root): List[com.histmersion.util.storage.Store.EntryName]

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
  type EntryName = EntryName.T
  object EntryName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepPermissions = KeepPermissions.T
  object KeepPermissions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThroughLink = ThroughLink.T
  object ThroughLink extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A [[ContentStore]] over a store: `<sha256>.<ext>` at its top. */


  final class Contents(store: Store) extends ContentStore:

    def find(sha256: com.histmersion.Sha256): Option[(ByteSource, com.histmersion.util.storage.ContentStore.Ext)] =
      store
        .list()
        .find(s => s.isFile && s.key.name.value.startsWith(sha256.value + "."))
        .flatMap(s => store.get(s.key))
        .map(b => b.asSource -> ContentStore.Ext(b.key.name.value.drop(sha256.value.length + 1)))

    /** Written beside its name, hashed, and moved into place only when the content IS `sha256`: the store is
      * content-addressed, and whoever reads it trusts the name.
      */
    def store(sha256: com.histmersion.Sha256, ext: com.histmersion.util.storage.ContentStore.Ext, in: InputStream): Either[com.histmersion.util.storage.ContentStore.Problem, Unit] =
      if find(sha256).isDefined then
        val _ = in.transferTo(java.io.OutputStream.nullOutputStream())
        Right(())
      else
        scala.util
          .Try {
            Key.of(s"$sha256.${ContentStore.safeExt(ext)}").left.map(p => ContentStore.Problem(p.value)).flatMap { key =>
              store.reserve(key, TempName.Unique).left.map(e => ContentStore.Problem(e.message.value)).flatMap { r =>
                try
                  val md = MessageDigest.getInstance("SHA-256")
                  val out = DigestOutputStream(r.output(), md)
                  try { val _ = in.transferTo(out) }
                  finally out.close()
                  val actual = ContentStore.hex(ContentStore.Digest(md.digest()))
                  if actual != sha256 then Left(ContentStore.Problem(s"its content hashes to $actual"))
                  else r.commit().left.map(e => ContentStore.Problem(e.message.value)).map(_ => ())
                finally r.close()
              }
            }
          }
          .toEither
          .left
          .map(e => ContentStore.Problem(s"cannot store it in ${store.where()}: ${e.getMessage}"))
          .flatten


  /** Why a store operation did not happen. `message` is what callers show; it names the path where the operation
    * failed, as the file system said.
    */
  enum Error(val message: Error.Message):
    case NotFound(where: Error.NotFound.Where) extends Error(Error.Message(s"$where: no such file"))
    case NotAFile(where: Error.NotAFile.Where) extends Error(Error.Message(s"$where is not a file"))
    case Failed(reason: Error.Failed.Reason) extends Error(Error.Message(reason.value))

    /** An in-memory store has no file to hand to another program. */
    case NotOnDisk(where: Error.NotOnDisk.Where) extends Error(Error.Message(s"$where is not on disk"))

  object Error:
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object NotFound:
      type Where = com.histmersion.util.storage.Blob.Location
      val Where: _root_.com.histmersion.util.storage.Blob.Location.type = _root_.com.histmersion.util.storage.Blob.Location

    object NotAFile:
      type Where = com.histmersion.util.storage.Blob.Location
      val Where: _root_.com.histmersion.util.storage.Blob.Location.type = _root_.com.histmersion.util.storage.Blob.Location

    object Failed:
      type Reason = com.histmersion.util.storage.Store.Error.Reason
      val Reason: _root_.com.histmersion.util.storage.Store.Error.Reason.type = _root_.com.histmersion.util.storage.Store.Error.Reason

    object NotOnDisk:
      type Where = com.histmersion.util.storage.Blob.Location
      val Where: _root_.com.histmersion.util.storage.Blob.Location.type = _root_.com.histmersion.util.storage.Blob.Location

    type Message = com.histmersion.util.storage.Store.Problem
    val Message: _root_.com.histmersion.util.storage.Store.Problem.type = _root_.com.histmersion.util.storage.Store.Problem

  /** A name inside a store: `/`-separated segments, relative, never `.` or `..`, no empty segment, no backslash or NUL.
    * The empty key is the store itself.
    */
  type StoreKeyRule = Match["|(?!\\.{1,2}(/|$))[^/\\\\\\x00]+(/(?!\\.{1,2}(/|$))[^/\\\\\\x00]+)*"]

  type Key = Key.T

  object Key extends RefinedType[String, StoreKeyRule]:
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Segment = Segment.T
    object Segment extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Root: Key = applyUnsafe("")

    /** A name made of one segment or several, each checked. */
    def of(segments: String*): Either[com.histmersion.util.storage.Store.Key.Problem, Key] = either(segments.filter(_.nonEmpty).mkString("/")).left.map(Problem(_))

    extension (key: Key)
      def isRoot: Boolean = key.value.isEmpty

      /** `key/child`; `child` may itself hold several segments. */
      def /(child: String): Either[Key.Problem, Key] = of(key.value, child)

      /** `key/child` for a child the caller built from checked parts. */
      def child(name: com.histmersion.util.storage.Store.EntryName): Key = (key / name.value).fold(e => throw IllegalArgumentException(e.value), identity)

      def segments: List[com.histmersion.util.storage.Store.Key.Segment] = if key.isRoot then Nil else key.value.split('/').toList.map(Segment(_))

      /** The last segment. */
      def name: com.histmersion.util.storage.Store.EntryName = com.histmersion.util.storage.Store.EntryName(segments.lastOption.fold("")(_.value))

      def parent: Key = applyUnsafe(segments.dropRight(1).mkString("/"))

      /** The key `name` with `suffix` added to its last segment. */
      def withSuffix(suffix: com.histmersion.util.storage.TempName.Suffixed.Suffix): Key = parent.child(com.histmersion.util.storage.Store.EntryName(name.value + suffix.value))

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
      files: Measure.Files,
      exists: Measure.Exists,
      partial: Measure.Partial = Measure.Partial(false),
      unreadable: Measure.Unreadable = Measure.Unreadable(0)
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

    val absent: Measure = Measure(ByteCount.zero, Files(0L), exists = Exists(false))

  /** The disk a store is (or would be) on: its name, its size and the space this process may still use. */
  final case class Volume(id: Volume.Id, total: ByteCount, usable: ByteCount)

  object Volume:
    type Number = Number.T
    object Number extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
