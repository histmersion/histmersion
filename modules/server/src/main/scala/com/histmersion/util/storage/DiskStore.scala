package com.histmersion.util.storage

import com.histmersion.{ByteCount, ByteOffset, Sha256}

import java.io.{InputStream, IOException, OutputStream}
import java.nio.ByteBuffer
import java.nio.channels.{ClosedChannelException, FileChannel}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.attribute.{BasicFileAttributes, FileTime, PosixFileAttributeView}
import java.nio.file.{
  AtomicMoveNotSupportedException,
  Files,
  FileVisitOption,
  FileVisitResult,
  LinkOption,
  NoSuchFileException,
  Path,
  SimpleFileVisitor,
  StandardCopyOption,
  StandardOpenOption
}
import java.security.MessageDigest
import java.time.Instant
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** A store over a directory on disk: a key is a path below `root`. */
final private[storage] class DiskStore(val location: Location, root: Path) extends Store:

  private def path(key: Store.Key): Path = if key.isRoot then root else root.resolve(key.value)

  private def keyOf(p: Path): Store.Key = Store.Key.applyUnsafe(root.relativize(p).toString.replace('\\', '/'))

  private def stored(p: Path): Stored =
    val dir = Files.isDirectory(p)
    Stored(
      keyOf(p),
      if dir then Stored.Kind.Directory else Stored.Kind.File,
      ByteCount.applyUnsafe(if dir then 0L else Files.size(p)),
      Files.getLastModifiedTime(p).toInstant
    )

  private def io[A](body: => A): Either[Store.Error, A] =
    try Right(body)
    catch
      case e: NoSuchFileException => Left(Store.Error.NotFound(Blob.Location(e.getMessage)))
      case e: IOException         => Left(Store.Error.Failed(Store.Error.Reason(e.getMessage)))

  def where(key: Store.Key): com.histmersion.util.storage.Blob.Location = Blob.Location(path(key).toString)

  def sub(prefix: Store.Key): Store = DiskStore(location, path(prefix))

  def exists(key: Store.Key): Boolean = Files.exists(path(key))
  def isFile(key: Store.Key): Boolean = Files.isRegularFile(path(key))
  def isDirectory(key: Store.Key): Boolean = Files.isDirectory(path(key))
  def isLink(key: Store.Key): Boolean = Files.isSymbolicLink(path(key))
  def isExecutable(key: Store.Key): Boolean = Files.isRegularFile(path(key)) && Files.isExecutable(path(key))

  def stat(key: Store.Key): Option[Stored] =
    val p = path(key)
    Option.when(Files.exists(p))(stored(p))

  def get(key: Store.Key): Option[Blob] =
    val p = path(key)
    Option.when(Files.isRegularFile(p))(DiskStore.FileBlob(key, p))

  def list(prefix: Store.Key): List[Stored] =
    val dir = path(prefix)
    if !Files.isDirectory(dir) then Nil
    else Using.resource(Files.list(dir))(_.iterator.asScala.toList).map(stored).sortBy(_.key.value)

  def walk(prefix: Store.Key): List[Stored] =
    val dir = path(prefix)
    if !Files.isDirectory(dir) then Nil
    else
      Using
        .resource(Files.walk(dir))(_.iterator.asScala.filter(Files.isRegularFile(_)).toList)
        .map(stored)
        .sortBy(_.key.value)

  def put(key: Store.Key, bytes: com.histmersion.util.storage.ByteSource.Content): Either[Store.Error, Stored] =
    reserve(key).flatMap { r =>
      try
        Using.resource(r.output())(_.write(bytes.value))
        r.commit()
      finally r.close()
    }

  def write(key: Store.Key, bytes: com.histmersion.util.storage.ByteSource.Content): Either[Store.Error, Stored] = io {
    val p = path(key)
    Option(p.getParent).foreach(Files.createDirectories(_))
    Files.write(p, bytes.value, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    stored(p)
  }

  def reserve(key: Store.Key, temp: TempName, throughLink: com.histmersion.util.storage.Store.ThroughLink): Either[Store.Error, Reservation] = io {
    val asked = path(key)
    val target = if throughLink.value && Files.isSymbolicLink(asked) then asked.toRealPath() else asked
    val dir = target.toAbsolutePath.getParent
    Files.createDirectories(dir)
    val file = temp match
      case TempName.Fixed          => target.resolveSibling(target.getFileName.toString + ".tmp")
      case TempName.Unique         => Files.createTempFile(dir, s".${asked.getFileName}.", ".tmp")
      case TempName.Suffixed(more) => target.resolveSibling(target.getFileName.toString + more)
    if temp != TempName.Unique then Files.deleteIfExists(file): Unit
    DiskStore.FileReservation(key, file, target, asked)
  }

  def move(from: Store.Key, to: Store.Key): Either[Store.Error, Unit] = io {
    val t = path(to)
    Option(t.getParent).foreach(Files.createDirectories(_))
    Files.move(path(from), t, StandardCopyOption.REPLACE_EXISTING): Unit
  }

  def copy(from: Store.Key, to: Store.Key, keepAttributes: com.histmersion.util.storage.Store.KeepPermissions): Either[Store.Error, Unit] = io {
    val options =
      if keepAttributes.value then List(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
      else List(StandardCopyOption.REPLACE_EXISTING)
    Files.copy(path(from), path(to), options*): Unit
  }

  def makeDirectory(prefix: Store.Key): Either[Store.Error, Unit] = io(Files.createDirectories(path(prefix)): Unit)

  def delete(key: Store.Key): Boolean = Files.deleteIfExists(path(key))

  def deleteTree(prefix: Store.Key): Option[ByteCount] =
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

  def touch(key: Store.Key, at: Instant): Unit =
    val _ = scala.util.Try(Files.setLastModifiedTime(path(key), FileTime.from(at)))

  def size: ByteCount = ByteCount.applyUnsafe(walk().iterator.map(_.size.value).sum)

  def usableSpace: ByteCount =
    @tailrec def existing(p: Path): Option[Path] =
      if p == null then None else if Files.exists(p) then Some(p) else existing(p.getParent)
    ByteCount.applyUnsafe(
      existing(root.toAbsolutePath)
        .flatMap(p => scala.util.Try(Files.getFileStore(p).getUsableSpace).toOption)
        .getOrElse(0L)
    )

  def measure(budget: MeasureBudget): Store.Measure =
    if !Files.exists(root) then Store.Measure.absent
    else if Files.isRegularFile(root) then
      val _ = budget.take()
      Store.Measure(
        ByteCount.applyUnsafe(scala.util.Try(Files.size(root)).getOrElse(0L)),
        Store.Measure.Files(1L),
        exists = Store.Measure.Exists(true)
      )
    else if budget.exhausted then Store.Measure(
        ByteCount.zero,
        Store.Measure.Files(0L),
        exists = Store.Measure.Exists(true),
        partial = Store.Measure.Partial(true)
      )
    else
      // the walk's running sums: a visitor is callback-shaped, so it accumulates into locals
      var bytes = 0L
      var files = 0L
      var partial = false
      var unreadable = 0
      val start = scala.util.Try(root.toRealPath()).getOrElse(root)
      val visitor = new SimpleFileVisitor[Path]:
        private def step(): FileVisitResult =
          if budget.take() then FileVisitResult.CONTINUE
          else { partial = true; FileVisitResult.TERMINATE }
        override def preVisitDirectory(d: Path, a: BasicFileAttributes): FileVisitResult = step()
        override def visitFile(f: Path, a: BasicFileAttributes): FileVisitResult =
          if a.isRegularFile then { bytes += a.size; files += 1 }
          step()
        override def visitFileFailed(f: Path, e: IOException): FileVisitResult =
          unreadable += 1
          FileVisitResult.CONTINUE
        override def postVisitDirectory(d: Path, e: IOException): FileVisitResult =
          if e != null then unreadable += 1
          FileVisitResult.CONTINUE
      try
        val _ = Files.walkFileTree(start, java.util.EnumSet.noneOf(classOf[FileVisitOption]), Int.MaxValue, visitor)
      catch case _: IOException => unreadable += 1
      Store.Measure(
        ByteCount.applyUnsafe(bytes),
        Store.Measure.Files(files),
        Store.Measure.Exists(true),
        Store.Measure.Partial(partial),
        Store.Measure.Unreadable(unreadable)
      )

  def volume: Option[Store.Volume] =
    @tailrec def existing(p: Path): Option[Path] =
      if p == null then None else if Files.exists(p) then Some(p) else existing(p.getParent)
    existing(root.toAbsolutePath).flatMap { e =>
      scala.util.Try {
        val s = Files.getFileStore(e)
        Store.Volume(Store.Volume.Id(s.toString), ByteCount.applyUnsafe(s.getTotalSpace), ByteCount.applyUnsafe(s.getUsableSpace))
      }.toOption
    }

  def names(prefix: Store.Key): List[com.histmersion.util.storage.Store.EntryName] =
    val dir = path(prefix)
    if !Files.isDirectory(dir) then Nil
    else
      scala.util
        .Try(Using.resource(Files.list(dir))(_.iterator.asScala.toList.map(_.getFileName.toString).sorted))
        .getOrElse(Nil)
        .map(Store.EntryName(_))

  def prune(rule: PruneRule): PruneReport = PruneReport(DiskStore.pruned(walk(), rule).filter(s => delete(s.key)))

  private[util] def onDisk(key: Store.Key): Either[Store.Error, Path] = Right(path(key))

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

  final class FileBlob(val key: Store.Key, file: Path) extends Blob:
    def where: com.histmersion.util.storage.Blob.Location = Blob.Location(file.toString)
    def size: ByteCount = ByteCount.applyUnsafe(Files.size(file))
    def modified: Instant = Files.getLastModifiedTime(file).toInstant
    def open(): InputStream = Files.newInputStream(file)
    def bytes(): com.histmersion.util.storage.ByteSource.Content = ByteSource.Content(Files.readAllBytes(file))
    def text(): com.histmersion.util.storage.Blob.Text = Blob.Text(Files.readString(file, UTF_8))

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

    def readAt(offset: ByteOffset, length: com.histmersion.util.basemap.PmTiles.ByteLength): com.histmersion.util.storage.ByteSource.Content = Using.resource(reader())(_.read(offset, length))

    def reader(): RandomReader = ChannelReader(file)

    def mapped(): ByteBuffer =
      Using.resource(FileChannel.open(file, StandardOpenOption.READ))(c =>
        c.map(FileChannel.MapMode.READ_ONLY, 0, c.size())
      )



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

    def where: com.histmersion.util.storage.Blob.Location = Blob.Location(file.toString)

    def size: ByteCount = ByteCount.applyUnsafe(withChannel(_.size()))

    def read(offset: ByteOffset, length: com.histmersion.util.basemap.PmTiles.ByteLength): com.histmersion.util.storage.ByteSource.Content = withChannel { c =>
      val len = length.value
      val buf = ByteBuffer.allocate(len)
      @tailrec def loop(at: Long): Unit =
        if buf.hasRemaining then
          val n = c.read(buf, at)
          if n < 0 then throw java.io.EOFException(s"$file ends before byte ${offset.value + len}")
          loop(at + n)
      loop(offset.value)
      ByteSource.Content(buf.array())
    }

    def readUpTo(offset: ByteOffset, length: com.histmersion.util.basemap.PmTiles.ByteLength): com.histmersion.util.storage.ByteSource.Content = withChannel { c =>
      val buf = ByteBuffer.allocate(length.value)
      @tailrec def loop(at: Long): Unit =
        if buf.hasRemaining then
          val n = c.read(buf, at)
          if n > 0 then loop(at + n)
      loop(offset.value)
      ByteSource.Content(java.util.Arrays.copyOf(buf.array(), buf.position()))
    }

    def close(): Unit = synchronized { closed = true; ch.close() }

  object ChannelReader:



  final class FileReservation(
      val key: Store.Key,
      file: Path,
      target: Path,
      asked: Path
  ) extends Reservation:
    @volatile private var committed = false

    def where: com.histmersion.util.storage.Blob.Location = Blob.Location(file.toString)

    def output(): OutputStream = Files.newOutputStream(file)

    def writeAt(offset: ByteOffset, bytes: com.histmersion.util.storage.ByteSource.Content): Unit =
      Using.resource(FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) { ch =>
        val buf = ByteBuffer.wrap(bytes.value)
        @tailrec def loop(at: Long): Unit = if buf.hasRemaining then loop(at + ch.write(buf, at))
        loop(offset.value)
      }

    def size: ByteCount = ByteCount.applyUnsafe(if Files.exists(file) then Files.size(file) else 0L)

    def commit(keepPermissions: com.histmersion.util.storage.Store.KeepPermissions): Either[Store.Error, Stored] =
      try
        if keepPermissions.value && Files.exists(asked) then
          Option(Files.getFileAttributeView(asked, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS))
            .foreach(v => Files.setPosixFilePermissions(file, v.readAttributes.permissions))
        try Files.move(file, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        catch case _: AtomicMoveNotSupportedException => Files.move(file, target, StandardCopyOption.REPLACE_EXISTING)
        committed = true
        Right(
          Stored(
            key,
            Stored.Kind.File,
            ByteCount.applyUnsafe(Files.size(target)),
            Files.getLastModifiedTime(target).toInstant
          )
        )
      catch case e: IOException => Left(Store.Error.Failed(Store.Error.Reason(e.getMessage)))

    def discard(): Unit =
      val _ = Files.deleteIfExists(file)

    def close(): Unit = if !committed then discard()

    private[util] def onDisk: Either[Store.Error, Path] = Right(file)



