package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.plugin.EnvVar

import com.histmersion.plugin.Processes
import com.histmersion.plugin.Processes.Result
import java.io.{FileInputStream, InputStream, IOException, RandomAccessFile}
import java.lang.ProcessBuilder.Redirect
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.Try
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

@atBoundary(BoundaryKind.Process, "runs external programs for plugins")
final class SystemProcesses extends Processes:
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def run(
      argv: List[Processes.RunArgv],
      env: Map[EnvVar.Name, EnvVar.Value],
      stdin: Option[Processes.RunStdin],
      timeout: FiniteDuration
  ): Either[Problem, Result] =
    val tool = argv.headOption.map(a => Path.of(a.value).getFileName.toString).getOrElse("?")
    val pb = new ProcessBuilder(argv.map(_.value).asJava)
    env.foreach((k, v) => pb.environment().put(k.value, v.value))
    Try(pb.start()).toEither.left
      .map(e => notStarted(tool, e))
      .flatMap { proc =>
        val out = drain(proc.getInputStream)
        val err = drain(proc.getErrorStream)
        // a tool that exits without reading its input closes the pipe: that is its answer, not ours to report
        try
          val in = proc.getOutputStream
          try stdin.foreach(s => in.write(s.value.getBytes(UTF_8)))
          finally in.close()
        catch case _: IOException => ()
        val finished = proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
        if !finished then
          proc.descendants().forEach { p =>
            val _ = p.destroyForcibly()
          }
          val _ = proc.destroyForcibly()
          Left(Problem.Io.Process(Problem.Io.Tool(tool), Problem.Io.Ending.TimedOut(Problem.Millis.of(timeout))))
        else Right(Result(Result.Exit(proc.exitValue), Result.Out(out()), Result.Err(err())))
      }

  final class Session private (proc: Process, tool: String) extends Processes.Session:
    private val lock = new Object
    // what the tool printed so far; appended by the two reader threads, read under `lock`
    private val outBytes = new java.io.ByteArrayOutputStream()
    private val errBytes = new java.io.ByteArrayOutputStream()
    private val in = proc.getOutputStream
    private val readersAtWork = new java.util.concurrent.atomic.AtomicInteger(2)
    private val drainMillis = 2000L

    private def pump(from: InputStream, into: java.io.ByteArrayOutputStream): Unit =
      val buf = new Array[Byte](4096)
      try
        var n = from.read(buf) // `var`: the read loop's count
        while n >= 0 do
          lock.synchronized { into.write(buf, 0, n); lock.notifyAll() }
          n = from.read(buf)
      catch case _: IOException => ()
      finally
        val _ = Try(from.close())
        lock.synchronized { val _ = readersAtWork.decrementAndGet(); lock.notifyAll() }

    private def awaitReadersAtEnd(): Unit =
      val deadline = System.nanoTime + drainMillis * 1000000L
      lock.synchronized {
        while readersAtWork.get > 0 && deadline - System.nanoTime > 0 do
          lock.wait(math.max(1L, math.min((deadline - System.nanoTime) / 1000000, 50L)))
      }

    private[SystemProcesses] def startReaders(stdout: InputStream, stderr: InputStream): Unit =
      val _ = Thread.ofVirtual().start(() => pump(stdout, outBytes))
      val _ = Thread.ofVirtual().start(() => pump(stderr, errBytes))

    /** Everything the tool printed to stdout / stderr so far. */
    def out: Processes.Result.Out = Processes.Result.Out(lock.synchronized(outBytes.toString(UTF_8)))
    def err: Processes.Result.Err = Processes.Result.Err(lock.synchronized(errBytes.toString(UTF_8)))
    def alive: Boolean = proc.isAlive

    /** Write one line to the tool's stdin. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def send(line: Processes.Session.SendLine): Either[Problem, Unit] =
      try { in.write((line.value + "\n").getBytes(UTF_8)); in.flush(); Right(()) }
      catch
        case e: IOException =>
          Left(Problem.Io.Process(Problem.Io.Tool(s"$tool"), Problem.Io.Ending.NotStarted(External.Reported.of(e))))

    /** Wait until `ready` holds for stdout, the tool has exited, or `timeout` passed; true = `ready`. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def awaitOut(timeout: FiniteDuration)(
        ready: Processes.Result.Out => Boolean
    ): Boolean =
      await(timeout, () => ready(out))

    /** Wait until stderr has grown past `length` characters, the tool has exited, or `timeout` passed. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def awaitErr(length: Processes.Session.AwaitErrLength, timeout: FiniteDuration): Unit = {
      val _ = await(timeout, () => err.value.length > length.value)
    }

    private def await(timeout: FiniteDuration, ready: () => Boolean): Boolean =
      val deadline = System.nanoTime + timeout.toNanos
      lock.synchronized {
        // `var`: the time left, re-read after each wake-up
        var left = deadline - System.nanoTime
        while !ready() && proc.isAlive && left > 0 do
          lock.wait(math.max(1L, math.min(left / 1000000, 50L)))
          left = deadline - System.nanoTime
      }
      if !proc.isAlive && !lock.synchronized(ready()) then awaitReadersAtEnd()
      lock.synchronized(ready())

    /** Close the tool's stdin and wait for it to exit within `timeout` (killed otherwise); its exit code. */
    def close(timeout: FiniteDuration): Option[Processes.Result.Exit] =
      try in.close()
      catch case _: IOException => ()
      if proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS) then
        awaitReadersAtEnd()
        Some(Processes.Result.Exit(proc.exitValue))
      else
        proc.descendants().forEach { p =>
          val _ = p.destroyForcibly()
        }
        val _ = proc.destroyForcibly()
        None

    /** The exit code once the tool has exited within `timeout`. */
    def exit(timeout: FiniteDuration): Option[Processes.Result.Exit] =
      Option.when(proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)) {
        awaitReadersAtEnd()
        Processes.Result.Exit(proc.exitValue)
      }

  object Session:
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def start(
        argv: List[Processes.StartArgv],
        env: Map[EnvVar.Name, EnvVar.Value]
    ): Either[Problem, Session] =
      val tool = argv.headOption.map(a => Path.of(a.value).getFileName.toString).getOrElse("?")
      val pb = new ProcessBuilder(argv.map(_.value).asJava)
      env.foreach((k, v) => pb.environment().put(k.value, v.value))
      val fifos = fifoDir().flatMap(dir =>
        readEnds(dir) match
          case Some(ends) => Some((dir, ends))
          case None       =>
            removed(dir)
            None
      )
      fifos.foreach { (dir, _) =>
        val _ = pb.redirectOutput(dir.resolve("out").toFile).redirectError(dir.resolve("err").toFile)
      }
      val started = Try(pb.start()).toEither
      fifos.foreach((dir, _) => removed(dir))
      started.left
        .map { e =>
          fifos.foreach { (_, ends) =>
            val _ = Try(ends._1.close())
            val _ = Try(ends._2.close())
          }
          notStarted(tool, e)
        }
        .map { proc =>
          val s = new Session(proc, tool)
          fifos match
            case Some((_, (stdout, stderr))) => s.startReaders(stdout, stderr)
            case None                        => s.startReaders(proc.getInputStream, proc.getErrorStream)
          s
        }

    private def fifoDir(): Option[Path] =
      Try(Files.createTempDirectory("histmersion-session-")).toOption.flatMap { dir =>
        val made = Try {
          val mk = new ProcessBuilder("mkfifo", "-m", "600", dir.resolve("out").toString, dir.resolve("err").toString)
            .redirectOutput(Redirect.DISCARD)
            .redirectError(Redirect.DISCARD)
            .start()
          mk.getOutputStream.close()
          val done = mk.waitFor(5, TimeUnit.SECONDS)
          if !done then
            val _ = mk.destroyForcibly()
          done && mk.exitValue == 0
        }.getOrElse(false)
        if made then Some(dir)
        else
          removed(dir)
          None
      }

    private def readEnds(dir: Path): Option[(InputStream, InputStream)] =
      Try(readEnd(dir.resolve("out"))).toOption.flatMap { stdout =>
        val stderr = Try(readEnd(dir.resolve("err"))).toOption
        if stderr.isEmpty then
          val _ = Try(stdout.close())
        stderr.map(e => (stdout, e))
      }

    private def readEnd(fifo: Path): InputStream =
      val writer = new RandomAccessFile(fifo.toFile, "rw")
      try new FileInputStream(fifo.toFile)
      finally writer.close()

    private def removed(dir: Path): Unit =
      List(dir.resolve("out"), dir.resolve("err"), dir).foreach(p => Try(Files.deleteIfExists(p)))

  /** `tool` could not be started or run: what the JDK said. */
  private def notStarted(tool: String, e: Throwable): Problem =
    Problem.Io.Process(Problem.Io.Tool(tool), Problem.Io.Ending.NotStarted(External.Reported.of(e)))

  private def drain(in: InputStream): () => String =
    val box = new CompletableFuture[String]()
    val _ = Thread.ofVirtual().start { () =>
      val _ = box.complete(try new String(in.readAllBytes(), UTF_8)
      catch case _: IOException => "")
    }
    () => Try(box.get(5, TimeUnit.SECONDS)).getOrElse("")

  def start(
      argv: List[Processes.StartArgv],
      env: Map[EnvVar.Name, EnvVar.Value]
  ): Either[Problem, Processes.Session] = Session.start(argv, env)

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def capture(argv: List[Processes.CaptureArgv]): Either[Problem, Result] =
    val tool = argv.headOption.map(a => Path.of(a.value).getFileName.toString).getOrElse("?")
    Try {
      val proc = new ProcessBuilder(argv.map(_.value).asJava).start()
      val out = drain(proc.getInputStream)
      val err = drain(proc.getErrorStream)
      proc.getOutputStream.close()
      val exit = proc.waitFor()
      Result(Result.Exit(exit), Result.Out(out()), Result.Err(err()))
    }.toEither.left.map(e => notStarted(tool, e))
