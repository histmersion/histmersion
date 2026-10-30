package com.histmersion.plugins.vaults

import java.io.{InputStream, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Running a vault's command-line tool: an argument vector, NEVER a shell (a reference cannot inject anything), a time
  * limit after which the process is killed, and the output kept in memory only. Error texts come from the tool's
  * stderr; its stdout — the secret — is never put into one.
  */
object Subprocess:

  final case class Result(exit: Int, out: String, err: String)

  /** Pure: the executable for `tool` — a path as given (`/opt/kp/keepassxc-cli`), or a bare name looked up in `path`
    * (the PATH the server started with).
    */
  def locate(tool: String, path: Option[String]): Either[String, String] =
    val t = Option(tool).getOrElse("").trim
    def runnable(p: Path) = Files.isRegularFile(p) && Files.isExecutable(p)
    if t.isEmpty then Left("no command is configured (cli = \"…\")")
    else if t.contains('/') then
      val p = Path.of(t)
      if runnable(p) then Right(p.toString) else Left(s"$t is not an executable file")
    else
      path.toList
        .flatMap(_.split(java.io.File.pathSeparatorChar).toList)
        .filter(_.nonEmpty)
        .map(d => Path.of(d, t))
        .find(runnable)
        .map(_.toString)
        .toRight(s"$t was not found on the PATH")

  /** Run `argv` with `env` added to the server's environment, `stdin` written and closed (no stdin: closed at once, so
    * a tool that would prompt sees end-of-file instead of waiting), within `timeout`. `Left` = it could not start or
    * did not finish in time.
    */
  def run(
      argv: List[String],
      env: Map[String, String],
      stdin: Option[String],
      timeout: FiniteDuration
  ): Either[String, Result] =
    val tool = argv.headOption.map(a => Path.of(a).getFileName.toString).getOrElse("?")
    val pb = new ProcessBuilder(argv.asJava)
    env.foreach((k, v) => pb.environment().put(k, v))
    Try(pb.start()).toEither.left.map(e => s"$tool could not be started: ${e.getMessage}").flatMap { proc =>
      val out = drain(proc.getInputStream)
      val err = drain(proc.getErrorStream)
      // a tool that exits without reading its input closes the pipe: that is its answer, not ours to report
      try
        val in = proc.getOutputStream
        try stdin.foreach(s => in.write(s.getBytes(UTF_8)))
        finally in.close()
      catch case _: IOException => ()
      val finished = proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
      if !finished then
        proc.descendants().forEach { p =>
          val _ = p.destroyForcibly()
        }
        val _ = proc.destroyForcibly()
        Left(s"$tool did not answer within ${timeout.toSeconds} s")
      else Right(Result(proc.exitValue, out(), err()))
    }

  /** A tool kept running to answer several requests (`keepassxc-cli open`'s interactive mode): lines written to its
    * stdin one at a time, its output read as it comes, in memory only. Owned by the one call that starts it, which
    * [[close]]s it.
    */
  final class Session private (proc: Process, tool: String):
    private val lock = new Object
    // what the tool printed so far; appended by the two reader threads, read under `lock`
    private val outBytes = new java.io.ByteArrayOutputStream()
    private val errBytes = new java.io.ByteArrayOutputStream()
    private val in = proc.getOutputStream

    private def pump(from: InputStream, into: java.io.ByteArrayOutputStream): Unit =
      val buf = new Array[Byte](4096)
      try
        var n = from.read(buf) // `var`: the read loop's count
        while n >= 0 do
          lock.synchronized { into.write(buf, 0, n); lock.notifyAll() }
          n = from.read(buf)
      catch case _: IOException => ()
      finally lock.synchronized(lock.notifyAll())

    private[Subprocess] def startReaders(): Unit =
      val _ = Thread.ofVirtual().start(() => pump(proc.getInputStream, outBytes))
      val _ = Thread.ofVirtual().start(() => pump(proc.getErrorStream, errBytes))

    /** Everything the tool printed to stdout / stderr so far. */
    def out: String = lock.synchronized(outBytes.toString(UTF_8))
    def err: String = lock.synchronized(errBytes.toString(UTF_8))
    def alive: Boolean = proc.isAlive

    /** Write one line to the tool's stdin. */
    def send(line: String): Either[String, Unit] =
      try { in.write((line + "\n").getBytes(UTF_8)); in.flush(); Right(()) }
      catch case e: IOException => Left(s"$tool stopped reading its input")

    /** Wait until `ready` holds for stdout, the tool has exited, or `timeout` passed; true = `ready`. */
    def awaitOut(timeout: FiniteDuration)(ready: String => Boolean): Boolean = await(timeout, () => ready(out))

    /** Wait until stderr has grown past `length` characters, the tool has exited, or `timeout` passed. */
    def awaitErr(length: Int, timeout: FiniteDuration): Unit = { val _ = await(timeout, () => err.length > length) }

    private def await(timeout: FiniteDuration, ready: () => Boolean): Boolean =
      val deadline = System.nanoTime + timeout.toNanos
      lock.synchronized {
        // `var`: the time left, re-read after each wake-up
        var left = deadline - System.nanoTime
        while !ready() && proc.isAlive && left > 0 do
          lock.wait(math.max(1L, math.min(left / 1000000, 50L)))
          left = deadline - System.nanoTime
        ready()
      }

    /** Close the tool's stdin and wait for it to exit within `timeout` (killed otherwise); its exit code. */
    def close(timeout: FiniteDuration): Option[Int] =
      try in.close()
      catch case _: IOException => ()
      if proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS) then Some(proc.exitValue)
      else
        proc.descendants().forEach { p =>
          val _ = p.destroyForcibly()
        }
        val _ = proc.destroyForcibly()
        None

    /** The exit code once the tool has exited within `timeout`. */
    def exit(timeout: FiniteDuration): Option[Int] =
      Option.when(proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS))(proc.exitValue)

  object Session:
    /** Start `argv` (no shell) with `env` added to the server's environment. */
    def start(argv: List[String], env: Map[String, String]): Either[String, Session] =
      val tool = argv.headOption.map(a => Path.of(a).getFileName.toString).getOrElse("?")
      val pb = new ProcessBuilder(argv.asJava)
      env.foreach((k, v) => pb.environment().put(k, v))
      Try(pb.start()).toEither.left.map(e => s"$tool could not be started: ${e.getMessage}").map { proc =>
        val s = new Session(proc, tool)
        s.startReaders()
        s
      }

  /** Read a stream to its end on a thread of its own (a full pipe would block the tool), and hand the text over when
    * asked.
    */
  private def drain(in: InputStream): () => String =
    val box = new CompletableFuture[String]()
    val _ = Thread.ofVirtual().start { () =>
      val _ = box.complete(try new String(in.readAllBytes(), UTF_8)
      catch case _: IOException => "")
    }
    () => Try(box.get(5, TimeUnit.SECONDS)).getOrElse("")

  /** Pure: the tool's own message for a log line — the first non-blank stderr line, bounded. */
  def problem(err: String): String =
    err.linesIterator.map(_.trim).find(_.nonEmpty).map(_.take(200)).getOrElse("no message")

  /** Pure: what a tool printed, without the one line break it ends with (`op read`, `bw get` and `keepassxc-cli show`
    * all end their value with one). Nothing else is trimmed: a value may begin or end with a space.
    */
  def value(out: String): String = out.stripSuffix("\n").stripSuffix("\r")

  /** Pure: an argument that would read as an option of the tool is refused rather than passed on. */
  def plain(what: String, s: String): Either[String, String] =
    if s.isEmpty then Left(s"$what is empty")
    else if s.startsWith("-") then Left(s"$what may not start with '-'")
    else Right(s)
