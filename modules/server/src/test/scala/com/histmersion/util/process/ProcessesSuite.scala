package com.histmersion
package util.process

import com.histmersion.api.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.storage.{ByteSource, Location, Storage}

import java.nio.file.Files
import com.histmersion.util.storage.Store

class ProcessesSuite extends munit.FunSuite:

  test("a process runs in its store with the variables added, its output read line by line"):
    val dir = Files.createTempDirectory("histmersion-process")
    val r = Processes()
      .run(
        ProcessLaunch(
          List(
            ProcessLaunch.Command("/bin/sh"),
            ProcessLaunch.Command("-c"),
            ProcessLaunch.Command("echo \"$GREETING\"; pwd; echo oops >&2; exit 3")
          ),
          workingStore = Some(Storage.at(Location.Scratch, dir)),
          environment = Map(ProcessLaunch.EnvironmentKey("GREETING") -> ProcessLaunch.EnvironmentValue("hello"))
        )
      )
      .fold(e => fail(e.sentence), identity)
    assertEquals(r.exitCode, 3)
    assertEquals(r.lines.head, "hello")
    assertEquals(java.nio.file.Path.of(r.lines(1).value).toRealPath(), dir.toRealPath())
    assertEquals(r.lines(2), "oops", "errors go with the output")

  test("output appended to a file of a store"):
    val dir = Files.createTempDirectory("histmersion-process")
    val logs = Storage.at(Location.Logs, dir)
    logs.put(Store.Key("child.log"), ByteSource.Content("before\n".getBytes)): Unit
    val p = Processes()
      .start(
        ProcessLaunch(
          List(ProcessLaunch.Command("/bin/sh"), ProcessLaunch.Command("-c"), ProcessLaunch.Command("echo after")),
          output = ProcessOutput.AppendTo(logs, Store.Key("child.log"))
        )
      )
      .fold(e => fail(e.sentence), identity)
    assertEquals(p.waitFor(), 0)
    assertEquals(logs.get(Store.Key("child.log")).map(_.text()), Some("before\nafter\n"))

  test("a program that is not there is refused, not thrown"):
    assert(
      Processes().run(ProcessLaunch(List(ProcessLaunch.Command("/nonexistent/histmersion-no-such-program")))).isLeft
    )

  test("in memory: nothing starts, each launch is recorded and answered"):
    val mem = Processes.InMemory(l =>
      ProcessResult(
        if l.command.head == ProcessLaunch.Command("ok") then ProcessResult.ExitCode(0) else ProcessResult.ExitCode(1),
        List(ProcessResult.Lines("out"))
      )
    )
    assertEquals(
      mem.run(ProcessLaunch(List(ProcessLaunch.Command("ok")))),
      Right(ProcessResult(ProcessResult.ExitCode(0), List(ProcessResult.Lines("out"))))
    )
    assertEquals(mem.run(ProcessLaunch(List(ProcessLaunch.Command("no")))).map(_.succeeded), Right(false))
    assertEquals(mem.launches.map(_.command.head), List("ok", "no"))
