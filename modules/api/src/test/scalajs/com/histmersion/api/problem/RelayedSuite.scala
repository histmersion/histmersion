package com.histmersion
package api
package problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

import util.problem.Problem
import util.problem.Problem.*
import util.problem.Text.sentence
import json.NewtypeCodecs.{list, problemCodec}

/** In the browser a problem is its sentence: a typed entry reads as a [[Problem.Relayed]] of its `text`, without the
  * groups' codecs ([[ProblemJson]]); what the page would write back is the relayed entry, which the server reads as the
  * same relayed problem (RenderSuite, on both platforms).
  */
class RelayedSuite extends munit.FunSuite:

  /** `Invalid.Missing` of `rationale` as the server writes it (TypedEntrySuite on the JVM checks the same text). */
  private val Missing =
    """{"group":"Invalid","case":"Missing","data":{"field":"rationale","at":[]},"text":"rationale is required"}"""

  private val relayed = Relayed(Sentence("rationale is required"))

  test("a typed entry reads as its sentence"):
    val read = RawJson.decode[Problem](Missing)
    assertEquals(read, Right(relayed))
    assertEquals(read.map(_.sentence), Right("rationale is required"))

  test("an entry of any group or case reads as its sentence, a case the page has never heard of too"):
    assertEquals(
      RawJson.decode[Problem]("""{"group":"Nope","case":"Gone","data":{"x":1},"text":"something went away"}"""),
      Right(Relayed(Sentence("something went away")))
    )

  test("a bare string reads as its text, kept as it was"):
    assertEquals(
      RawJson.decode[Problem]("\"rationale is required\"").map(_.sentence),
      Right("rationale is required")
    )

  test("in a record, problems read as their sentences"):
    assertEquals(
      RawJson.decode[List[Problem]](s"""[$Missing, "converter down"]""").map(_.map(_.sentence)),
      Right(List("rationale is required", "converter down"))
    )

  test("the page writes a relayed problem, and any other it holds, as the relayed entry of its sentence"):
    val written = """{"group":"Relayed","case":"Relayed","data":{},"text":"rationale is required"}"""
    assertEquals((relayed: Problem).asJson.noSpaces, written)
    assertEquals((Invalid.Missing.here(Field("rationale")): Problem).asJson.noSpaces, written)
    assertEquals(RawJson.decode[Problem](written), Right(relayed))
