package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.Creation
import com.histmersion.domain.Creator
import com.histmersion.util.database.Jsonb

class StoredCreatorSuite extends munit.FunSuite:

  private def shown(creator: Creator): Option[Queue.Creator] = Jsonb.read[Queue.Creator](StoredCreator.write(creator))

  private val skeptic = Creator.Agent.Model(
    Creator.Definition.Name("article-skeptic"),
    Creator.Provider.Name("anthropic"),
    Creator.LanguageModel.Name("claude-opus-5-5")
  )

  test("a person's item shows the surface it came through"):
    assertEquals(
      shown(Creator.Manual(Creator.Surface.Cli)),
      Some(Queue.Creator(Creation.Manual, surface = Some(Creation.Surface("cli"))))
    )
    Creator.Surface.values.foreach(s => assert(shown(Creator.Manual(s)).flatMap(_.surface).isDefined, s.toString))

  test("a workflow's item shows its plugin, workflow and agent: the model's three names, or code"):
    assertEquals(
      shown(
        Creator.New.Automated(Creator.Plugin.Name("research"), Creator.Workflow.Name("review-article"), skeptic).stored
      ),
      Some(
        Queue.Creator(
          Creation.Automated,
          plugin = Some(Creation.Plugin("research")),
          workflow = Some(Creation.Workflow("review-article")),
          agent = Some(
            Queue.Agent(
              Creation.ByModel,
              Some(Creation.Definition("article-skeptic")),
              Some(Creation.Provider("anthropic")),
              Some(Creation.Model("claude-opus-5-5"))
            )
          )
        )
      )
    )
    assertEquals(
      shown(Creator.code(Creator.Plugin.Name("library"), Creator.Workflow.Name("seed")).stored).flatMap(_.agent),
      Some(Queue.Agent(Creation.Code))
    )

  test("what the migration writes shows as it stands: a known workflow alone, or nothing but the job"):
    assertEquals(
      Jsonb.read[Queue.Creator](Jsonb.JsonText("""{"kind": "automated", "workflow": "review-article"}""")),
      Some(Queue.Creator(Creation.Automated, workflow = Some(Creation.Workflow("review-article"))))
    )
    assertEquals(
      Jsonb.read[Queue.Creator](Jsonb.JsonText("""{"job": "zz-missing-job", "kind": "unknown"}""")),
      Some(Queue.Creator(Creation.Unknown, job = Some(Creation.Job("zz-missing-job"))))
    )
    assertEquals(
      shown(Creator.Unknown(Creator.Facts.Unrecorded(Some(Creator.Facts.Job("zz-missing-job"))))),
      Some(Queue.Creator(Creation.Unknown, job = Some(Creation.Job("zz-missing-job"))))
    )

  test("an item from a surface that cannot carry identity yet shows as unattributed, with its surface"):
    assertEquals(
      shown(Creator.New.Unattributed(Creator.Surface.Mcp).stored),
      Some(Queue.Creator(Creation.Unattributed, surface = Some(Creation.Surface("mcp"))))
    )
    assertEquals(StoredCreator.actor(Creator.New.Unattributed(Creator.Surface.Rest)), "person")

  test("the actor a new item gets: the person for a person's item, the agents for a workflow's"):
    assertEquals(StoredCreator.actor(Creator.New.Manual(Creator.Surface.Web)), "person")
    assertEquals(
      StoredCreator.actor(Creator.code(Creator.Plugin.Name("engine"), Creator.Workflow.Name("drain"))),
      "agents"
    )
