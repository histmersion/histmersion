package com.histmersion.util.process

import com.histmersion.util.storage.HandedFile

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, IOException, OutputStream}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*
import com.histmersion.util.storage.Store

/** Launching child processes (the bundled database's tools, the converter, dumps): the one place a process is started.
  * Working directories and log files are store handles.
  */
trait Processes:
  /** Start the process; `Left` with why it could not be. */
  def start(launch: ProcessLaunch): Either[com.histmersion.util.process.Processes.StartResultPart1, RunningProcess]

  /** Start the process, read its output and wait for it to end. */
  def run(launch: ProcessLaunch): Either[com.histmersion.util.process.Processes.RunResultPart1, ProcessResult]

object Processes:
  type StartResultPart1 = StartResultPart1.T
  object StartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResultPart1 = RunResultPart1.T
  object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(): Processes = Implementation()

  final private class Implementation extends Processes:
    def start(launch: ProcessLaunch): Either[com.histmersion.util.process.Processes.Implementation.StartResultPart1, RunningProcess] =
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

    private def directory(launch: ProcessLaunch): Either[com.histmersion.util.process.Processes.Implementation.DirectoryResultPart1, Option[HandedFile]] = launch.workingStore match
      case None    => Right(None)
      case Some(s) => s.onDiskFile(Store.Key.Root).left.map(_.message).map(Some(_))

    private def log(launch: ProcessLaunch): Either[com.histmersion.util.process.Processes.Implementation.LogResultPart1, Option[ProcessBuilder.Redirect]] = launch.output match
      case ProcessOutput.Piped            => Right(None)
      case ProcessOutput.Discarded        => Right(Some(ProcessBuilder.Redirect.DISCARD))
      case ProcessOutput.AppendTo(s, key) =>
        s.onDiskFile(key).left.map(_.message).map(f => Some(ProcessBuilder.Redirect.appendTo(f)))
      case ProcessOutput.WriteTo(s, key) =>
        s.onDiskFile(key).left.map(_.message).map(f => Some(ProcessBuilder.Redirect.to(f)))

    def run(launch: ProcessLaunch): Either[com.histmersion.util.process.Processes.Implementation.RunResultPart1, ProcessResult] =
      start(launch).map { p =>
        val lines = scala.io.Source.fromInputStream(p.output).getLines().toList
        ProcessResult(p.waitFor(), lines)
      }

  object Implementation:
    type StartResultPart1 = StartResultPart1.T
    object StartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DirectoryResultPart1 = DirectoryResultPart1.T
    object DirectoryResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LogResultPart1 = LogResultPart1.T
    object LogResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunResultPart1 = RunResultPart1.T
    object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final private class OsProcess(p: Process) extends RunningProcess:
    def pid: com.histmersion.util.process.Processes.OsProcess.PidResult = p.pid
    def isAlive: com.histmersion.util.process.Processes.OsProcess.IsAliveResult = p.isAlive
    def output: InputStream = p.getInputStream
    def input: OutputStream = p.getOutputStream
    def waitFor(): com.histmersion.util.process.Processes.OsProcess.WaitForResult = p.waitFor()
    def waitFor(timeout: com.histmersion.util.process.Processes.OsProcess.WaitForTimeout, unit: TimeUnit): Boolean = p.waitFor(timeout, unit)
    def destroy(): Unit = p.destroy()
    def destroyForcibly(): Unit =
      val _ = p.destroyForcibly()

  object OsProcess:
    type PidResult = PidResult.T
    object PidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsAliveResult = IsAliveResult.T
    object IsAliveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WaitForResult = WaitForResult.T
    object WaitForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WaitForTimeout = WaitForTimeout.T
    object WaitForTimeout extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Processes that never start: each launch is recorded and answered by `answer` (its exit code and output). */
  final class InMemory(answer: ProcessLaunch => ProcessResult) extends Processes:
    private val launched = AtomicReference(List.empty[ProcessLaunch])

    /** The launches asked for, in order. */
    def launches: List[ProcessLaunch] = launched.get.reverse

    def start(launch: ProcessLaunch): Either[com.histmersion.util.process.Processes.InMemory.StartResultPart1, RunningProcess] =
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

    def run(launch: ProcessLaunch): Either[com.histmersion.util.process.Processes.InMemory.RunResultPart1, ProcessResult] =
      start(launch).map(p => ProcessResult(p.waitFor(), scala.io.Source.fromInputStream(p.output).getLines().toList))

  object InMemory:
    type StartResultPart1 = StartResultPart1.T
    object StartResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunResultPart1 = RunResultPart1.T
    object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
