package com.histmersion.pluginhost

import com.histmersion.plugin.Processes
import com.histmersion.plugin.Processes.Result
import java.io.{InputStream, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.Try

final class SystemProcesses extends Processes:
  def run(
      argv: List[Processes.RunArgv],
      env: Map[Processes.RunEnvPart1, Processes.RunEnvPart2],
      stdin: Option[Processes.RunStdin],
      timeout: FiniteDuration
  ): Either[Processes.RunResultPart1, Result] =
    val tool = argv.headOption.map(a => Path.of(a.value).getFileName.toString).getOrElse("?")
    val pb = new ProcessBuilder(argv.map(_.value).asJava)
    env.foreach((k, v) => pb.environment().put(k.value, v.value))
    Try(pb.start()).toEither.left.map(e => Processes.RunResultPart1(s"$tool could not be started: ${e.getMessage}")).flatMap { proc =>
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
        Left(Processes.RunResultPart1(s"$tool did not answer within ${timeout.toSeconds} s"))
      else Right(Result(Result.Exit(proc.exitValue), Result.Out(out()), Result.Err(err())))
    }

  final class Session private (proc: Process, tool: String) extends Processes.Session:
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
  
    private[SystemProcesses] def startReaders(): Unit =
      val _ = Thread.ofVirtual().start(() => pump(proc.getInputStream, outBytes))
      val _ = Thread.ofVirtual().start(() => pump(proc.getErrorStream, errBytes))
  
    /** Everything the tool printed to stdout / stderr so far. */
    def out: Processes.Session.OutResult = Processes.Session.OutResult(lock.synchronized(outBytes.toString(UTF_8)))
    def err: Processes.Session.ErrResult = Processes.Session.ErrResult(lock.synchronized(errBytes.toString(UTF_8)))
    def alive: Processes.Session.AliveResult = Processes.Session.AliveResult(proc.isAlive)
  
    /** Write one line to the tool's stdin. */
    def send(line: Processes.Session.SendLine): Either[Processes.Session.SendResultPart1, Unit] =
      try { in.write((line.value + "\n").getBytes(UTF_8)); in.flush(); Right(()) }
      catch case e: IOException => Left(Processes.Session.SendResultPart1(s"$tool stopped reading its input"))
  
    /** Wait until `ready` holds for stdout, the tool has exited, or `timeout` passed; true = `ready`. */
    def awaitOut(timeout: FiniteDuration)(ready: Processes.Session.AwaitOutReadyArg1 => Processes.Session.AwaitOutReadyResult): Processes.Session.AwaitOutResult =
      Processes.Session.AwaitOutResult(await(timeout, () => ready(Processes.Session.AwaitOutReadyArg1(out.value)).value))
  
    /** Wait until stderr has grown past `length` characters, the tool has exited, or `timeout` passed. */
    def awaitErr(length: Processes.Session.AwaitErrLength, timeout: FiniteDuration): Unit = { val _ = await(timeout, () => err.value.length > length.value) }
  
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
    def close(timeout: FiniteDuration): Option[Processes.Session.CloseResult] =
      try in.close()
      catch case _: IOException => ()
      if proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS) then Some(Processes.Session.CloseResult(proc.exitValue))
      else
        proc.descendants().forEach { p =>
          val _ = p.destroyForcibly()
        }
        val _ = proc.destroyForcibly()
        None
  
    /** The exit code once the tool has exited within `timeout`. */
    def exit(timeout: FiniteDuration): Option[Processes.Session.ExitResult] =
      Option.when(proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS))(Processes.Session.ExitResult(proc.exitValue))

  object Session:
    def start(argv: List[Processes.StartArgv], env: Map[Processes.StartEnvPart1, Processes.StartEnvPart2]): Either[Processes.StartResultPart1, Session] =
      val tool = argv.headOption.map(a => Path.of(a.value).getFileName.toString).getOrElse("?")
      val pb = new ProcessBuilder(argv.map(_.value).asJava)
      env.foreach((k, v) => pb.environment().put(k.value, v.value))
      Try(pb.start()).toEither.left.map(e => Processes.StartResultPart1(s"$tool could not be started: ${e.getMessage}")).map { proc =>
        val s = new Session(proc, tool)
        s.startReaders()
        s
      }

  private def drain(in: InputStream): () => String =
    val box = new CompletableFuture[String]()
    val _ = Thread.ofVirtual().start { () =>
      val _ = box.complete(try new String(in.readAllBytes(), UTF_8)
      catch case _: IOException => "")
    }
    () => Try(box.get(5, TimeUnit.SECONDS)).getOrElse("")

  def start(argv: List[Processes.StartArgv], env: Map[Processes.StartEnvPart1, Processes.StartEnvPart2]): Either[Processes.StartResultPart1, Processes.Session] = Session.start(argv, env)

  def capture(argv: List[Processes.CaptureArgv]): Either[Processes.CaptureResultPart1, Result] =
    val tool = argv.headOption.map(a => Path.of(a.value).getFileName.toString).getOrElse("?")
    Try {
      val proc = new ProcessBuilder(argv.map(_.value).asJava).start()
      val out = drain(proc.getInputStream)
      val err = drain(proc.getErrorStream)
      proc.getOutputStream.close()
      val exit = proc.waitFor()
      Result(Result.Exit(exit), Result.Out(out()), Result.Err(err()))
    }.toEither.left.map(e => Processes.CaptureResultPart1(s"$tool could not be run: ${e.getMessage}"))
