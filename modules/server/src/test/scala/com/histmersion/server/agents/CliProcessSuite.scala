package com.histmersion.server.agents

import com.histmersion.testsupport.ComparedByValue.given

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeoutException

/** #1650: a CLI step must never outlive its timeout, whatever the CLI does — not read its stdin, sleep, or leave a
  * grandchild holding its stdout — and a kill takes the whole process tree. Fake CLIs are shell scripts; nothing is
  * spent.
  */
class CliProcessSuite extends munit.FunSuite:
  override val munitTimeout = scala.concurrent.duration.Duration(90, "s")

  private def script(body: String): (Path, Path) =
    val dir = Files.createTempDirectory("hm-cliproc-")
    val f = dir.resolve("fake")
    Files.writeString(f, "#!/bin/bash\n" + body.replace("@DIR@", s"'$dir'") + "\n")
    f.toFile.setExecutable(true)
    (f, dir)

  private def cli(f: Path): List[ClaudeCode.Argument] = List(ClaudeCode.Argument(f.toString))

  private def alive(pid: Long): Boolean = ProcessHandle.of(pid).map(_.isAlive).orElse(false)

  private def pidIn(f: Path): Long =
    val deadline = System.currentTimeMillis() + 5000
    while (!Files.exists(f) || Files.readString(f).trim.isEmpty) && System.currentTimeMillis() < deadline do
      Thread.sleep(50)
    Files.readString(f).trim.toLong

  private def awaitDead(pid: Long, millis: Long = 5000): Boolean =
    val deadline = System.currentTimeMillis() + millis
    while alive(pid) && System.currentTimeMillis() < deadline do Thread.sleep(50)
    !alive(pid)

  private def elapsed[A](f: => A): (A, Long) =
    val t0 = System.nanoTime()
    val a = f
    (a, (System.nanoTime() - t0) / 1000000)

  test("a CLI that reads its stdin and answers: lines, exit code, and a heartbeat per line"):
    val (f, dir) = script("cat; echo done; exit 3")
    val beats = java.util.concurrent.atomic.AtomicInteger()
    val reg = java.util.concurrent.atomic.AtomicInteger()
    val unreg = java.util.concurrent.atomic.AtomicInteger()
    val hooks = CliProcess.Hooks(
      _ => { val _ = reg.incrementAndGet() },
      _ => { val _ = unreg.incrementAndGet() },
      () => { val _ = beats.incrementAndGet() }
    )
    val r = CliProcess.withHooks(hooks)(CliProcess.run(cli(f), dir, _ => (), CliProcess.RunStdin("a\nb\n"), CliProcess.RunTimeoutSeconds(10), CliProcess.RunHint("hint")))
    assertEquals(r.lines, List("a", "b", "done"))
    assertEquals(r.exit, 3)
    assertEquals((reg.get, unreg.get, beats.get), (1, 1, 3))

  test("hooks reach a thread the run forks (a parallel step)"):
    val (f, dir) = script("echo hi")
    val reg = java.util.concurrent.atomic.AtomicInteger()
    val hooks = CliProcess.Hooks(_ => { val _ = reg.incrementAndGet() }, _ => (), () => ())
    CliProcess.withHooks(hooks) {
      val _ = ox.par(
        List(
          () => CliProcess.run(cli(f), dir, _ => (), CliProcess.RunStdin(""), CliProcess.RunTimeoutSeconds(10), CliProcess.RunHint("hint")),
          () => CliProcess.run(cli(f), dir, _ => (), CliProcess.RunStdin(""), CliProcess.RunTimeoutSeconds(10), CliProcess.RunHint("hint"))
        )
      )
    }
    assertEquals(reg.get, 2)

  test("a missing executable still fails at start"):
    val dir = Files.createTempDirectory("hm-cliproc-")
    val e =
      intercept[java.io.IOException](CliProcess.run(List(com.histmersion.server.agents.ClaudeCode.Argument("hm-no-such-cli-xyz")), dir, _ => (), com.histmersion.server.agents.CliProcess.RunStdin(""), com.histmersion.server.agents.CliProcess.RunTimeoutSeconds(5), com.histmersion.server.agents.CliProcess.RunHint("install it")))
    assert(e.getMessage.contains("cannot run 'hm-no-such-cli-xyz'"), e.getMessage)

  test("#1650: a CLI that never reads stdin, given a 200 KB prompt, is killed at the timeout"):
    val (f, dir) = script(s"echo $$$$ > @DIR@/pid; sleep 300")
    val prompt = CliProcess.RunStdin("x" * 200000)
    val (res, ms) = elapsed(scala.util.Try(CliProcess.run(cli(f), dir, _ => (), prompt, CliProcess.RunTimeoutSeconds(2), CliProcess.RunHint("hint"))))
    assert(res.failed.toOption.exists(_.isInstanceOf[TimeoutException]), res)
    assert(ms < 12000, s"took $ms ms")
    assert(awaitDead(pidIn(dir.resolve("pid"))), "the CLI is still alive")

  test("a CLI that sleeps past its timeout is killed with its children"):
    val (f, dir) = script(s"sleep 300 & echo $$! > @DIR@/child; cat > /dev/null; sleep 300")
    val (res, ms) = elapsed(scala.util.Try(CliProcess.run(cli(f), dir, _ => (), CliProcess.RunStdin("hi"), CliProcess.RunTimeoutSeconds(2), CliProcess.RunHint("hint"))))
    assert(res.failed.toOption.exists(_.isInstanceOf[TimeoutException]), res)
    assert(ms < 12000, s"took $ms ms")
    assert(awaitDead(pidIn(dir.resolve("child"))), "the child is still alive")

  test("#1650: a grandchild holding stdout after the CLI exited does not hold the step; it is killed"):
    val (f, dir) = script(s"sleep 300 & echo $$! > @DIR@/child; echo answer; exit 0")
    val (r, ms) = elapsed(CliProcess.run(cli(f), dir, _ => (), CliProcess.RunStdin(""), CliProcess.RunTimeoutSeconds(60), CliProcess.RunHint("hint")))
    assertEquals(r.lines, List("answer"))
    assertEquals(r.exit, 0)
    assert(ms < CliProcess.DrainMillis + 6000, s"took $ms ms")
    assert(awaitDead(pidIn(dir.resolve("child"))), "the grandchild is still alive")

  test("a step cancelled mid-call (thread interrupt) kills the tree"):
    val (f, dir) = script(s"sleep 300 & echo $$! > @DIR@/child; sleep 300")
    val t = Thread.ofVirtual().start { () =>
      val _ = scala.util.Try(CliProcess.run(cli(f), dir, _ => (), CliProcess.RunStdin(""), CliProcess.RunTimeoutSeconds(120), CliProcess.RunHint("hint")))
    }
    val child = pidIn(dir.resolve("child"))
    t.interrupt()
    t.join(java.time.Duration.ofSeconds(15))
    assert(!t.isAlive, "the call did not return")
    assert(awaitDead(child), "the child is still alive")
