package com.histmersion
package util.database

import com.histmersion.domain.Creator
import com.histmersion.api.automation.Creation
import com.histmersion.util.json.RawJson

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

  test("a stored creator reads back as the creator it was written from, and an incomplete one as none"):
    val run = Creator.New.Automated(
      Creator.Plugin.Name("library"),
      Creator.Workflow.Name("enrich-source"),
      Creator.Agent.RunModels(Some(Creator.Definition.Name("enrich-notes"))),
      Creator.Surface.Internal
    )
    List(
      reviewer,
      run,
      Creator.code(Creator.Plugin.Name("engine"), Creator.Workflow.Name("drain")),
      Creator.New.Manual(Creator.Surface.Web),
      Creator.New.Unattributed(Creator.Surface.Mcp)
    ).foreach(c => assertEquals(StoredCreator.recorded(StoredCreator.column(c)), Some(c)))
    assertEquals(StoredCreator.recorded(StoredCreator.write(Creator.unrecorded)), None)
    assertEquals(StoredCreator.recorded(Jsonb.JsonText("""{"kind": "manual"}""")), None)
    assertEquals(StoredCreator.recorded(Jsonb.JsonText("""{"kind": "automated", "plugin": "Bad Name"}""")), None)

  test("the run's write shows a model agent with its one definition, or none, and never a provider or model"):
    def run(definition: Option[Creator.Definition.Name]) =
      Creator.New.Automated(
        Creator.Plugin.Name("library"),
        Creator.Workflow.Name("enrich-source"),
        Creator.Agent.RunModels(definition),
        Creator.Surface.Internal
      )
    val one = shown(run(Some(Creator.Definition.Name("enrich-notes"))).stored).flatMap(_.agent)
    assertEquals(one, Some(Creation.Agent(Creation.ByModel, Some(Creation.Definition("enrich-notes")))))
    assertEquals(shown(run(None).stored).flatMap(_.agent), Some(Creation.Agent(Creation.ByModel)))

  test("a bundle's carried creator is written as it came, and one that is absent or not a creator as unrecorded"):
    def json(text: String) = RawJson.parse(text).toOption
    val carried = StoredCreator.carried(StoredCreator.document(Some(StoredCreator.write(reviewer.stored))))
    assertEquals(StoredCreator.recorded(carried), Some(reviewer))
    assertEquals(StoredCreator.carried(None), StoredCreator.write(Creator.unrecorded))
    assertEquals(StoredCreator.carried(json("""{"x": 1}""")), StoredCreator.write(Creator.unrecorded))
    assertEquals(StoredCreator.carried(json("[1]")), StoredCreator.write(Creator.unrecorded))
