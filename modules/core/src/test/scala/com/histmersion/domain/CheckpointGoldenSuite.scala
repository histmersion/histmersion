package com.histmersion
package domain
import com.histmersion.util.json.RawJson.*


/** Stored agent runs keep loading: every golden line (written from the codecs runs were stored with) decodes and
  * encodes back to the same bytes, and today's values encode to exactly those lines.
  */
class CheckpointGoldenSuite extends munit.FunSuite:

  private def lines(file: String): List[String] =
    scala.io.Source
      .fromResource(s"golden/checkpoints/$file")(using scala.io.Codec.UTF8)
      .getLines()
      .filter(_.nonEmpty)
      .toList

  CheckpointGolden.all.foreach { g =>
    test(s"${g.file}: every stored line decodes and re-encodes byte for byte"):
      val stored = lines(g.file)
      assert(stored.nonEmpty)
      stored.foreach { line =>
        val again =
          decode(line)(using g.decoder).fold(e => fail(s"${g.file}: $e\n$line"), _.asJson(using g.encoder).noSpaces)
        assertEquals(again, line)
      }

    test(s"${g.file}: the samples encode to the stored lines"):
      assertEquals(g.values.map(_.asJson(using g.encoder).noSpaces), lines(g.file))
  }
