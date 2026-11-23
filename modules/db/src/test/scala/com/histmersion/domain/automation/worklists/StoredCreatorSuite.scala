package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.Queue
import com.histmersion.domain.Creator
import com.histmersion.util.database.Jsonb
import com.histmersion.api.Provenance

class StoredCreatorSuite extends munit.FunSuite:

  private def shown(creator: Creator): Option[Provenance] = Jsonb.read[Provenance](StoredCreator.write(creator))

  private val skeptic = Creator.Agent.Model(
    Creator.Definition.Name("article-skeptic"),
    Creator.Provider.Name("anthropic"),
    Creator.LanguageModel.Name("claude-opus-5-5")
  )

  test("a person's item shows the surface it came through"):
    assertEquals(
      shown(Creator.Manual(Creator.Surface.Cli)),
      Some(Provenance(Provenance.Manual, surface = Some(Provenance.Surface("cli"))))
    )
    Creator.Surface.values.foreach(s => assert(shown(Creator.Manual(s)).flatMap(_.surface).isDefined, s.toString))

  test("a workflow's item shows its plugin, workflow and agent: the model's three names, or code"):
    assertEquals(
      shown(
        Creator.New.Automated(Creator.Plugin.Name("research"), Creator.Workflow.Name("review-article"), skeptic).stored
      ),
      Some(
        Provenance(
          Provenance.Automated,
          plugin = Some(Provenance.Plugin("research")),
          workflow = Some(Provenance.Workflow("review-article")),
          agent = Some(
            Provenance.Agent(
              Provenance.ByModel,
              Some(Provenance.Definition("article-skeptic")),
              Some(Provenance.Provider("anthropic")),
              Some(Provenance.Model("claude-opus-5-5"))
            )
          )
        )
      )
    )
    assertEquals(
      shown(Creator.code(Creator.Plugin.Name("library"), Creator.Workflow.Name("seed")).stored).flatMap(_.agent),
      Some(Provenance.Agent(Provenance.Code))
    )

  test("what the migration writes shows as it stands: a known workflow alone, or nothing but the job"):
    assertEquals(
      Jsonb.read[Provenance](Jsonb.JsonText("""{"kind": "automated", "workflow": "review-article"}""")),
      Some(Provenance(Provenance.Automated, workflow = Some(Provenance.Workflow("review-article"))))
    )
    assertEquals(
      Jsonb.read[Provenance](Jsonb.JsonText("""{"job": "zz-missing-job", "kind": "unknown"}""")),
      Some(Provenance(Provenance.Unknown, job = Some(Provenance.Job("zz-missing-job"))))
    )
    assertEquals(
      shown(Creator.Unknown(Creator.Facts.Unrecorded(Some(Creator.Facts.Job("zz-missing-job"))))),
      Some(Provenance(Provenance.Unknown, job = Some(Provenance.Job("zz-missing-job"))))
    )

  test("an item from a surface that cannot carry identity yet shows as unattributed, with its surface"):
    assertEquals(
      shown(Creator.New.Unattributed(Creator.Surface.Mcp).stored),
      Some(Provenance(Provenance.Unattributed, surface = Some(Provenance.Surface("mcp"))))
    )
    assertEquals(StoredCreator.actor(Creator.New.Unattributed(Creator.Surface.Rest)), "person")

  test("the actor a new item gets: the person for a person's item, the agents for a workflow's"):
    assertEquals(StoredCreator.actor(Creator.New.Manual(Creator.Surface.Web)), "person")
    assertEquals(
      StoredCreator.actor(Creator.code(Creator.Plugin.Name("engine"), Creator.Workflow.Name("drain"))),
      "agents"
    )
