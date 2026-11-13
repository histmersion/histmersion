package com.histmersion
package util.json
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given


import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.PlanGoal.ProposedWork

/** Rows stored when ids were plain text still load, whatever the id looks like. */
class StoredIdCodecsSuite extends munit.FunSuite:

  test("a stored proposal with an id that is not a citekey decodes"):
    val row =
      """{"kind":"edge","citekey":"A","status":null,"edgeKind":"challenges","target":"b","evidence":"e","quote":"q"}"""
    val p = RawJson.decode[Proposal](row).fold(e => fail(e), identity)
    assertEquals(p.citekey.value, "A")
    assertEquals(p.target.map(_.value), Some("b"))

  test("a stored proposed work with a DOI written as a link decodes"):
    val w =
      RawJson.decode[ProposedWork]("""{"doi":"https://doi.org/10.1/x","why":"w"}""").fold(e => fail(e), identity)
    assertEquals(w.doi.value, "https://doi.org/10.1/x")
