package com.histmersion
package api
package problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import util.problem.Problem
import util.problem.Problem.*
import Column.stored
import Answer.entry
import json.NewtypeCodecs.problemCodec

/** A problem in a wire record on the JVM keeps its case both ways: the server, the CLI and the MCP tools read the typed
  * entry back to the case it was written from. The browser reads the sentence alone (RelayedSuite, Scala.js).
  */
class TypedEntrySuite extends munit.FunSuite:

  examples.foreach { p =>
    test(s"${p.group.productPrefix}.${p.productPrefix}: inside a record, typed with its sentence, and read back"):
      val written = (p: Problem).asJson
      p match
        case u: Stored.Unrecognised => assertEquals(written, Json.fromString(u.stored))
        case _                      => assertEquals(written, p.entry.asJson)
      assertEquals(RawJson.decode[Problem](written.noSpaces), Right(p))
  }

  test("a problem inside a record keeps the type of the problems it carries"):
    val copy: Problem = NotFound.Copy(
      Raw("10.1/x"),
      Vector(NotFound.Answer(NotFound.Fetcher("core"), NotFound.Kind.Refused, Problem.Run.Interrupted))
    )
    val written = copy.asJson.noSpaces
    assert(written.contains("\"why\":{\"group\":\"Run\",\"case\":\"Interrupted\""), written)
    assertEquals(RawJson.decode[Problem](written), Right(copy))

  test("an entry of a case this side does not know is no problem"):
    assert(RawJson.decode[Problem]("""{"group":"Run","case":"Nope","data":{},"text":"x"}""").isLeft)

  test("the typed entry RelayedSuite reads in the browser is the one the server writes"):
    // `Invalid.Missing` of `rationale` as the server writes it; RelayedSuite (Scala.js) holds the same text
    val missing =
      """{"group":"Invalid","case":"Missing","data":{"field":"rationale","at":[]},"text":"rationale is required"}"""
    assertEquals((Invalid.Missing.here(Field("rationale")): Problem).asJson.noSpaces, missing)
