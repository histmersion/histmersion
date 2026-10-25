package com.histmersion.server.agents

import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.domain.CompileArticle.SourceFact
import com.histmersion.values.SourceStatus
import io.circe.syntax.*

/** The vocabularies live in `values` with no codec; their wire instances are api's `VocabularyCodecs`. A
  * record the server derives a codec for must find those — a vocabulary encoded any other way (by case
  * name, as an object) would change what a checkpointed run reads back. */
class VocabularyWireSuite extends munit.FunSuite:

  test("a checkpointed record spells a vocabulary as the wire does"):
    val found = CompileArticlePipeline.Found("f", 1, List(SourceFact("ck", "T", None, SourceStatus.Superseded)), Nil)
    assert(found.asJson.noSpaces.contains("\"status\":\"superseded\""), found.asJson.noSpaces)
