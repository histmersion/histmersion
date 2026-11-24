package com.histmersion
package domain

import chimp.server.ToolResult
import com.histmersion.api.Api
import com.histmersion.api.automation.Creation
import com.histmersion.api.library.Source

class CreatorControllersSuite extends munit.FunSuite:

  private val agent = Creation.By(
    Some(Creation.Plugin("research-workspace")),
    Some(Creation.Workflow("review-article")),
    Some(Creation.Definition("article-skeptic")),
    Some(Creation.Provider("anthropic")),
    Some(Creation.Model("claude-opus-5-5"))
  )

  private val skepticAgent = Creator.Agent.Model(
    Creator.Definition.Name("article-skeptic"),
    Creator.Provider.Name("anthropic"),
    Creator.LanguageModel.Name("claude-opus-5-5")
  )

  private val skeptic = Creator.New.Automated(
    Creator.Plugin.Name("research-workspace"),
    Creator.Workflow.Name("review-article"),
    skepticAgent,
    Creator.Surface.Cli
  )

  private def seen(in: Creation.Requested): Either[Api.Error, Creator.New] =
    CreatorControllers.rest(in)(Right(summon[Creator.New]))

  test("a REST write that names nothing is unattributed: the script, the page and the CLI look alike"):
    assertEquals(seen(Source.Patch.Request()), Right(Creator.New.Unattributed(Creator.Surface.Rest)))

  test("a REST write that says how it came and names no agent is the person's, by that surface"):
    assertEquals(seen(Source.Patch.Request(via = Some(Creation.Web))), Right(Creator.New.Manual(Creator.Surface.Web)))
    assertEquals(seen(Source.Patch.Request(via = Some(Creation.Cli))), Right(Creator.New.Manual(Creator.Surface.Cli)))
    assertEquals(seen(Source.Patch.Request(via = Some(Creation.Rest))), Right(Creator.New.Manual(Creator.Surface.Rest)))

  test("an agent through the CLI is automated with its three names and the CLI's surface"):
    assertEquals(seen(Source.Patch.Request(createdBy = Some(agent), via = Some(Creation.Cli))), Right(skeptic))

  test("an unknown surface and half an agent are refused before the write"):
    assert(seen(Source.Patch.Request(via = Some(Creation.Via("mcp")))).isLeft)
    assert(seen(Source.Patch.Request(createdBy = Some(Creation.By(Some(Creation.Plugin("p")))))).isLeft)
    assert(
      seen(
        Source.Patch.Request(createdBy =
          Some(Creation.By(Some(Creation.Plugin("p")), Some(Creation.Workflow("w")), Some(Creation.Definition("d"))))
        )
      ).isLeft
    )

  test("the refused write never runs its body"):
    var ran = false
    CreatorControllers.rest(Source.Patch.Request(via = Some(Creation.Via("fax")))) { ran = true; Right(()) }
    assert(!ran)

  test("an MCP call is unattributed unless the agent names itself, and then automated over the MCP"):
    assertEquals(CreatorControllers(None, None).overMcp, Right(Creator.New.Unattributed(Creator.Surface.Mcp)))
    assertEquals(
      CreatorControllers(Some(agent), None).overMcp.map(_.stored),
      Right(
        Creator.Automated(
          Creator.Part.Known(Creator.Plugin.Name("research-workspace")),
          Creator.Part.Known(Creator.Workflow.Name("review-article")),
          Creator.Part.Known(skepticAgent),
          Creator.Part.Known(Creator.Surface.Mcp)
        )
      )
    )

  test("an MCP tool given a malformed name answers with the refusal"):
    val refused =
      CreatorControllers.mcp(Source.Patch.Request(createdBy = Some(Creation.By(Some(Creation.Plugin("Bad Name"))))))(
        ToolResult.text("written")
      )
    assert(refused.isError)
