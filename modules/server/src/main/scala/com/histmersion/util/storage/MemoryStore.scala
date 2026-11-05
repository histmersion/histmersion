package com.histmersion.util.storage

import com.histmersion.{ByteCount, ByteOffset, Sha256}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import hearth.kindlings.optics.syntax.*

/** A store in memory, for tests of the code that is given one: the same keys, the same answers, no disk. Its files
  * cannot be handed to another program.
  */
final class MemoryStore private (
    val location: Location,
    name: MemoryStore.Name,
    state: AtomicReference[MemoryStore.Files],
    base: Store.Key
) extends Store:
  import MemoryStore.{Entry, Files}

  def this(location: Location, name: String) =
    this(location, name, AtomicReference(MemoryStore.Files(Map.empty, Set.empty)), Store.Key.Root)

  private def full(key: Store.Key): Store.Key =
    if base.isRoot then key else if key.isRoot then base else base.child(key.value)
  private def local(key: Store.Key): Store.Key = key.relativeTo(base)
  private def now: Instant = Instant.EPOCH.plusMillis(MemoryStore.ticks.incrementAndGet())

  private def files: Map[Store.Key, Entry] = state.get.files.filter((k, _) => k.within(base))

  private def directories(within: Store.Key): Set[Store.Key] =
    state.get.files.keySet.flatMap(k => ancestors(k)).filter(_.within(within)) ++
      state.get.directories.filter(_.within(within))

  private def ancestors(k: Store.Key): List[Store.Key] =
    k.segments.inits.toList.drop(1).filter(_.nonEmpty).map(s => Store.Key.applyUnsafe(s.mkString("/")))

  def where(key: Store.Key): com.histmersion.util.storage.MemoryStore.WhereResult = s"$name/${full(key).value}"

  def sub(prefix: Store.Key): Store = MemoryStore(location, name, state, full(prefix))

  def exists(key: Store.Key): com.histmersion.util.storage.MemoryStore.ExistsResult = isFile(key) || isDirectory(key)
  def isFile(key: Store.Key): com.histmersion.util.storage.MemoryStore.IsFileResult = state.get.files.contains(full(key))
  def isDirectory(key: Store.Key): com.histmersion.util.storage.MemoryStore.IsDirectoryResult = full(key).isRoot || directories(Store.Key.Root).contains(full(key))
  def isLink(key: Store.Key): com.histmersion.util.storage.MemoryStore.IsLinkResult = false
  def isExecutable(key: Store.Key): com.histmersion.util.storage.MemoryStore.IsExecutableResult = false

  def stat(key: Store.Key): Option[Stored] =
    state.get.files
      .get(full(key))
      .map(e => Stored(key, Stored.Kind.File, e.size, e.modified))
      .orElse(Option.when(isDirectory(key))(Stored(key, Stored.Kind.Directory, ByteCount.zero, Instant.EPOCH)))

  def get(key: Store.Key): Option[Blob] =
    state.get.files.get(full(key)).map(e => MemoryStore.MemoryBlob(key, where(key), e))

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

  def walk(prefix: Store.Key): List[Stored] =
    val p = full(prefix)
    files.toList
      .collect { case (k, e) if k.within(p) => Stored(local(k), Stored.Kind.File, e.size, e.modified) }
      .sortBy(_.key.value)

  private def store(key: Store.Key, bytes: com.histmersion.util.storage.MemoryStore.StoreBytes): Stored =
    val e = Entry(bytes.clone(), now)
    state.updateAndGet(_.modify(_.files).using(_.updated(full(key), e)))
    Stored(key, Stored.Kind.File, e.size, e.modified)

  def put(key: Store.Key, bytes: com.histmersion.util.storage.MemoryStore.PutBytes): Either[Store.Error, Stored] = Right(store(key, bytes))
  def write(key: Store.Key, bytes: com.histmersion.util.storage.MemoryStore.WriteBytes): Either[Store.Error, Stored] = Right(store(key, bytes))

  def reserve(key: Store.Key, temp: TempName, throughLink: com.histmersion.util.storage.MemoryStore.ReserveThroughLink): Either[Store.Error, Reservation] =
    Right(MemoryStore.MemoryReservation(key, where(key), (k, b) => store(k, b)))

  def move(from: Store.Key, to: Store.Key): Either[Store.Error, Unit] =
    state.get.files.get(full(from)).toRight(Store.Error.NotFound(where(from))).map { e =>
      state.updateAndGet(_.modify(_.files).using(_.removed(full(from)).updated(full(to), e))): Unit
    }

  def copy(from: Store.Key, to: Store.Key, keepAttributes: com.histmersion.util.storage.MemoryStore.CopyKeepAttributes): Either[Store.Error, Unit] =
    state.get.files.get(full(from)).toRight(Store.Error.NotFound(where(from))).map { e =>
      val copied = if keepAttributes then e else e.modify(_.modified).setTo(now)
      state.updateAndGet(_.modify(_.files).using(_.updated(full(to), copied))): Unit
    }

  def makeDirectory(prefix: Store.Key): Either[Store.Error, Unit] =
    Right(state.updateAndGet(_.modify(_.directories).using(_ + full(prefix))): Unit)

  def delete(key: Store.Key): com.histmersion.util.storage.MemoryStore.DeleteResult =
    val k = full(key)
    state.getAndUpdate(_.modify(_.files).using(_.removed(k))).files.contains(k)

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

  def size: ByteCount = ByteCount.applyUnsafe(files.valuesIterator.map(_.size.value).sum)

  def usableSpace: ByteCount = ByteCount.applyUnsafe(Long.MaxValue)

  def measure(budget: MeasureBudget): Store.Measure =
    if base.isRoot || exists(Store.Key.Root) then
      val held = files.values.toList
      val counted = held.takeWhile(_ => budget.take())
      Store.Measure(
        ByteCount.applyUnsafe(counted.iterator.map(_.size.value).sum),
        counted.size.toLong,
        exists = true,
        partial = counted.size < held.size
      )
    else Store.Measure.absent

  def volume: Option[Store.Volume] = None

  def names(prefix: Store.Key): List[com.histmersion.util.storage.MemoryStore.NamesResult] = list(prefix).map(_.key.name)

  def prune(rule: PruneRule): PruneReport = PruneReport(DiskStore.pruned(walk(), rule).filter(s => delete(s.key)))

  private[util] def onDisk(key: Store.Key): Either[Store.Error, java.nio.file.Path] = Left(
    Store.Error.NotOnDisk(where(key))
  )

