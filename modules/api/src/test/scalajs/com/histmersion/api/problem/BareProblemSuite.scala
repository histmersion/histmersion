package com.histmersion
package api
package problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

import util.problem.Problem
import util.problem.Problem.*
import json.NewtypeCodecs.problemCodec

/** In the browser a problem written bare is kept as it was, even a case's frozen sentence: the page links no column
  * reader ([[SentenceReaders]]), as the server sends every problem typed.
  */
class BareProblemSuite extends munit.FunSuite:

  test("a bare string reads as the text as it was, and writes back bare"):
    List(
      "interrupted" -> Stored.Text("interrupted"),
      "cancelled by Person" -> Stored.Text("cancelled by Person"),
      "converter down" -> Stored.Text("converter down"),
      "" -> Stored.Text("")
    ).foreach { (text, kept) =>
      val bare = s"\"$text\""
      val read = RawJson.decode[Problem](bare)
      assertEquals(read, Right(Stored.Unrecognised(kept)))
      assertEquals(read.map(_.asJson.noSpaces), Right(bare))
    }

  test("a typed problem still reads as its case"):
    val typed = (Problem.Run.Interrupted: Problem).asJson.noSpaces
    assertEquals(RawJson.decode[Problem](typed), Right(Problem.Run.Interrupted))
