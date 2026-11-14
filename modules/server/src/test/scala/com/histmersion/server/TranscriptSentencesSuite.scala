package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import com.histmersion.testsupport.TypeNamesShown

/** The problem sentences the oracle transcripts recorded name types as a reader does, never by their code names. */
class TranscriptSentencesSuite extends munit.FunSuite:

  private def transcripts: Vector[Path] =
    Files
      .list(Path.of("modules"))
      .iterator
      .asScala
      .flatMap(m => Vector(m.resolve("src/test/resources/oracle")).filter(Files.isDirectory(_)))
      .flatMap { root =>
        val walk = Files.walk(root)
        try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".txt")).toVector
        finally walk.close()
      }
      .toVector
      .sortBy(_.toString)

  test("the transcripts are found"):
    assert(transcripts.size > 20, transcripts)

  test("no problem sentence in a transcript shows a type's code name"):
    val shown = for
      t <- transcripts
      s <- TypeNamesShown.sentencesOf(Files.readString(t, UTF_8))
      name <- TypeNamesShown.in(s)
    yield s"$t: $name in: $s"
    assertEquals(shown.distinct, Vector.empty)
