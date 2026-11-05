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

  private def drain(in: InputStream): () => String =
    val box = new CompletableFuture[String]()
    val _ = Thread.ofVirtual().start { () =>
      val _ = box.complete(try new String(in.readAllBytes(), UTF_8)
      catch case _: IOException => "")
    }
    () => Try(box.get(5, TimeUnit.SECONDS)).getOrElse("")

  def start(argv: List[String], env: Map[String, String]): Either[String, Processes.Session] = Session.start(argv, env)

  def capture(argv: List[String]): Either[String, Result] =
    val tool = argv.headOption.map(a => Path.of(a).getFileName.toString).getOrElse("?")
    Try {
      val proc = new ProcessBuilder(argv.asJava).start()
      val out = drain(proc.getInputStream)
      val err = drain(proc.getErrorStream)
      proc.getOutputStream.close()
      val exit = proc.waitFor()
      Result(exit, out(), err())
    }.toEither.left.map(e => s"$tool could not be run: ${e.getMessage}")
