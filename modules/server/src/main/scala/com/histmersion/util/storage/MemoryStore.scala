package com.histmersion
package util.storage

import com.histmersion.{ByteCount, ByteOffset, Sha256}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.basemap.PmTiles
import com.histmersion.lint.{tolerated, LintRule}

/** A store in memory, for tests of the code that is given one: the same keys, the same answers, no disk. Its files
  * cannot be handed to another program.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final class MemoryStore private (
    val location: Location,
    name: MemoryStore.Name,
    state: AtomicReference[MemoryStore.Files],
    base: Store.Key
) extends Store:
  import MemoryStore.{Entry, Files}

  def this(location: Location, name: String) =
    this(location, MemoryStore.Name(name), AtomicReference(MemoryStore.Files(Map.empty, Set.empty)), Store.Key.Root)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def full(key: Store.Key): Store.Key =
    if base.isRoot then key else if key.isRoot then base else base.child(Store.EntryName(key.value))
  private def local(key: Store.Key): Store.Key = key.relativeTo(base)
  private def now: Instant = Instant.EPOCH.plusMillis(MemoryStore.ticks.incrementAndGet())

  private def files: Map[Store.Key, Entry] = state.get.files.filter((k, _) => k.within(base))

  private def directories(within: Store.Key): Set[Store.Key] =
    state.get.files.keySet.flatMap(k => ancestors(k)).filter(_.within(within)) ++
      state.get.directories.filter(_.within(within))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def ancestors(k: Store.Key): List[Store.Key] =
    k.segments.inits.toList.drop(1).filter(_.nonEmpty).map(s => Store.Key.applyUnsafe(s.mkString("/")))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def where(key: Store.Key): Blob.Location = Blob.Location(s"$name/${full(key).value}")

  def sub(prefix: Store.Key): Store = MemoryStore(location, name, state, full(prefix))

  def exists(key: Store.Key): Boolean = isFile(key) || isDirectory(key)
  def isFile(key: Store.Key): Boolean = state.get.files.contains(full(key))
  def isDirectory(key: Store.Key): Boolean = full(key).isRoot || directories(Store.Key.Root).contains(full(key))
  def isLink(key: Store.Key): Boolean = false
  def isExecutable(key: Store.Key): Boolean = false

  def stat(key: Store.Key): Option[Stored] =
    state.get.files
      .get(full(key))
      .map(e => Stored(key, Stored.Kind.File, e.size, e.modified))
      .orElse(Option.when(isDirectory(key))(Stored(key, Stored.Kind.Directory, ByteCount.zero, Instant.EPOCH)))

  def get(key: Store.Key): Option[Blob] =
    state.get.files.get(full(key)).map(e => MemoryStore.MemoryBlob(key, where(key), e))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def list(prefix: Store.Key): List[Stored] =
    val p = full(prefix)
    val depth = p.segments.size + 1
    val fs = files.toList.collect {
      case (k, e) if k.within(p) && k.segments.size == depth => Stored(local(k), Stored.Kind.File, e.size, e.modified)
    }
    val ds = directories(p).toList.collect {
      case d if d.segments.size == depth => Stored(local(d), Stored.Kind.Directory, ByteCount.zero, Instant.EPOCH)
    }
    (fs ++ ds).sortBy(_.key.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def walk(prefix: Store.Key): List[Stored] =
    val p = full(prefix)
    files.toList
      .collect { case (k, e) if k.within(p) => Stored(local(k), Stored.Kind.File, e.size, e.modified) }
      .sortBy(_.key.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def store(key: Store.Key, bytes: ByteSource.Content): Stored =
    val e = Entry(ByteSource.Content(bytes.value.clone()), now)
    state.updateAndGet(_.modify(_.files).using(_.updated(full(key), e)))
    Stored(key, Stored.Kind.File, e.size, e.modified)

  def put(key: Store.Key, bytes: ByteSource.Content): Either[Store.Error, Stored] = Right(store(key, bytes))
  def write(key: Store.Key, bytes: ByteSource.Content): Either[Store.Error, Stored] = Right(store(key, bytes))

  def reserve(key: Store.Key, temp: TempName, throughLink: Store.ThroughLink): Either[Store.Error, Reservation] =
    Right(MemoryStore.MemoryReservation(key, where(key), (k, b) => store(k, b)))

  def move(from: Store.Key, to: Store.Key): Either[Store.Error, Unit] =
    state.get.files.get(full(from)).toRight(Store.Error.NotFound(where(from))).map { e =>
      state.updateAndGet(_.modify(_.files).using(_.removed(full(from)).updated(full(to), e))): Unit
    }

  def copy(from: Store.Key, to: Store.Key, keepAttributes: Store.KeepPermissions): Either[Store.Error, Unit] =
    state.get.files.get(full(from)).toRight(Store.Error.NotFound(where(from))).map { e =>
      val copied = if keepAttributes.keepPermissions then e else e.modify(_.modified).setTo(now)
      state.updateAndGet(_.modify(_.files).using(_.updated(full(to), copied))): Unit
    }

  def makeDirectory(prefix: Store.Key): Either[Store.Error, Unit] =
    Right(state.updateAndGet(_.modify(_.directories).using(_ + full(prefix))): Unit)

  def delete(key: Store.Key): Boolean =
    val k = full(key)
    state.getAndUpdate(_.modify(_.files).using(_.removed(k))).files.contains(k)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def deleteTree(prefix: Store.Key): Option[ByteCount] =
    val p = full(prefix)
    Option.when(exists(prefix)) {
      val before =
        state.getAndUpdate(f => Files(f.files.filterNot((k, _) => k.within(p)), f.directories.filterNot(_.within(p))))
      ByteCount.applyUnsafe(before.files.collect { case (k, e) if k.within(p) => e.size.value }.sum)
    }

  def touch(key: Store.Key, at: Instant): Unit =
    val _ = state.updateAndGet(f =>
      f.files.get(full(key)).fold(f)(e => f.modify(_.files).using(_.updated(full(key), e.modify(_.modified).setTo(at))))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def size: ByteCount = ByteCount.applyUnsafe(files.valuesIterator.map(_.size.value).sum)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def usableSpace: ByteCount = ByteCount.applyUnsafe(Long.MaxValue)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def measure(budget: MeasureBudget): Store.Measure =
    if base.isRoot || exists(Store.Key.Root) then
      val held = files.values.toList
      val counted = held.takeWhile(_ => budget.take())
      Store.Measure(
        ByteCount.applyUnsafe(counted.iterator.map(_.size.value).sum),
        Store.Measure.Files(counted.size.toLong),
        exists = Store.Measure.Exists.Exists,
        partial = (if counted.size < held.size then Store.Measure.Partial.Partial else Store.Measure.Partial.Complete)
      )
    else Store.Measure.absent

  def volume: Option[Store.Volume] = None

  def names(prefix: Store.Key): List[Store.EntryName] = list(prefix).map(_.key.name)

  def prune(rule: PruneRule): PruneReport = PruneReport(DiskStore.pruned(walk(), rule).filter(s => delete(s.key)))

  private[util] def onDisk(key: Store.Key): Either[Store.Error, java.nio.file.Path] = Left(
    Store.Error.NotOnDisk(where(key))
  )

object MemoryStore:
  /** The modification times of an in-memory store: one later than the last for each write. */

  type Name = Name.T
  object Name extends Newtype[String, Pure]

  private val ticks = java.util.concurrent.atomic.AtomicLong()

  final case class Entry(bytes: ByteSource.Content, modified: Instant):
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def size: ByteCount = ByteCount.applyUnsafe(bytes.value.length.toLong)

  final case class Files(files: Map[Store.Key, Entry], directories: Set[Store.Key])

  final class MemoryBlob(val key: Store.Key, val where: MemoryBlob.Where, entry: Entry) extends Blob:
    def size: ByteCount = entry.size
    def modified: Instant = entry.modified
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def open(): InputStream = ByteArrayInputStream(entry.bytes.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def bytes(): ByteSource.Content = ByteSource.Content(entry.bytes.value.clone())
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def text(): Blob.Text = Blob.Text(String(entry.bytes.value, UTF_8))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def sha256(): Sha256 =
      Sha256.applyUnsafe(
        MessageDigest.getInstance("SHA-256").digest(entry.bytes.value).map(b => f"${b & 0xff}%02x").mkString
      )
    def readAt(offset: ByteOffset, length: PmTiles.ByteLength): ByteSource.Content = reader().read(offset, length)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def mapped(): ByteBuffer = ByteBuffer.wrap(entry.bytes.value).asReadOnlyBuffer()
    def reader(): RandomReader = new RandomReader:
      def where: Blob.Location = MemoryBlob.this.where
      def size: ByteCount = entry.size
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def read(offset: ByteOffset, length: PmTiles.ByteLength): ByteSource.Content =
        val held = entry.bytes.value
        val len = length.value
        if offset.value + len > held.length then
          throw java.io.EOFException(s"$where ends before byte ${offset.value + len}")
        ByteSource.Content(java.util.Arrays.copyOfRange(held, offset.value.toInt, offset.value.toInt + len))
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def readUpTo(offset: ByteOffset, length: PmTiles.ByteLength): ByteSource.Content =
        val held = entry.bytes.value
        val from = math.min(offset.value, held.length.toLong).toInt
        ByteSource.Content(java.util.Arrays.copyOfRange(held, from, math.min(held.length, from + length.value)))
      def close(): Unit = ()

  object MemoryBlob:

    export Blob.Location as Where

  final class MemoryReservation(
      val key: Store.Key,
      val where: MemoryReservation.Where,
      commitTo: (Store.Key, ByteSource.Content) => Stored
  ) extends Reservation:
    private val written = AtomicReference(Array.emptyByteArray)
    @volatile private var done = false

    def output(): OutputStream = new ByteArrayOutputStream:
      override def close(): Unit =
        super.close()
        written.set(toByteArray)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def writeAt(offset: ByteOffset, bytes: ByteSource.Content): Unit =
      val _ = written.updateAndGet { now =>
        val data = bytes.value
        val out = java.util.Arrays.copyOf(now, math.max(now.length, offset.value.toInt + data.length))
        System.arraycopy(data, 0, out, offset.value.toInt, data.length)
        out
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def size: ByteCount = ByteCount.applyUnsafe(written.get.length.toLong)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def commit(keepPermissions: Store.KeepPermissions): Either[Store.Error, Stored] =
      done = true
      Right(commitTo(key, ByteSource.Content(written.get)))

    def discard(): Unit = written.set(Array.emptyByteArray)
    def close(): Unit = if !done then discard()
    private[util] def onDisk: Either[Store.Error, java.nio.file.Path] = Left(Store.Error.NotOnDisk(where))

  object MemoryReservation:

    export Blob.Location as Where
