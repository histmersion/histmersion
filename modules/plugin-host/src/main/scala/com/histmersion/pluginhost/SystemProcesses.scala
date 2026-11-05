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
      argv: List[com.histmersion.pluginhost.SystemProcesses.RunArgv],
      env: Map[com.histmersion.pluginhost.SystemProcesses.RunEnvPart1, com.histmersion.pluginhost.SystemProcesses.RunEnvPart2],
      stdin: Option[com.histmersion.pluginhost.SystemProcesses.RunStdin],
      timeout: FiniteDuration
  ): Either[com.histmersion.pluginhost.SystemProcesses.RunResultPart1, Result] =
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
    def out: com.histmersion.pluginhost.SystemProcesses.Session.OutResult = lock.synchronized(outBytes.toString(UTF_8))
    def err: com.histmersion.pluginhost.SystemProcesses.Session.ErrResult = lock.synchronized(errBytes.toString(UTF_8))
    def alive: com.histmersion.pluginhost.SystemProcesses.Session.AliveResult = proc.isAlive
  
    /** Write one line to the tool's stdin. */
    def send(line: com.histmersion.pluginhost.SystemProcesses.Session.SendLine): Either[com.histmersion.pluginhost.SystemProcesses.Session.SendResultPart1, Unit] =
      try { in.write((line + "\n").getBytes(UTF_8)); in.flush(); Right(()) }
      catch case e: IOException => Left(s"$tool stopped reading its input")
  
    /** Wait until `ready` holds for stdout, the tool has exited, or `timeout` passed; true = `ready`. */
    def awaitOut(timeout: FiniteDuration)(ready: com.histmersion.pluginhost.SystemProcesses.Session.AwaitOutReadyArg1 => com.histmersion.pluginhost.SystemProcesses.Session.AwaitOutReadyResult): com.histmersion.pluginhost.SystemProcesses.Session.AwaitOutResult = await(timeout, () => ready(out))
  
    /** Wait until stderr has grown past `length` characters, the tool has exited, or `timeout` passed. */
    def awaitErr(length: com.histmersion.pluginhost.SystemProcesses.Session.AwaitErrLength, timeout: FiniteDuration): Unit = { val _ = await(timeout, () => err.length > length) }
  
    private def await(timeout: FiniteDuration, ready: () => com.histmersion.pluginhost.SystemProcesses.Session.AwaitReadyResult): com.histmersion.pluginhost.SystemProcesses.Session.AwaitResult =
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
    def close(timeout: FiniteDuration): Option[com.histmersion.pluginhost.SystemProcesses.Session.CloseResult] =
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
    def exit(timeout: FiniteDuration): Option[com.histmersion.pluginhost.SystemProcesses.Session.ExitResult] =
      Option.when(proc.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS))(proc.exitValue)

  object Session:
    /** Start `argv` (no shell) with `env` added to the server's environment. */
    type OutResult = OutResult.T
    object OutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ErrResult = ErrResult.T
    object ErrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AliveResult = AliveResult.T
    object AliveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SendResultPart1 = SendResultPart1.T
    object SendResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SendLine = SendLine.T
    object SendLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitOutResult = AwaitOutResult.T
    object AwaitOutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitOutReadyArg1 = AwaitOutReadyArg1.T
    object AwaitOutReadyArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitOutReadyResult = AwaitOutReadyResult.T
    object AwaitOutReadyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitErrLength = AwaitErrLength.T
    object AwaitErrLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitResult = AwaitResult.T
    object AwaitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AwaitReadyResult = AwaitReadyResult.T
    object AwaitReadyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CloseResult = CloseResult.T
    object CloseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExitResult = ExitResult.T
    object ExitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartResultPart1 = StartResultPart1.T
    object StartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartArgv = StartArgv.T
    object StartArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartEnvPart1 = StartEnvPart1.T
    object StartEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartEnvPart2 = StartEnvPart2.T
    object StartEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def start(argv: List[com.histmersion.pluginhost.SystemProcesses.Session.StartArgv], env: Map[com.histmersion.pluginhost.SystemProcesses.Session.StartEnvPart1, com.histmersion.pluginhost.SystemProcesses.Session.StartEnvPart2]): Either[com.histmersion.pluginhost.SystemProcesses.Session.StartResultPart1, Session] =
      val tool = argv.headOption.map(a => Path.of(a).getFileName.toString).getOrElse("?")
      val pb = new ProcessBuilder(argv.asJava)
      env.foreach((k, v) => pb.environment().put(k, v))
      Try(pb.start()).toEither.left.map(e => s"$tool could not be started: ${e.getMessage}").map { proc =>
        val s = new Session(proc, tool)
        s.startReaders()
        s
      }

  private def drain(in: InputStream): () => com.histmersion.pluginhost.SystemProcesses.DrainResultResult =
    val box = new CompletableFuture[String]()
    val _ = Thread.ofVirtual().start { () =>
      val _ = box.complete(try new String(in.readAllBytes(), UTF_8)
      catch case _: IOException => "")
    }
    () => Try(box.get(5, TimeUnit.SECONDS)).getOrElse("")

  def start(argv: List[com.histmersion.pluginhost.SystemProcesses.StartArgv], env: Map[com.histmersion.pluginhost.SystemProcesses.StartEnvPart1, com.histmersion.pluginhost.SystemProcesses.StartEnvPart2]): Either[com.histmersion.pluginhost.SystemProcesses.StartResultPart1, Processes.Session] = Session.start(argv, env)

  def capture(argv: List[com.histmersion.pluginhost.SystemProcesses.CaptureArgv]): Either[com.histmersion.pluginhost.SystemProcesses.CaptureResultPart1, Result] =
    val tool = argv.headOption.map(a => Path.of(a).getFileName.toString).getOrElse("?")
    Try {
      val proc = new ProcessBuilder(argv.asJava).start()
      val out = drain(proc.getInputStream)
      val err = drain(proc.getErrorStream)
      proc.getOutputStream.close()
      val exit = proc.waitFor()
      Result(exit, out(), err())
    }.toEither.left.map(e => s"$tool could not be run: ${e.getMessage}")

object SystemProcesses:
  type RunResultPart1 = RunResultPart1.T
  object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunArgv = RunArgv.T
  object RunArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunEnvPart1 = RunEnvPart1.T
  object RunEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunEnvPart2 = RunEnvPart2.T
  object RunEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStdin = RunStdin.T
  object RunStdin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainResultResult = DrainResultResult.T
  object DrainResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartResultPart1 = StartResultPart1.T
  object StartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartArgv = StartArgv.T
  object StartArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartEnvPart1 = StartEnvPart1.T
  object StartEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartEnvPart2 = StartEnvPart2.T
  object StartEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureResultPart1 = CaptureResultPart1.T
  object CaptureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CaptureArgv = CaptureArgv.T
  object CaptureArgv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
