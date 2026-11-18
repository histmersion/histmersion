package com.histmersion
package api
package problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import util.problem.Problem
import util.problem.Problem.*
import Answer.entry
import json.NewtypeCodecs.{list, problemCodec}

/** A problem in a wire record keeps its case both ways, on the JVM (the server, the CLI, the MCP tools) and in the
  * browser alike: one codec writes the typed entry and reads it back to the case it was written from, a problem nested
  * in another's fields too. Only a bare string reads differently by platform (BareProblemSuite on Scala.js,
  * ReadBackSuite on the JVM).
  */
class TypedEntrySuite extends munit.FunSuite:

  examples.foreach { p =>
    test(s"${p.group.productPrefix}.${p.productPrefix}: inside a record, typed with its sentence, and read back"):
      val written = (p: Problem).asJson
      assertEquals(written, p.entry.asJson)
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

  test("the typed entry is written the same on every platform, and read back as its case"):
    // `Invalid.Missing` of `rationale` as the server writes it, and the browser too
    val missing =
      """{"group":"Invalid","case":"Missing","data":{"field":"rationale","at":[]},"text":"rationale is required"}"""
    assertEquals((Invalid.Missing.here(Field("rationale")): Problem).asJson.noSpaces, missing)
    assertEquals(RawJson.decode[Problem](missing), Right(Invalid.Missing.here(Field("rationale"))))

  test("in a record, problems read as their cases, a problem one carries keeps its case"):
    val nested: Problem = Upstream.CallFailed(
      Upstream.Name("hello-ai"),
      Problem.Run.Interrupted
    )
    val record = List[Problem](Invalid.Missing.here(Field("rationale")), nested)
    assertEquals(RawJson.decode[List[Problem]](record.asJson.noSpaces), Right(record))
