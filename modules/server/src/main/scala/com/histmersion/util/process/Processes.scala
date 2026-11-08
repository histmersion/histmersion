package com.histmersion
package util.process

import com.histmersion.util.storage.HandedFile

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, IOException, OutputStream}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*
import com.histmersion.util.storage.Store
import io.github.iltotore.iron.constraint.any.Pure

/** Launching child processes (the bundled database's tools, the converter, dumps): the one place a process is started.
  * Working directories and log files are store handles.
  */
trait Processes:
  /** Start the process; `Left` with why it could not be. */
  def start(launch: ProcessLaunch): Either[Processes.Problem, RunningProcess]

  /** Start the process, read its output and wait for it to end. */
  def run(launch: ProcessLaunch): Either[Processes.Problem, ProcessResult]

object Processes:
  type Pid = Pid.T
  object Pid extends Newtype[Long, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type WaitTimeout = WaitTimeout.T
  object WaitTimeout extends Newtype[Long, Pure]

  def apply(): Processes = Implementation()

  final private class Implementation extends Processes:
    def start(launch: ProcessLaunch): Either[Problem, RunningProcess] =
      for
        dir <- directory(launch)
        out <- log(launch)
        started <-
          try
            val pb = ProcessBuilder(launch.command.map(_.value).asJava).redirectErrorStream(launch.mergeErrors.value)
            dir.foreach(d => pb.directory(d))
            out.foreach(o => pb.redirectOutput(o))
            val env = pb.environment()
            launch.environment.foreach((k, v) => env.put(k.value, v.value))
            Right(OsProcess(pb.start()))
          catch case e: IOException => Left(Problem(e.getMessage))
      yield started

    private def directory(launch: ProcessLaunch): Either[Problem, Option[HandedFile]] = launch.workingStore match
      case None    => Right(None)
      case Some(s) => s.onDiskFile(Store.Key.Root).left.map(e => Problem(e.message.value)).map(Some(_))

    private def log(launch: ProcessLaunch): Either[Problem, Option[ProcessBuilder.Redirect]] = launch.output match
      case ProcessOutput.Piped            => Right(None)
      case ProcessOutput.Discarded        => Right(Some(ProcessBuilder.Redirect.DISCARD))
      case ProcessOutput.AppendTo(s, key) =>
        s.onDiskFile(key).left.map(e => Problem(e.message.value)).map(f => Some(ProcessBuilder.Redirect.appendTo(f)))
      case ProcessOutput.WriteTo(s, key) =>
        s.onDiskFile(key).left.map(e => Problem(e.message.value)).map(f => Some(ProcessBuilder.Redirect.to(f)))

    def run(launch: ProcessLaunch): Either[Problem, ProcessResult] =
      start(launch).map { p =>
        val lines = scala.io.Source.fromInputStream(p.output).getLines().toList
        ProcessResult(p.waitFor(), lines.map(ProcessResult.Lines(_)))
      }


  final private class OsProcess(p: Process) extends RunningProcess:
    def pid: Pid = Pid(p.pid)
    def isAlive: Boolean = p.isAlive
    def output: InputStream = p.getInputStream
    def input: OutputStream = p.getOutputStream
    def waitFor(): ProcessResult.ExitCode = ProcessResult.ExitCode(p.waitFor())
    def waitFor(timeout: WaitTimeout, unit: TimeUnit): Boolean = p.waitFor(timeout.value, unit)
    def destroy(): Unit = p.destroy()
    def destroyForcibly(): Unit =
      val _ = p.destroyForcibly()


  /** Processes that never start: each launch is recorded and answered by `answer` (its exit code and output). */
  final class InMemory(answer: ProcessLaunch => ProcessResult) extends Processes:
    private val launched = AtomicReference(List.empty[ProcessLaunch])

    /** The launches asked for, in order. */
    def launches: List[ProcessLaunch] = launched.get.reverse

    def start(launch: ProcessLaunch): Either[Problem, RunningProcess] =
      launched.updateAndGet(launch :: _)
      val r = answer(launch)
      Right(new RunningProcess:
        def pid: Pid = Pid(0L)
        def isAlive: Boolean = false
        val output: InputStream = ByteArrayInputStream(r.lines.map(_.value + "\n").mkString.getBytes("UTF-8"))
        val input: OutputStream = ByteArrayOutputStream()
        def waitFor(): ProcessResult.ExitCode = r.exitCode
        def waitFor(timeout: WaitTimeout, unit: TimeUnit): Boolean = true
        def destroy(): Unit = ()
        def destroyForcibly(): Unit = ())

    def run(launch: ProcessLaunch): Either[Problem, ProcessResult] =
      start(launch).map(p => ProcessResult(p.waitFor(), scala.io.Source.fromInputStream(p.output).getLines().toList.map(ProcessResult.Lines(_))))


