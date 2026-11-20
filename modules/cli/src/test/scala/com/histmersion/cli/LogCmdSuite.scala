package com.histmersion
package cli

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.log.{LogEvent, LogPage}

/** `histmersion log …`: every filter a flag, `--file` the file store, `--problem` alone the problem's trace. */
class LogCmdSuite extends munit.FunSuite:
  private val json = Cli.JsonOutput(false)

  test("each flag sets its filter; --file reads the file"):
    assertEquals(
      Parsing.parse("log", "--job", "j1", "--source", "smith2020", "--kind", "Written", "--since", "7d", "--file"),
      Right(
        LogCmd.Command.Log(
          LogPage.Filter(
            job = Some(LogEvent.Key("j1")),
            source = Some(LogEvent.Key("smith2020")),
            kind = Some(LogEvent.Kind("Written")),
            since = Some(LogEvent.At("7d")),
            from = Some(LogPage.From("file"))
          ),
          json
        )
      )
    )
    assertEquals(Parsing.parse("log"), Right(LogCmd.Command.Log(LogPage.Filter(), json)))

  test("--problem is the problem's id"):
    assertEquals(
      Parsing.parse("log", "--problem", "ab12"),
      Right(LogCmd.Command.Log(LogPage.Filter(problem = Some(LogEvent.ProblemId("ab12"))), json))
    )

  test("a failure names the trace the server answered it under, and how to see what happened"):
    assertEquals(
      Client.traceOf(Some(" 0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f ")),
      Some("0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f")
    )
    assertEquals(Client.traceOf(Some(" ")), None)
    assertEquals(Client.traceOf(None), None)
    assertEquals(
      Cli.Error.traceLine(LogEvent.Trace("t1")),
      "trace t1 — what happened: histmersion log --trace t1"
    )
