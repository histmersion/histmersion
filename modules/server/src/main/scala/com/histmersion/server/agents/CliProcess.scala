package com.histmersion
package server.agents

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** One CLI process per step attempt: in an empty temporary working directory (no project `AGENTS.md`, `.codex/`,
  * `.claude/` or `opencode.json` is read), the user message on stdin, stdout read LINE BY LINE as it arrives (a quota
  * guard can stop the process mid-step), stderr kept for the error, a timeout, and the whole process tree killed on
  * cancel.
  *
  * #1650 (a step that hung for hours past its timeout): the timeout must cover EVERYTHING the call waits on. stdin is
  * written from its own thread (a CLI that never reads it, given a prompt larger than the pipe buffer, used to block
  * the write before the timeout even started); the readers are joined with a bound (a grandchild that inherited stdout
  * keeps the pipe open after the CLI exits); and a kill takes the descendants first, then the parent, then checks
  * again. The CLI is started as the leader of its own process group where the platform lets us (`setsid`, else
  * `perl -e setpgrp`), so a kill also reaches a grandchild that outlived its parent — `Process.descendants` no longer
  * sees an orphan.
  */
object CliProcess:

  /** What a run printed; `stopped` = `onLine` asked for it to end (the quota guard), with why. */
  type CannotStartHint = CannotStartHint.T
  object CannotStartHint extends Newtype[String, Pure]
  type RunStdin = RunStdin.T
  object RunStdin extends Newtype[String, Pure]
  type RunTimeoutSeconds = RunTimeoutSeconds.T
  object RunTimeoutSeconds extends Newtype[Int, Pure]
  type RunHint = CannotStartHint
  val RunHint: CannotStartHint.type = CannotStartHint
  type WithDirPrefix = WithDirPrefix.T
  object WithDirPrefix extends Newtype[String, Pure]
  type PrivateFileText = PrivateFileText.T
  object PrivateFileText extends Newtype[String, Pure]

  final case class Run(lines: List[Run.Lines], exit: Run.Exit, stderr: Run.Stderr, stopped: Option[Run.Stopped])

  object Run:
    type Lines = Lines.T
    object Lines extends Newtype[String, Pure]
    type Exit = Exit.T
    object Exit extends Newtype[Int, Pure]
    type Stderr = Stderr.T
    object Stderr extends Newtype[String, Pure]
    type Stopped = Stopped.T
    object Stopped extends Newtype[String, Pure]

  /** Raised when the executable cannot be started at all. */
  def cannotStart(command: ClaudeCode.Argument, e: java.io.IOException, hint: CannotStartHint): java.io.IOException =
    java.io.IOException(s"cannot run '$command' (${e.getMessage}) — $hint")

  /** What the engine watches of a step's processes (the stall watchdog): every process a step starts is registered
    * while it lives, and every line it prints is a heartbeat. Set per run thread by [[withHooks]] and inherited by the
    * threads a run forks (its parallel steps).
    */
  final case class Hooks(register: Process => Unit, unregister: Process => Unit, beat: () => Unit)

  private val hooks = InheritableThreadLocal[Hooks]()

  /** Run `f` with `h` as this thread's (and its forks') hooks. */
  def withHooks[A](h: Hooks)(f: => A): A =
    val before = hooks.get()
    hooks.set(h)
    try f
    finally hooks.set(before)

  private def current: Option[Hooks] = Option(hooks.get())

  /** How long the readers may take to drain after the CLI exited (a well-behaved one closes its pipes as it exits)
    * before whatever still holds them is killed.
    */
  val DrainMillis = 5000L

  /** How often the process tree is looked at while the CLI runs (the descendants seen are remembered, so a kill reaches
    * one whose parent has since exited).
    */
  private val PollMillis = 250L

  /** The prefix that starts a command as the leader of a new process group, if this platform has one. */
  private lazy val groupPrefix: List[String] =
    val setsid = List("/usr/bin/setsid", "/bin/setsid").find(f => Files.isExecutable(Path.of(f)))
    setsid
      .map(List(_))
      .orElse(
        Option.when(Files.isExecutable(Path.of("/usr/bin/perl")))(
          List("/usr/bin/perl", "-e", "setpgrp(0,0); exec { $ARGV[0] } @ARGV; exit 127")
        )
      )
      .getOrElse(Nil)

  /** `command` resolved the way `execvp` would against `path` (None: not found, or not executable). */
  private[agents] def resolve(command: ClaudeCode.Argument, path: Option[String], dir: Path): Option[Path] =
    if command.value.contains('/') then Option(dir.resolve(command.value)).filter(Files.isExecutable)
    else
      path.toList
        .flatMap(_.split(java.io.File.pathSeparator).toList)
        .filter(_.nonEmpty)
        .map(d => Path.of(d).resolve(command.value))
        .find(f => Files.isRegularFile(f) && Files.isExecutable(f))

  /** One live CLI: the process, whether it leads its own group, and every descendant seen so far. */
  final private class ProcessTree(val p: Process, val grouped: Boolean):
    val seen: java.util.Set[ProcessHandle] = java.util.concurrent.ConcurrentHashMap.newKeySet[ProcessHandle]()

    /** Remember the descendants it has now. macOS's process-table read (`sysctl KERN_PROC`) fails now and then while
      * processes come and go ("Cannot allocate memory"): that look is skipped, the next one (every PollMillis, and
      * again before each kill) sees them.
      */
    def look(): Unit =
      try
        p.descendants().forEach { d =>
          val _ = seen.add(d)
        }
      catch case _: RuntimeException => ()
    def kill(): Unit =
      look()
      val all = seen.asScala.toList
      all.foreach { d =>
        val _ = d.destroy()
      }
      if grouped then signalGroup(p.pid, "TERM")
      p.destroy()
      if !p.waitFor(5, TimeUnit.SECONDS) then { val _ = p.destroyForcibly() }
      // re-check: a descendant that ignored the TERM, or one started in the meantime
      look()
      if grouped then signalGroup(p.pid, "KILL")
      seen.asScala.filter(_.isAlive).foreach { d =>
        val _ = d.destroyForcibly()
      }

  private def signalGroup(pgid: Long, sig: String): Unit =
    try
      val k = ProcessBuilder("/bin/kill", s"-$sig", "--", s"-$pgid").redirectErrorStream(true).start()
      k.getInputStream.readAllBytes()
      val _ = k.waitFor(2, TimeUnit.SECONDS)
    catch
      case e: InterruptedException => Thread.currentThread().interrupt()
      case _: Exception            => () // no `kill` (or the group is gone): the per-process kills above still ran

  def run(
      argv: List[ClaudeCode.Argument],
      dir: Path,
      env: java.util.Map[String, String] => Unit,
      stdin: RunStdin,
      timeoutSeconds: RunTimeoutSeconds,
      hint: RunHint,
      onLine: Run.Lines => Option[Run.Stopped] = _ => None
  ): Run =
    val pb = ProcessBuilder().directory(dir.toFile)
    env(pb.environment())
    // in a process group of its own only when the executable resolves: a missing one must still fail
    // at start ("cannot run …"), not as the wrapper's exit status
    val grouped = groupPrefix.nonEmpty && resolve(argv.head, Option(pb.environment().get("PATH")), dir).isDefined
    pb.command((if grouped then groupPrefix ++ argv.map(_.value) else argv.map(_.value)).asJava)
    val p = try pb.start()
    catch case e: java.io.IOException => throw cannotStart(argv.head, e, hint)
    val tree = ProcessTree(p, grouped)
    val h = current
    h.foreach(_.register(p))
    val stdout = ConcurrentLinkedQueue[Run.Lines]()
    val stderr = StringBuilder()
    val stopped =
      java.util.concurrent.atomic.AtomicReference[Option[Run.Stopped]](None) // set once by the reader thread
    val tOut = Thread.ofVirtual().start { () =>
      try
        p.inputReader(StandardCharsets.UTF_8).lines().forEach { l =>
          val _ = stdout.add(Run.Lines(l))
          h.foreach(_.beat())
          if stopped.get().isEmpty then
            onLine(Run.Lines(l)).foreach { why =>
              stopped.set(Some(why))
              tree.kill()
            }
        }
      catch case _: java.io.UncheckedIOException => () // the pipe closed under us (a kill)
    }
    val tErr = Thread.ofVirtual().start { () =>
      try
        val s = String(p.getErrorStream.readAllBytes(), StandardCharsets.UTF_8)
        stderr.synchronized { val _ = stderr.append(s.takeRight(4000)) }
      catch case _: java.io.IOException => ()
    }
    // its own thread: a CLI that does not read its stdin must not hold the timeout off (#1650)
    val tIn = Thread.ofVirtual().start { () =>
      val w = p.outputWriter(StandardCharsets.UTF_8)
      try w.write(stdin.value)
      catch case _: java.io.IOException => () // the CLI may close its stdin early (a stopped run)
      finally
        try w.close()
        catch case _: java.io.IOException => ()
    }
    try
      val deadline = System.nanoTime() + math.max(1, timeoutSeconds.value).toLong * 1000000000L
      var exited = false // the wait loop's flag; local to this call
      while !exited do
        tree.look()
        val left = deadline - System.nanoTime()
        if left <= 0 then
          tree.kill()
          throw java.util.concurrent.TimeoutException(
            s"${Path.of(argv.head.value).getFileName} did not finish within $timeoutSeconds s"
          )
        exited = p.waitFor(math.min(PollMillis * 1000000L, left), TimeUnit.NANOSECONDS)
      // the CLI is gone; whatever still holds its pipes (a grandchild) has DrainMillis, then is killed
      tOut.join(java.time.Duration.ofMillis(DrainMillis))
      tErr.join(java.time.Duration.ofMillis(500))
      if tOut.isAlive || tErr.isAlive then
        tree.kill()
        tOut.join(java.time.Duration.ofMillis(2000))
        tErr.join(java.time.Duration.ofMillis(500))
      tIn.join(java.time.Duration.ofMillis(500))
      val held =
        Option.when(tOut.isAlive || tErr.isAlive)("(a process it started kept its output open; stopped reading it)")
      val err = stderr.synchronized(stderr.toString).trim
      Run(
        stdout.asScala.toList,
        Run.Exit(p.exitValue()),
        Run.Stderr((err + held.fold("")(m => if err.isEmpty then m else s"\n$m")).trim),
        stopped.get()
      )
    catch
      case e: InterruptedException =>
        tree.kill() // a cancelled run: the CLI and whatever it started go with it
        throw e
    finally
      if p.isAlive || tree.seen.asScala.exists(_.isAlive) then tree.kill()
      // a member of its group nobody saw (started and orphaned between two looks) goes too
      else if grouped then signalGroup(p.pid, "KILL")
      h.foreach(_.unregister(p))

  /** Kill `p` and every descendant it has now (the descendants first, then the parent, then a check). */
  def kill(p: Process): Unit = ProcessTree(p, grouped = false).kill()

  /** A scratch working directory, removed with everything in it afterwards. */
  def withDir[A](prefix: WithDirPrefix)(f: Path => A): A =
    val dir = Files.createTempDirectory(prefix.value)
    try f(dir)
    finally
      val files = Files.walk(dir)
      try
        files.sorted(java.util.Comparator.reverseOrder()).forEach { p =>
          val _ = Files.deleteIfExists(p)
        }
      finally files.close()

  /** An owner-only file (it may carry a run token). */
  def privateFile(f: Path, text: PrivateFileText): Path =
    Files.writeString(f, text.value, StandardCharsets.UTF_8)
    try {
      val _ = Files.setPosixFilePermissions(f, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
    } catch case _: UnsupportedOperationException => ()
    f
