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

  test("--problem alone asks for the problem's trace; with another filter it is one filter among them"):
    assertEquals(LogCmd.problemAlone(LogPage.Filter(problem = Some(LogEvent.Key("ab12")))), Some("ab12"))
    assertEquals(
      LogCmd.problemAlone(LogPage.Filter(problem = Some(LogEvent.Key("ab12")), job = Some(LogEvent.Key("j1")))),
      None
    )
    assertEquals(LogCmd.problemAlone(LogPage.Filter()), None)
