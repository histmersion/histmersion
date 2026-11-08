package com.histmersion
package server.agents

import com.histmersion.domain.research.workflows.*

import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.domain.CompileArticle.SourceFact
import com.histmersion.api.research.DraftMode
import com.histmersion.api.automation.ScheduleKind
import io.circe.syntax.*
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title
import com.histmersion.domain.research.Slug
import com.histmersion.api.research.Article

/** The vocabularies live in `values` with no codec; their wire instances are api's `VocabularyCodecs`. A record the
  * server derives a codec for must find those — a vocabulary encoded any other way (by case name, as an object) would
  * change what a checkpointed run reads back.
  */
class VocabularyWireSuite extends munit.FunSuite:

  test("a checkpointed record spells a vocabulary as the wire does"):
    val found = CompileArticlePipeline.Found(Goal.Plans.ArticleDraft.Filter("f"), RowCount(1), List(SourceFact(Citekey("ck"), Title("T"), None, Source.Status.Superseded)), Nil)
    assert(found.asJson.noSpaces.contains("\"status\":\"superseded\""), found.asJson.noSpaces)

  test("a checkpointed snapshot spells its draft mode as the wire does"):
    val snap = CompileArticlePipeline.Snap(Slug("a"), DraftMode.Extend, Title("t"), Article.Kind.Wire("concept"), Article.Body(""), None, None, Nil, Nil)
    assert(snap.asJson.noSpaces.contains("\"mode\":\"extend\""), snap.asJson.noSpaces)

  test("goal links and schedules spell their kinds as the wire does"):
    assertEquals(Goal.LinkKind.QueueItem.asJson.noSpaces, "\"queue-item\"")
    assertEquals(ScheduleKind.Acquisition.asJson.noSpaces, "\"acquisition\"")
