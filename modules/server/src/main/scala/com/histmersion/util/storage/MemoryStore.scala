package com.histmersion.util.storage

import com.histmersion.values.{ByteCount, ByteOffset, Sha256}

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
    name: String,
    state: AtomicReference[MemoryStore.Files],
    base: StoreKey
) extends Store:
  import MemoryStore.{Entry, Files}

  def this(location: Location, name: String) =
    this(location, name, AtomicReference(MemoryStore.Files(Map.empty, Set.empty)), StoreKey.Root)

  private def full(key: StoreKey): StoreKey =
    if base.isRoot then key else if key.isRoot then base else base.child(key.value)
  private def local(key: StoreKey): StoreKey = key.relativeTo(base)
  private def now: Instant = Instant.EPOCH.plusMillis(MemoryStore.ticks.incrementAndGet())

  private def files: Map[StoreKey, Entry] = state.get.files.filter((k, _) => k.within(base))

  private def directories(within: StoreKey): Set[StoreKey] =
    state.get.files.keySet.flatMap(k => ancestors(k)).filter(_.within(within)) ++
      state.get.directories.filter(_.within(within))

  private def ancestors(k: StoreKey): List[StoreKey] =
    k.segments.inits.toList.drop(1).filter(_.nonEmpty).map(s => StoreKey.applyUnsafe(s.mkString("/")))

  def where(key: StoreKey): String = s"$name/${full(key).value}"

  def sub(prefix: StoreKey): Store = MemoryStore(location, name, state, full(prefix))

  def exists(key: StoreKey): Boolean = isFile(key) || isDirectory(key)
  def isFile(key: StoreKey): Boolean = state.get.files.contains(full(key))
  def isDirectory(key: StoreKey): Boolean = full(key).isRoot || directories(StoreKey.Root).contains(full(key))

  def stat(key: StoreKey): Option[Stored] =
    state.get.files
      .get(full(key))
      .map(e => Stored(key, StoredKind.File, e.size, e.modified))
      .orElse(Option.when(isDirectory(key))(Stored(key, StoredKind.Directory, ByteCount.zero, Instant.EPOCH)))

  def get(key: StoreKey): Option[Blob] =
    state.get.files.get(full(key)).map(e => MemoryStore.MemoryBlob(key, where(key), e))

  def list(prefix: StoreKey): List[Stored] =
    val p = full(prefix)
    val depth = p.segments.size + 1
    val fs = files.toList.collect {
      case (k, e) if k.within(p) && k.segments.size == depth => Stored(local(k), StoredKind.File, e.size, e.modified)
    }
    val ds = directories(p).toList.collect {
      case d if d.segments.size == depth => Stored(local(d), StoredKind.Directory, ByteCount.zero, Instant.EPOCH)
    }
    (fs ++ ds).sortBy(_.key.value)

  def walk(prefix: StoreKey): List[Stored] =
    val p = full(prefix)
    files.toList
      .collect { case (k, e) if k.within(p) => Stored(local(k), StoredKind.File, e.size, e.modified) }
      .sortBy(_.key.value)

  private def store(key: StoreKey, bytes: Array[Byte]): Stored =
    val e = Entry(bytes.clone(), now)
    state.updateAndGet(_.modify(_.files).using(_.updated(full(key), e)))
    Stored(key, StoredKind.File, e.size, e.modified)

  def put(key: StoreKey, bytes: Array[Byte]): Either[StoreError, Stored] = Right(store(key, bytes))
  def write(key: StoreKey, bytes: Array[Byte]): Either[StoreError, Stored] = Right(store(key, bytes))

  def reserve(key: StoreKey, temp: TempName, throughLink: Boolean): Either[StoreError, Reservation] =
    Right(MemoryStore.MemoryReservation(key, where(key), (k, b) => store(k, b)))

  def move(from: StoreKey, to: StoreKey): Either[StoreError, Unit] =
    state.get.files.get(full(from)).toRight(StoreError.NotFound(where(from))).map { e =>
      state.updateAndGet(_.modify(_.files).using(_.removed(full(from)).updated(full(to), e))): Unit
    }

  def copy(from: StoreKey, to: StoreKey, keepAttributes: Boolean): Either[StoreError, Unit] =
    state.get.files.get(full(from)).toRight(StoreError.NotFound(where(from))).map { e =>
      val copied = if keepAttributes then e else e.modify(_.modified).setTo(now)
      state.updateAndGet(_.modify(_.files).using(_.updated(full(to), copied))): Unit
    }

  def makeDirectory(prefix: StoreKey): Either[StoreError, Unit] =
    Right(state.updateAndGet(_.modify(_.directories).using(_ + full(prefix))): Unit)

  def delete(key: StoreKey): Boolean =
    val k = full(key)
    state.getAndUpdate(_.modify(_.files).using(_.removed(k))).files.contains(k)

  def deleteTree(prefix: StoreKey): Option[ByteCount] =
    val p = full(prefix)
    Option.when(exists(prefix)) {
      val before =
        state.getAndUpdate(f => Files(f.files.filterNot((k, _) => k.within(p)), f.directories.filterNot(_.within(p))))
      ByteCount.applyUnsafe(before.files.collect { case (k, e) if k.within(p) => e.size.value }.sum)
    }

  def touch(key: StoreKey, at: Instant): Unit =
    val _ = state.updateAndGet(f =>
      f.files.get(full(key)).fold(f)(e => f.modify(_.files).using(_.updated(full(key), e.modify(_.modified).setTo(at))))
    )

  def size: ByteCount = ByteCount.applyUnsafe(files.valuesIterator.map(_.size.value).sum)

  def usableSpace: ByteCount = ByteCount.applyUnsafe(Long.MaxValue)

  def prune(rule: PruneRule): PruneReport = PruneReport(DiskStore.pruned(walk(), rule).filter(s => delete(s.key)))

  private[util] def onDisk(key: StoreKey): Either[StoreError, java.nio.file.Path] = Left(
    StoreError.NotOnDisk(where(key))
  )

