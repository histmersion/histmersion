package com.histmersion.util.storage

import com.histmersion.values.{ByteCount, ByteOffset, Sha256}

import java.io.{IOException, InputStream, OutputStream}
import java.nio.ByteBuffer
import java.nio.channels.{ClosedChannelException, FileChannel}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.attribute.{FileTime, PosixFileAttributeView}
import java.nio.file.{
  AtomicMoveNotSupportedException,
  Files,
  LinkOption,
  NoSuchFileException,
  Path,
  StandardCopyOption,
  StandardOpenOption
}
import java.security.MessageDigest
import java.time.Instant
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** A store over a directory on disk: a key is a path below `root`. */
private[storage] final class DiskStore(val location: Location, root: Path) extends Store:

  private def path(key: StoreKey): Path = if key.isRoot then root else root.resolve(key.value)

  private def keyOf(p: Path): StoreKey = StoreKey.applyUnsafe(root.relativize(p).toString.replace('\\', '/'))

  private def stored(p: Path): Stored =
    val dir = Files.isDirectory(p)
    Stored(
      keyOf(p),
      if dir then StoredKind.Directory else StoredKind.File,
      ByteCount.applyUnsafe(if dir then 0L else Files.size(p)),
      Files.getLastModifiedTime(p).toInstant
    )

  private def io[A](body: => A): Either[StoreError, A] =
    try Right(body)
    catch
      case e: NoSuchFileException => Left(StoreError.NotFound(e.getMessage))
      case e: IOException         => Left(StoreError.Failed(e.getMessage))

  def where(key: StoreKey): String = path(key).toString

  def sub(prefix: StoreKey): Store = DiskStore(location, path(prefix))

  def exists(key: StoreKey): Boolean = Files.exists(path(key))
  def isFile(key: StoreKey): Boolean = Files.isRegularFile(path(key))
  def isDirectory(key: StoreKey): Boolean = Files.isDirectory(path(key))

  def stat(key: StoreKey): Option[Stored] =
    val p = path(key)
    Option.when(Files.exists(p))(stored(p))

  def get(key: StoreKey): Option[Blob] =
    val p = path(key)
    Option.when(Files.isRegularFile(p))(DiskStore.FileBlob(key, p))

  def list(prefix: StoreKey): List[Stored] =
    val dir = path(prefix)
    if !Files.isDirectory(dir) then Nil
    else Using.resource(Files.list(dir))(_.iterator.asScala.toList).map(stored).sortBy(_.key.value)

  def walk(prefix: StoreKey): List[Stored] =
    val dir = path(prefix)
    if !Files.isDirectory(dir) then Nil
    else
      Using
        .resource(Files.walk(dir))(_.iterator.asScala.filter(Files.isRegularFile(_)).toList)
        .map(stored)
        .sortBy(_.key.value)

  def put(key: StoreKey, bytes: Array[Byte]): Either[StoreError, Stored] =
    reserve(key).flatMap { r =>
      try
        Using.resource(r.output())(_.write(bytes))
        r.commit()
      finally r.close()
    }

  def write(key: StoreKey, bytes: Array[Byte]): Either[StoreError, Stored] = io {
    val p = path(key)
    Option(p.getParent).foreach(Files.createDirectories(_))
    Files.write(p, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    stored(p)
  }

  def reserve(key: StoreKey, temp: TempName, throughLink: Boolean): Either[StoreError, Reservation] = io {
    val asked = path(key)
    val target = if throughLink && Files.isSymbolicLink(asked) then asked.toRealPath() else asked
    val dir = target.toAbsolutePath.getParent
    Files.createDirectories(dir)
    val file = temp match
      case TempName.Fixed  => target.resolveSibling(target.getFileName.toString + ".tmp")
      case TempName.Unique => Files.createTempFile(dir, s".${asked.getFileName}.", ".tmp")
    if temp == TempName.Fixed then Files.deleteIfExists(file): Unit
    DiskStore.FileReservation(key, file, target, asked)
  }

  def move(from: StoreKey, to: StoreKey): Either[StoreError, Unit] = io {
    val t = path(to)
    Option(t.getParent).foreach(Files.createDirectories(_))
    Files.move(path(from), t, StandardCopyOption.REPLACE_EXISTING): Unit
  }

  def copy(from: StoreKey, to: StoreKey, keepAttributes: Boolean): Either[StoreError, Unit] = io {
    val options =
      if keepAttributes then List(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
      else List(StandardCopyOption.REPLACE_EXISTING)
    Files.copy(path(from), path(to), options*): Unit
  }

  def makeDirectory(prefix: StoreKey): Either[StoreError, Unit] = io(Files.createDirectories(path(prefix)): Unit)

  def delete(key: StoreKey): Boolean = Files.deleteIfExists(path(key))

  def deleteTree(prefix: StoreKey): Option[ByteCount] =
    val p = path(prefix)
    if !Files.exists(p) then None
    else
      var bytes = 0L
      Using.resource(Files.walk(p)) { s =>
        s.sorted(java.util.Comparator.reverseOrder()).forEach { f =>
          if Files.isRegularFile(f) then bytes += Files.size(f)
          val _ = Files.deleteIfExists(f)
        }
      }
      Some(ByteCount.applyUnsafe(bytes))

  def touch(key: StoreKey, at: Instant): Unit =
    val _ = scala.util.Try(Files.setLastModifiedTime(path(key), FileTime.from(at)))

  def size: ByteCount = ByteCount.applyUnsafe(walk().iterator.map(_.size.value).sum)

  def usableSpace: ByteCount =
    @tailrec def existing(p: Path): Option[Path] =
      if p == null then None else if Files.exists(p) then Some(p) else existing(p.getParent)
    ByteCount.applyUnsafe(
      existing(root.toAbsolutePath).flatMap(p => scala.util.Try(Files.getFileStore(p).getUsableSpace).toOption)
        .getOrElse(0L)
    )

  def prune(rule: PruneRule): PruneReport = PruneReport(DiskStore.pruned(walk(), rule).filter(s => delete(s.key)))

  private[util] def onDisk(key: StoreKey): Either[StoreError, Path] = Right(path(key))

private[storage] object DiskStore:

  /** The files `rule` removes, oldest first. */
  def pruned(files: List[Stored], rule: PruneRule): List[Stored] =
    val oldest = files.sortBy(s => (s.modified, s.key.value))
    rule match
      case PruneRule.OlderThan(cutoff)       => oldest.filter(_.modified.isBefore(cutoff))
      case PruneRule.LeastRecentlyUsed(keep) =>
        @tailrec def loop(rest: List[Stored], total: Long, out: List[Stored]): List[Stored] = rest match
          case s :: tail if total > keep.value => loop(tail, total - s.size.value, s :: out)
          case _                               => out.reverse
        loop(oldest, files.iterator.map(_.size.value).sum, Nil)

  final class FileBlob(val key: StoreKey, file: Path) extends Blob:
    def where: String = file.toString
    def size: ByteCount = ByteCount.applyUnsafe(Files.size(file))
    def modified: Instant = Files.getLastModifiedTime(file).toInstant
    def open(): InputStream = Files.newInputStream(file)
    def bytes(): Array[Byte] = Files.readAllBytes(file)
    def text(): String = Files.readString(file, UTF_8)

    def sha256(): Sha256 =
      val md = MessageDigest.getInstance("SHA-256")
      Using.resource(open()) { in =>
        val buf = new Array[Byte](1 << 16)
        @tailrec def loop(): Unit =
          val n = in.read(buf)
          if n > 0 then { md.update(buf, 0, n); loop() }
        loop()
      }
      Sha256.applyUnsafe(md.digest().map(b => f"${b & 0xff}%02x").mkString)

    def readAt(offset: ByteOffset, length: Int): Array[Byte] = Using.resource(reader())(_.read(offset, length))

    def reader(): RandomReader = ChannelReader(file)

    def mapped(): ByteBuffer =
      Using.resource(FileChannel.open(file, StandardOpenOption.READ))(c => c.map(FileChannel.MapMode.READ_ONLY, 0, c.size()))

  /** Positional reads on one channel, opened again when an interrupt closed it. */
  final class ChannelReader(file: Path) extends RandomReader:
    @volatile private var ch: FileChannel = FileChannel.open(file, StandardOpenOption.READ)
    @volatile private var closed = false

    private def reopen(broken: FileChannel): Unit = synchronized {
      if !closed && (ch eq broken) then ch = FileChannel.open(file, StandardOpenOption.READ)
    }

    private def withChannel[A](body: FileChannel => A): A =
      def attempt(retry: Boolean): A =
        val c = ch
        try body(c)
        catch
          case e: ClosedChannelException if !closed =>
            reopen(c)
            if retry && !Thread.currentThread().isInterrupted then attempt(retry = false) else throw e
      attempt(retry = true)

    def where: String = file.toString

    def size: ByteCount = ByteCount.applyUnsafe(withChannel(_.size()))

    def read(offset: ByteOffset, length: Int): Array[Byte] = withChannel { c =>
      val buf = ByteBuffer.allocate(length)
      @tailrec def loop(at: Long): Unit =
        if buf.hasRemaining then
          val n = c.read(buf, at)
          if n < 0 then throw java.io.EOFException(s"$file ends before byte ${offset.value + length}")
          loop(at + n)
      loop(offset.value)
      buf.array()
    }

    def readUpTo(offset: ByteOffset, length: Int): Array[Byte] = withChannel { c =>
      val buf = ByteBuffer.allocate(length)
      @tailrec def loop(at: Long): Unit =
        if buf.hasRemaining then
          val n = c.read(buf, at)
          if n > 0 then loop(at + n)
      loop(offset.value)
      java.util.Arrays.copyOf(buf.array(), buf.position())
    }

    def close(): Unit = synchronized { closed = true; ch.close() }

  final class FileReservation(
      val key: StoreKey,
      file: Path,
      target: Path,
      asked: Path
  ) extends Reservation:
    @volatile private var committed = false

    def where: String = file.toString

    def output(): OutputStream = Files.newOutputStream(file)

    def writeAt(offset: ByteOffset, bytes: Array[Byte]): Unit =
      Using.resource(FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) { ch =>
        val buf = ByteBuffer.wrap(bytes)
        @tailrec def loop(at: Long): Unit = if buf.hasRemaining then loop(at + ch.write(buf, at))
        loop(offset.value)
      }

    def size: ByteCount = ByteCount.applyUnsafe(if Files.exists(file) then Files.size(file) else 0L)

    def commit(keepPermissions: Boolean): Either[StoreError, Stored] =
      try
        if keepPermissions && Files.exists(asked) then
          Option(Files.getFileAttributeView(asked, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS))
            .foreach(v => Files.setPosixFilePermissions(file, v.readAttributes.permissions))
        try Files.move(file, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        catch case _: AtomicMoveNotSupportedException => Files.move(file, target, StandardCopyOption.REPLACE_EXISTING)
        committed = true
        Right(
          Stored(
            key,
            StoredKind.File,
            ByteCount.applyUnsafe(Files.size(target)),
            Files.getLastModifiedTime(target).toInstant
          )
        )
      catch case e: IOException => Left(StoreError.Failed(e.getMessage))

    def discard(): Unit =
      val _ = Files.deleteIfExists(file)

    def close(): Unit = if !committed then discard()

    private[util] def onDisk: Either[StoreError, Path] = Right(file)
