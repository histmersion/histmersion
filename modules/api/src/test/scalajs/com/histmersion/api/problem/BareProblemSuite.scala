package com.histmersion
package api
package problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

import util.problem.Problem
import util.problem.Problem.*
import json.NewtypeCodecs.problemCodec

/** In the browser a problem written bare is its text as it was, even a master shape: the page reads no stored shape
  * ([[StoredShapes]] is the JVM's), as the server sends every problem typed. The page writes a problem by its sentence.
  */
class BareProblemSuite extends munit.FunSuite:

  test("a bare string reads as the text as it was, and is written by that text"):
    List(
      "interrupted" -> Sentence("interrupted"),
      "cancelled by Person" -> Sentence("cancelled by Person"),
      "converter down" -> Sentence("converter down"),
      "" -> Sentence("")
    ).foreach { (text, kept) =>
      val bare = s"\"$text\""
      val read = RawJson.decode[Problem](bare)
      assertEquals(read, Right(Relayed(kept)))
      assertEquals(
        read.map(_.asJson.noSpaces),
        Right(s"""{"group":"Relayed","case":"Relayed","data":{},"text":$bare}""")
      )
    }

  test("a typed problem reads as its sentence (RelayedSuite)"):
    val typed = """{"group":"Run","case":"Interrupted","data":{},"text":"interrupted by a restart"}"""
    assertEquals(RawJson.decode[Problem](typed), Right(Relayed(Sentence("interrupted by a restart"))))
