package com.histmersion.util.process

import com.histmersion.util.storage.{Location, Storage, StoreKey}

import java.nio.file.Files

class ProcessesSuite extends munit.FunSuite:

  test("a process runs in its store with the variables added, its output read line by line"):
    val dir = Files.createTempDirectory("histmersion-process")
    val r = Processes()
      .run(
        ProcessLaunch(
          List("/bin/sh", "-c", "echo \"$GREETING\"; pwd; echo oops >&2; exit 3"),
          workingStore = Some(Storage.at(Location.Scratch, dir)),
          environment = Map("GREETING" -> "hello")
        )
      )
      .fold(fail(_), identity)
    assertEquals(r.exitCode, 3)
    assertEquals(r.lines.head, "hello")
    assertEquals(java.nio.file.Path.of(r.lines(1)).toRealPath(), dir.toRealPath())
    assertEquals(r.lines(2), "oops", "errors go with the output")

  test("output appended to a file of a store"):
    val dir = Files.createTempDirectory("histmersion-process")
    val logs = Storage.at(Location.Logs, dir)
    logs.put(StoreKey("child.log"), "before\n".getBytes): Unit
    val p = Processes()
      .start(ProcessLaunch(List("/bin/sh", "-c", "echo after"), output = ProcessOutput.AppendTo(logs, StoreKey("child.log"))))
      .fold(fail(_), identity)
    assertEquals(p.waitFor(), 0)
    assertEquals(logs.get(StoreKey("child.log")).map(_.text()), Some("before\nafter\n"))

  test("a program that is not there is refused, not thrown"):
    assert(Processes().run(ProcessLaunch(List("/nonexistent/histmersion-no-such-program"))).isLeft)

  test("in memory: nothing starts, each launch is recorded and answered"):
    val mem = Processes.InMemory(l => ProcessResult(if l.command.head == "ok" then 0 else 1, List("out")))
    assertEquals(mem.run(ProcessLaunch(List("ok"))), Right(ProcessResult(0, List("out"))))
    assertEquals(mem.run(ProcessLaunch(List("no"))).map(_.succeeded), Right(false))
    assertEquals(mem.launches.map(_.command.head), List("ok", "no"))