object MemoryStore:
  /** The modification times of an in-memory store: one later than the last for each write. */
  type StoreBytes = StoreBytes.T
  object StoreBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutBytes = PutBytes.T
  object PutBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteBytes = WriteBytes.T
  object WriteBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  private val ticks = java.util.concurrent.atomic.AtomicLong()

  final case class Entry(bytes: Entry.Bytes, modified: Instant):
    def size: ByteCount = ByteCount.applyUnsafe(bytes.length.toLong)

  object Entry:
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Files(files: Map[Store.Key, Entry], directories: Set[Store.Key])

  final class MemoryBlob(val key: Store.Key, val where: MemoryBlob.Where, entry: Entry) extends Blob:
    def size: ByteCount = entry.size
    def modified: Instant = entry.modified
    def open(): InputStream = ByteArrayInputStream(entry.bytes)
    def bytes(): com.histmersion.util.storage.MemoryStore.MemoryBlob.BytesResult = entry.bytes.clone()
    def text(): com.histmersion.util.storage.MemoryStore.MemoryBlob.TextResult = String(entry.bytes, UTF_8)
    def sha256(): Sha256 =
      Sha256.applyUnsafe(MessageDigest.getInstance("SHA-256").digest(entry.bytes).map(b => f"${b & 0xff}%02x").mkString)
    def readAt(offset: ByteOffset, length: com.histmersion.util.storage.MemoryStore.MemoryBlob.ReadAtLength): com.histmersion.util.storage.MemoryStore.MemoryBlob.ReadAtResult = reader().read(offset, length)
    def mapped(): ByteBuffer = ByteBuffer.wrap(entry.bytes).asReadOnlyBuffer()
    def reader(): RandomReader = new RandomReader:
      def where: com.histmersion.util.storage.MemoryStore.MemoryBlob.ReaderWhereResult = MemoryBlob.this.where
      def size: ByteCount = entry.size
      def read(offset: ByteOffset, length: com.histmersion.util.storage.MemoryStore.MemoryBlob.ReaderReadLength): com.histmersion.util.storage.MemoryStore.MemoryBlob.ReaderReadResult =
        if offset.value + length > entry.bytes.length then
          throw java.io.EOFException(s"$where ends before byte ${offset.value + length}")
        java.util.Arrays.copyOfRange(entry.bytes, offset.value.toInt, offset.value.toInt + length)
      def readUpTo(offset: ByteOffset, length: com.histmersion.util.storage.MemoryStore.MemoryBlob.ReaderReadUpToLength): com.histmersion.util.storage.MemoryStore.MemoryBlob.ReaderReadUpToResult =
        val from = math.min(offset.value, entry.bytes.length.toLong).toInt
        java.util.Arrays.copyOfRange(entry.bytes, from, math.min(entry.bytes.length, from + length))
      def close(): Unit = ()

  object MemoryBlob:
    type ReaderWhereResult = ReaderWhereResult.T
    object ReaderWhereResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReaderReadResult = ReaderReadResult.T
    object ReaderReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReaderReadLength = ReaderReadLength.T
    object ReaderReadLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReaderReadUpToResult = ReaderReadUpToResult.T
    object ReaderReadUpToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReaderReadUpToLength = ReaderReadUpToLength.T
    object ReaderReadUpToLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type BytesResult = BytesResult.T
    object BytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReadAtResult = ReadAtResult.T
    object ReadAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Where = Where.T
    object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type TextResult = TextResult.T
    object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReadAtLength = ReadAtLength.T
    object ReadAtLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final class MemoryReservation(val key: Store.Key, val where: MemoryReservation.Where, commitTo: (Store.Key, Array[Byte]) => Stored)
      extends Reservation:
    private val written = AtomicReference(Array.emptyByteArray)
    @volatile private var done = false

    def output(): OutputStream = new ByteArrayOutputStream:
      override def close(): Unit =
        super.close()
        written.set(toByteArray)

    def writeAt(offset: ByteOffset, bytes: com.histmersion.util.storage.MemoryStore.MemoryReservation.WriteAtBytes): Unit =
      val _ = written.updateAndGet { now =>
        val out = java.util.Arrays.copyOf(now, math.max(now.length, offset.value.toInt + bytes.length))
        System.arraycopy(bytes, 0, out, offset.value.toInt, bytes.length)
        out
      }

    def size: ByteCount = ByteCount.applyUnsafe(written.get.length.toLong)

    def commit(keepPermissions: com.histmersion.util.storage.MemoryStore.MemoryReservation.CommitKeepPermissions): Either[Store.Error, Stored] =
      done = true
      Right(commitTo(key, written.get))

    def discard(): Unit = written.set(Array.emptyByteArray)
    def close(): Unit = if !done then discard()
    private[util] def onDisk: Either[Store.Error, java.nio.file.Path] = Left(Store.Error.NotOnDisk(where))

  object MemoryReservation:
    type WriteAtBytes = WriteAtBytes.T
    object WriteAtBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Where = Where.T
    object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type CommitKeepPermissions = CommitKeepPermissions.T
    object CommitKeepPermissions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
