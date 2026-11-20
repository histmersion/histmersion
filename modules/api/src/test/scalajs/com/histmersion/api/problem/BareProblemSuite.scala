package com.histmersion
package api
package problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import util.problem.Problem
import util.problem.Problem.*
import json.NewtypeCodecs.{list, problemCodec}

/** In the browser a problem written bare is outside text, kept as it came ([[Problem.Relayed]]), even a text a master
  * version stored in one of the shapes the JVM reads as a case: the page reads no stored shape ([[StoredShapes]] is the
  * JVM's and the database's), as the server sends every problem typed. A typed entry reads as its case on both
  * platforms (TypedEntrySuite).
  */
class BareProblemSuite extends munit.FunSuite:

  /** Texts in the master shapes, which the JVM reads as cases. */
  private val MasterShapes: List[String] = List(
    StoredShapes.Interrupted,
    StoredShapes.Drained,
    s"${StoredShapes.NotAsked}the index is over its daily allowance",
    "requested: paused on request",
    "allowance: paused before it started",
    "interrupted: the upload is no longer held by this process"
  )

  test("a bare string reads as the text as it came, and is written by that text"):
    List(
      "cancelled by Person" -> Sentence("cancelled by Person"),
      "converter down" -> Sentence("converter down"),
      "a \"quoted\" line\nand another" -> Sentence("a \"quoted\" line\nand another"),
      "" -> Sentence("")
    ).foreach { (text, kept) =>
      val bare = Json.fromString(text).noSpaces
      val read = RawJson.decode[Problem](bare)
      assertEquals(read, Right(Relayed(kept)))
      assertEquals(
        read.map(_.asJson.noSpaces),
        Right(s"""{"group":"Relayed","case":"Relayed","data":{},"text":$bare}""")
      )
    }

  test("a bare string in a master shape is outside text too, not the case the JVM reads it as"):
    MasterShapes.foreach { text =>
      RawJson.decode[Problem](Json.fromString(text).noSpaces) match
        case Right(Relayed(kept)) => assertEquals(s"$kept", text)
        case other                => fail(s"$text read as $other, not as the outside text it is")
      val onTheJvm = StoredShapes.read(text) match
        case _: Relayed | _: Stored.Unrecognised => false
        case _                                   => true
      assert(onTheJvm, s"$text is a shape the JVM reads as a case")
    }

  test("in a record, a typed entry reads as its case and a bare string as outside text"):
    val typed = """{"group":"Run","case":"Interrupted","data":{},"text":"interrupted by a restart"}"""
    assertEquals(
      RawJson.decode[List[Problem]](s"""[$typed, "interrupted"]"""),
      Right(List[Problem](Run.Interrupted, Relayed(Sentence("interrupted"))))
    )

  test("an entry of a case this page does not know reads as the server's sentence (E19: a tab open across an upgrade)"):
    val unknownCase = """{"group":"Conflict","case":"Invented","data":{"x":1},"text":"the server's own sentence"}"""
    val unknownGroup = """{"group":"Galaxy","case":"Far","data":{},"text":"another sentence"}"""
    val changedFields = """{"group":"Run","case":"Interrupted","data":{"extra":[1]},"text":"interrupted"}"""
    assertEquals(RawJson.decode[Problem](unknownCase), Right(Relayed(Sentence("the server's own sentence"))))
    assertEquals(RawJson.decode[Problem](unknownGroup), Right(Relayed(Sentence("another sentence"))))
    assertEquals(
      RawJson.decode[Problem]("""{"group":"Run","case":"Nope","data":{},"text":"x"}"""),
      Right(Relayed(Sentence("x")))
    )
    assert(RawJson.decode[Problem](changedFields).isRight)
    // the whole answer reads: the page shows the sentence, not "could not read the server's answer"
    RawJson.decode[Api.Error](s"""{"problems":[$unknownCase]}""") match
      case Right(e) =>
        assertEquals((e.problems.size, e.problems.head), (1, Relayed(Sentence("the server's own sentence"))))
      case Left(why) => fail(s"the answer did not read: $why")
