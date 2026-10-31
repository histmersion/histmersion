package com.histmersion.util.process

import com.histmersion.util.storage.{HandedFile, StoreKey}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, IOException, OutputStream}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*

/** Launching child processes (the bundled database's tools, the converter, dumps): the one place a process is started.
  * Working directories and log files are store handles.
  */
trait Processes:
  /** Start the process; `Left` with why it could not be. */
  def start(launch: ProcessLaunch): Either[String, RunningProcess]

  /** Start the process, read its output and wait for it to end. */
  def run(launch: ProcessLaunch): Either[String, ProcessResult]

object Processes:
  def apply(): Processes = Implementation()

  final private class Implementation extends Processes:
    def start(launch: ProcessLaunch): Either[String, RunningProcess] =
      for
        dir <- directory(launch)
        out <- log(launch)
        started <-
          try
            val pb = ProcessBuilder(launch.command.asJava).redirectErrorStream(launch.mergeErrors)
            dir.foreach(d => pb.directory(d))
            out.foreach(o => pb.redirectOutput(o))
            val env = pb.environment()
            launch.environment.foreach((k, v) => env.put(k, v))
            Right(OsProcess(pb.start()))
          catch case e: IOException => Left(e.getMessage)
      yield started

    private def directory(launch: ProcessLaunch): Either[String, Option[HandedFile]] = launch.workingStore match
      case None    => Right(None)
      case Some(s) => s.onDiskFile(StoreKey.Root).left.map(_.message).map(Some(_))

    private def log(launch: ProcessLaunch): Either[String, Option[ProcessBuilder.Redirect]] = launch.output match
      case ProcessOutput.Piped            => Right(None)
      case ProcessOutput.Discarded        => Right(Some(ProcessBuilder.Redirect.DISCARD))
      case ProcessOutput.AppendTo(s, key) =>
        s.onDiskFile(key).left.map(_.message).map(f => Some(ProcessBuilder.Redirect.appendTo(f)))
      case ProcessOutput.WriteTo(s, key) =>
        s.onDiskFile(key).left.map(_.message).map(f => Some(ProcessBuilder.Redirect.to(f)))

    def run(launch: ProcessLaunch): Either[String, ProcessResult] =
      start(launch).map { p =>
        val lines = scala.io.Source.fromInputStream(p.output).getLines().toList
        ProcessResult(p.waitFor(), lines)
      }

  final private class OsProcess(p: Process) extends RunningProcess:
    def pid: Long = p.pid
    def isAlive: Boolean = p.isAlive
    def output: InputStream = p.getInputStream
    def input: OutputStream = p.getOutputStream
    def waitFor(): Int = p.waitFor()
    def waitFor(timeout: Long, unit: TimeUnit): Boolean = p.waitFor(timeout, unit)
    def destroy(): Unit = p.destroy()
    def destroyForcibly(): Unit =
      val _ = p.destroyForcibly()

  /** Processes that never start: each launch is recorded and answered by `answer` (its exit code and output). */
  final class InMemory(answer: ProcessLaunch => ProcessResult) extends Processes:
    private val launched = AtomicReference(List.empty[ProcessLaunch])

    /** The launches asked for, in order. */
    def launches: List[ProcessLaunch] = launched.get.reverse

    def start(launch: ProcessLaunch): Either[String, RunningProcess] =
      launched.updateAndGet(launch :: _)
      val r = answer(launch)
      Right(new RunningProcess:
        def pid: Long = 0L
        def isAlive: Boolean = false
        val output: InputStream = ByteArrayInputStream(r.lines.map(_ + "\n").mkString.getBytes("UTF-8"))
        val input: OutputStream = ByteArrayOutputStream()
        def waitFor(): Int = r.exitCode
        def waitFor(timeout: Long, unit: TimeUnit): Boolean = true
        def destroy(): Unit = ()
        def destroyForcibly(): Unit = ())

    def run(launch: ProcessLaunch): Either[String, ProcessResult] =
      start(launch).map(p => ProcessResult(p.waitFor(), scala.io.Source.fromInputStream(p.output).getLines().toList))
