package com.histmersion
package cli

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.automation.{Goal, Queue}
import com.histmersion.api.research.OpenWork

class StateCmdSuite extends munit.FunSuite:
  private val json = Cli.JsonOutput.Text

  test("state: each flag sets its part of the scope; none asks for everything"):
    assertEquals(Parsing.parse("state"), Right(StateCmd.Command.State(OpenWork.Scope(), json)))
    assertEquals(
      Parsing.parse("state", "--who", "owner", "--goal", "bronze-age", "--kind", "acquisition", "--json"),
      Right(
        StateCmd.Command.State(
          OpenWork.Scope(
            Some(OpenWork.Terms.Who("owner")),
            Some(Goal.Slug("bronze-age")),
            Some(OpenWork.Terms.Kind("acquisition"))
          ),
          Cli.JsonOutput.Json
        )
      )
    )

  test("queue acquire why: the item's id"):
    assertEquals(
      Parsing.parse("queue", "acquire", "why", "42"),
      Right(StateCmd.Command.Why(Queue.QueueItem.Id(42), json))
    )
