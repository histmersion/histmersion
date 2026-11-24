package com.histmersion
package api.automation

class CreationLineSuite extends munit.FunSuite:
  private val at = Creation.At("2026-11-24T10:00:00Z")
  private val agent =
    Creation.Agent(
      Creation.ByModel,
      Some(Creation.Definition("drafter")),
      Some(Creation.Provider("anthropic")),
      Some(Creation.Model("claude"))
    )

  test("nothing recorded reads as no line"):
    assertEquals(Creation.line(None, None), None)

  test("a person on the web page, with the time"):
    val c = Creation(Creation.Manual, Some(Creation.Surface("web")))
    assertEquals(Creation.line(Some(c), Some(at)), Some("Created by a person via the web page at 2026-11-24T10:00:00Z"))

  test("an agent names its definition, provider and model, its workflow and its plugin"):
    val c = Creation(
      Creation.Automated,
      Some(Creation.Surface("internal")),
      Some(Creation.Plugin("in-tree")),
      Some(Creation.Workflow("review-article")),
      Some(agent)
    )
    assertEquals(
      Creation.line(Some(c), None),
      Some(
        "Created by the agent drafter (anthropic claude) in workflow review-article of in-tree via the server itself"
      )
    )

  test("a run's write without one step's answer names the run's definition only"):
    val c = Creation(
      Creation.Automated,
      None,
      None,
      None,
      Some(Creation.Agent(Creation.ByModel, Some(Creation.Definition("drafter"))))
    )
    assertEquals(c.who, "a model of the agent drafter")

  test("code, an unnamed caller and an unrecorded creator"):
    assertEquals(Creation(Creation.Automated, agent = Some(Creation.Agent(Creation.Code))).who, "code")
    assertEquals(Creation(Creation.Unattributed, Some(Creation.Surface("rest"))).who, "an unnamed caller")
    assertEquals(Creation(Creation.Unknown).who, "someone not recorded")

  test("only the time is known"):
    assertEquals(Creation.line(None, Some(at)), Some("Created at 2026-11-24T10:00:00Z"))
