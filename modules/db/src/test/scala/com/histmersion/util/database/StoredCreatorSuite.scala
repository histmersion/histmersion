package com.histmersion
package util.database

import com.histmersion.domain.Creator
import com.histmersion.api.automation.Creation

class StoredCreatorSuite extends munit.FunSuite:

  private def shown(creator: Creator): Option[Creation] = Jsonb.read[Creation](StoredCreator.write(creator))

  private val skeptic = Creator.Agent.Model(
    Creator.Definition.Name("article-skeptic"),
    Creator.Provider.Name("anthropic"),
    Creator.LanguageModel.Name("claude-opus-5-5")
  )

  private val reviewer =
    Creator.New.Automated(
      Creator.Plugin.Name("research"),
      Creator.Workflow.Name("review-article"),
      skeptic,
      Creator.Surface.Mcp
    )

  test("a person's record shows the surface it came through"):
    assertEquals(
      shown(Creator.New.Manual(Creator.Surface.Cli).stored),
      Some(Creation(Creation.Manual, surface = Some(Creation.Surface("cli"))))
    )
    Creator.Surface.values.foreach(s =>
      assert(shown(Creator.New.Manual(s).stored).flatMap(_.surface).isDefined, s.toString)
    )

  test("a workflow's record shows its plugin, workflow, agent (the model's three names, or code) and surface"):
    assertEquals(
      shown(reviewer.stored),
      Some(
        Creation(
          Creation.Automated,
          surface = Some(Creation.Surface("mcp")),
          plugin = Some(Creation.Plugin("research")),
          workflow = Some(Creation.Workflow("review-article")),
          agent = Some(
            Creation.Agent(
              Creation.ByModel,
              Some(Creation.Definition("article-skeptic")),
              Some(Creation.Provider("anthropic")),
              Some(Creation.Model("claude-opus-5-5"))
            )
          )
        )
      )
    )
    val code = shown(Creator.code(Creator.Plugin.Name("library"), Creator.Workflow.Name("seed")).stored)
    assertEquals(code.flatMap(_.agent), Some(Creation.Agent(Creation.Code)))
    assertEquals(code.flatMap(_.surface), Some(Creation.Surface("internal")))

  test("what the migrations write shows as it stands: a known workflow alone, or nothing but the job"):
    assertEquals(
      Jsonb.read[Creation](Jsonb.JsonText("""{"kind": "automated", "workflow": "review-article"}""")),
      Some(Creation(Creation.Automated, workflow = Some(Creation.Workflow("review-article"))))
    )
    assertEquals(
      Jsonb.read[Creation](Jsonb.JsonText("""{"job": "zz-missing-job", "kind": "unknown"}""")),
      Some(Creation(Creation.Unknown, job = Some(Creation.Job("zz-missing-job"))))
    )
    assertEquals(
      shown(Creator.Unknown(Creator.Facts.Unrecorded(Some(Creator.Facts.Job("zz-missing-job"))))),
      Some(Creation(Creation.Unknown, job = Some(Creation.Job("zz-missing-job"))))
    )

  test("a record from a surface that cannot carry identity yet shows as unattributed, with its surface"):
    assertEquals(
      shown(Creator.New.Unattributed(Creator.Surface.Mcp).stored),
      Some(Creation(Creation.Unattributed, surface = Some(Creation.Surface("mcp"))))
    )
    assertEquals(StoredCreator.actor(Creator.New.Unattributed(Creator.Surface.Rest)), "person")

  test("the actor a new item gets: the person for a person's item, the agents for a workflow's"):
    assertEquals(StoredCreator.actor(Creator.New.Manual(Creator.Surface.Web)), "person")
    assertEquals(
      StoredCreator.actor(Creator.code(Creator.Plugin.Name("engine"), Creator.Workflow.Name("drain"))),
      "agents"
    )

  test("a record whose creator was not recorded shows as unknown, and a new one's column is its stored creator"):
    assertEquals(shown(Creator.unrecorded), Some(Creation(Creation.Unknown)))
    assertEquals(StoredCreator.column(reviewer), StoredCreator.write(reviewer.stored))
    assertEquals(
      Jsonb.read[Creation](Jsonb.JsonText("""{"kind": "manual"}""")),
      Some(Creation(Creation.Manual)),
      "a backfilled person's record: the surface unknown"
    )
