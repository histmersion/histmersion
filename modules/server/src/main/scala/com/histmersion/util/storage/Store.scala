package com.histmersion
package util.storage
import com.histmersion.util.problem.{External, Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.ByteCount

import java.time.Instant
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import java.io.InputStream
import java.security.{DigestOutputStream, MessageDigest}
import io.github.iltotore.iron.constraint.any.Pure

/** One named place's files: what a supporting area or a domain is given instead of a path. Keys are relative and
  * checked ([[StoreKey]]); nothing outside the store can be named. A write either lands whole or not at all ([[put]],
  * [[reserve]]) unless it says otherwise ([[write]]). Messages name the file where an operation failed.
  */
trait Store:
  def location: Location

  /** Where `key` is, for messages and reports: the path on disk. */
  def where(key: Store.Key = Store.Key.Root): Blob.Location

  /** The store below `prefix`, its keys relative to it. */
  def sub(prefix: Store.Key): Store

  def exists(key: Store.Key): Boolean
  def isFile(key: Store.Key): Boolean
  def isDirectory(key: Store.Key): Boolean

  /** Is `key` a symbolic link (whatever it points to)? */
  def isLink(key: Store.Key): Boolean

  /** Is `key` a file this process may run? */
  def isExecutable(key: Store.Key): Boolean

  /** `key` and what it is now, when it exists. */
  def stat(key: Store.Key): Option[Stored]

  /** The file at `key`, when there is one. */
  def get(key: Store.Key): Option[Blob]

  /** What `prefix` holds directly, files and directories, by name. */
  def list(prefix: Store.Key = Store.Key.Root): List[Stored]

  /** Every file below `prefix`, at any depth, by key. */
  def walk(prefix: Store.Key = Store.Key.Root): List[Stored]

  /** `bytes` as the file `key`: written beside it, then moved over it in one step. */
  def put(
      key: Store.Key,
      bytes: ByteSource.Content
  ): Either[Store.Error, Stored]

  /** `bytes` written straight into `key`, created or truncated (a file whose readers check its size). */
  def write(
      key: Store.Key,
      bytes: ByteSource.Content
  ): Either[Store.Error, Stored]

  /** A file to write beside `key`; with `throughLink`, beside the file a symbolic link at `key` points to. */
  def reserve(
      key: Store.Key,
      temp: TempName = TempName.Fixed,
      throughLink: Store.ThroughLink = Store.ThroughLink(false)
  ): Either[Store.Error, Reservation]

  /** `from` moved to `to`, replacing it. */
  def move(
      from: Store.Key,
      to: Store.Key
  ): Either[Store.Error, Unit]

  /** `from` copied to `to`, replacing it; with `keepAttributes`, its times and permissions too. */
  def copy(
      from: Store.Key,
      to: Store.Key,
      keepAttributes: Store.KeepPermissions = Store.KeepPermissions(false)
  ): Either[Store.Error, Unit]

  /** `prefix` made a directory, with its parents. */
  def makeDirectory(
      prefix: Store.Key = Store.Key.Root
  ): Either[Store.Error, Unit]

  /** The file `key` removed; `false` when there was none. */
  def delete(key: Store.Key): Boolean

  /** `prefix` and everything below it removed; the bytes its files held, `None` when it was not there. */
  def deleteTree(prefix: Store.Key): Option[ByteCount]

  /** The file's modification time set to `at` (a use, for a cache that drops the least recently used). */
  def touch(key: Store.Key, at: Instant): Unit

  /** The bytes every file of the store holds. */
  def size: ByteCount

  /** The space left on the disk the store is on. */
  def usableSpace: ByteCount

  /** What the store holds, counted by a walk `budget` bounds (shared by every walk of one measurement). The store's own
    * directory may be a symbolic link (a directory moved to another disk) and is followed; links below it are neither
    * followed nor counted, so a link to a big directory elsewhere is not this store's size and a loop cannot hang the
    * walk. An entry that cannot be read is skipped and counted. A store whose root is a file counts that file.
    */
  def measure(budget: MeasureBudget): Store.Measure

  /** The disk the store is (or, when its directory is not made yet, would be) on. */
  def volume: Option[Store.Volume]

  /** The names `prefix` holds directly, by name, without reading what they are; none when it is no directory. */
  def names(prefix: Store.Key = Store.Key.Root): List[Store.EntryName]

  /** The files `rule` names, removed. */
  def prune(rule: PruneRule): PruneReport

  /** The file `key` itself, for a program the supporting areas hand it to. */
  private[util] def onDisk(
      key: Store.Key
  ): Either[Store.Error, java.nio.file.Path]

  /** The same, as the process launcher takes it. */
  final private[util] def onDiskFile(
      key: Store.Key
  ): Either[Store.Error, HandedFile] = onDisk(key).map(_.toFile)

object Store:
  type EntryName = EntryName.T
  object EntryName extends Newtype[String, Pure]
  type KeepPermissions = KeepPermissions.T
  object KeepPermissions extends Newtype[Boolean, Pure]
  type ThroughLink = ThroughLink.T
  object ThroughLink extends Newtype[Boolean, Pure]

  /** A [[ContentStore]] over a store: `<sha256>.<ext>` at its top. */

  final class Contents(store: Store) extends ContentStore:

    def find(sha256: Sha256): Option[(ByteSource, ContentStore.Ext)] =
      store
        .list()
        .find(s => s.isFile && s.key.name.value.startsWith(sha256.value + "."))
        .flatMap(s => store.get(s.key))
        .map(b => b.asSource -> ContentStore.Ext(b.key.name.value.drop(sha256.value.length + 1)))

    /** Written beside its name, hashed, and moved into place only when the content IS `sha256`: the store is
      * content-addressed, and whoever reads it trusts the name.
      */
    def store(sha256: Sha256, ext: ContentStore.Ext, in: InputStream): Either[Problem, Unit] =
      if find(sha256).isDefined then
        val _ = in.transferTo(java.io.OutputStream.nullOutputStream())
        Right(())
      else
        scala.util
          .Try {
            Key.of(s"$sha256.${ContentStore.safeExt(ext)}").flatMap { key =>
              store.reserve(key, TempName.Unique).left.map(e => e.message).flatMap { r =>
                try
                  val md = MessageDigest.getInstance("SHA-256")
                  val out = DigestOutputStream(r.output(), md)
                  try { val _ = in.transferTo(out) }
                  finally out.close()
                  val actual = ContentStore.hex(ContentStore.Digest(md.digest()))
                  if actual != sha256 then Left(Problem.Unclassified(Legacy.Text(s"its content hashes to $actual")))
                  else r.commit().left.map(e => e.message).map(_ => ())
                finally r.close()
              }
            }
          }
          .toEither
          .left
          .map(e =>
            Problem.Io.File(Problem.Io.Access.Write, Problem.Io.Path.of(store.where()), External.Reported.of(e))
          )
          .flatten

  /** Why a store operation did not happen. `message` is what callers show; it names the path where the operation
    * failed, as the file system said.
    */
  enum Error(val message: Problem):
    case NotFound(where: Error.NotFound.Where)
        extends Error(Problem.Io.Wrong(Problem.Io.Path.of(where), Problem.Io.Kind.Present))
    case NotAFile(where: Error.NotAFile.Where)
        extends Error(Problem.Io.Wrong(Problem.Io.Path.of(where), Problem.Io.Kind.File))
    case Failed(reason: Problem) extends Error(reason)

    /** An in-memory store has no file to hand to another program. */
    case NotOnDisk(where: Error.NotOnDisk.Where)
        extends Error(Problem.Io.Wrong(Problem.Io.Path.of(where), Problem.Io.Kind.OnDisk))

  object Error:
    object NotFound:
      type Where = Blob.Location
      val Where: Blob.Location.type = Blob.Location

    object NotAFile:
      type Where = Blob.Location
      val Where: Blob.Location.type = Blob.Location

    object NotOnDisk:
      type Where = Blob.Location
      val Where: Blob.Location.type = Blob.Location

  /** A name inside a store: `/`-separated segments, relative, never `.` or `..`, no empty segment, no backslash or NUL.
    * The empty key is the store itself.
    */
  type StoreKeyRule = Match["|(?!\\.{1,2}(/|$))[^/\\\\\\x00]+(/(?!\\.{1,2}(/|$))[^/\\\\\\x00]+)*"]

  type Key = Key.T

  object Key extends Newtype[String, StoreKeyRule]:
    type Segment = Segment.T
    object Segment extends Newtype[String, Pure]

    val Root: Key = applyUnsafe("")

    /** A name made of one segment or several, each checked. */
    def of(segments: String*): Either[Problem, Key] =
      checked(segments.filter(_.nonEmpty).mkString("/")).left.map(Problem.Invalid.malformed)

    extension (key: Key)
      def isRoot: Boolean = key.value.isEmpty

      /** `key/child`; `child` may itself hold several segments. */
      def /(child: String): Either[Problem, Key] = of(key.value, child)

      /** `key/child` for a child the caller built from checked parts. */
      def child(name: EntryName): Key =
        (key / name.value).fold(e => throw IllegalArgumentException(e.sentence), identity)

      def segments: List[Segment] = if key.isRoot then Nil else key.value.split('/').toList.map(Segment(_))

      /** The last segment. */
      def name: EntryName = EntryName(segments.lastOption.fold("")(_.value))

      def parent: Key = applyUnsafe(segments.dropRight(1).mkString("/"))

      /** The key `name` with `suffix` added to its last segment. */
      def withSuffix(suffix: TempName.Suffixed.Suffix): Key = parent.child(EntryName(name.value + suffix.value))

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
    object Files extends Newtype[Long, Pure]
    type Exists = Exists.T
    object Exists extends Newtype[Boolean, Pure]
    type Partial = Partial.T
    object Partial extends Newtype[Boolean, Pure]
    type Unreadable = Unreadable.T
    object Unreadable extends Newtype[Int, Pure]

    val absent: Measure = Measure(ByteCount.zero, Files(0L), exists = Exists(false))

  /** The disk a store is (or would be) on: its name, its size and the space this process may still use. */
  final case class Volume(id: Volume.Id, total: ByteCount, usable: ByteCount)

  object Volume:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