object MemoryStore:
  /** The modification times of an in-memory store: one later than the last for each write. */
  private val ticks = java.util.concurrent.atomic.AtomicLong()

  final case class Entry(bytes: Array[Byte], modified: Instant):
    def size: ByteCount = ByteCount.applyUnsafe(bytes.length.toLong)

  final case class Files(files: Map[StoreKey, Entry], directories: Set[StoreKey])

  final class MemoryBlob(val key: StoreKey, val where: String, entry: Entry) extends Blob:
    def size: ByteCount = entry.size
    def modified: Instant = entry.modified
    def open(): InputStream = ByteArrayInputStream(entry.bytes)
    def bytes(): Array[Byte] = entry.bytes.clone()
    def text(): String = String(entry.bytes, UTF_8)
    def sha256(): Sha256 =
      Sha256.applyUnsafe(MessageDigest.getInstance("SHA-256").digest(entry.bytes).map(b => f"${b & 0xff}%02x").mkString)
    def readAt(offset: ByteOffset, length: Int): Array[Byte] = reader().read(offset, length)
    def mapped(): ByteBuffer = ByteBuffer.wrap(entry.bytes).asReadOnlyBuffer()
    def reader(): RandomReader = new RandomReader:
      def where: String = MemoryBlob.this.where
      def size: ByteCount = entry.size
      def read(offset: ByteOffset, length: Int): Array[Byte] =
        if offset.value + length > entry.bytes.length then
          throw java.io.EOFException(s"$where ends before byte ${offset.value + length}")
        java.util.Arrays.copyOfRange(entry.bytes, offset.value.toInt, offset.value.toInt + length)
      def readUpTo(offset: ByteOffset, length: Int): Array[Byte] =
        val from = math.min(offset.value, entry.bytes.length.toLong).toInt
        java.util.Arrays.copyOfRange(entry.bytes, from, math.min(entry.bytes.length, from + length))
      def close(): Unit = ()

  final class MemoryReservation(val key: StoreKey, val where: String, commitTo: (StoreKey, Array[Byte]) => Stored)
      extends Reservation:
    private val written = AtomicReference(Array.emptyByteArray)
    @volatile private var done = false

    def output(): OutputStream = new ByteArrayOutputStream:
      override def close(): Unit =
        super.close()
        written.set(toByteArray)

    def writeAt(offset: ByteOffset, bytes: Array[Byte]): Unit =
      val _ = written.updateAndGet { now =>
        val out = java.util.Arrays.copyOf(now, math.max(now.length, offset.value.toInt + bytes.length))
        System.arraycopy(bytes, 0, out, offset.value.toInt, bytes.length)
        out
      }

    def size: ByteCount = ByteCount.applyUnsafe(written.get.length.toLong)

    def commit(keepPermissions: Boolean): Either[StoreError, Stored] =
      done = true
      Right(commitTo(key, written.get))

    def discard(): Unit = written.set(Array.emptyByteArray)
    def close(): Unit = if !done then discard()
    private[util] def onDisk: Either[StoreError, java.nio.file.Path] = Left(StoreError.NotOnDisk(where))
