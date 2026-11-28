package com.histmersion
package domain.catalogue.evidence

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.util.text.Anchor
import com.histmersion.lint.{tolerated, LintRule}

/** Re-anchoring a quoted passage in another OCR run of the same text (TextQuoteSelector). */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class TextAnchorSuite extends munit.FunSuite:

  private val body =
    Anchor.Text("Preface.\n\nThe storm god Tarhunna ruled Hatti. Later the storm god\nof Arinna ruled beside him.")

  test("an exact quote is found where it is, whatever its old offset"):
    val a = Anchor.reanchor(body, Anchor.Quote("Tarhunna ruled Hatti"), None, None, Some(Anchor.ReanchorHint(3))).get
    assertEquals(body.value.substring(a.start.value, a.end.value), "Tarhunna ruled Hatti")

  test("spacing is not text: a quote across another line break is found, and the offsets are the original text's"):
    val a = Anchor.reanchor(body, Anchor.Quote("the storm god of  Arinna"), None, None, None).get
    assertEquals(body.value.substring(a.start.value, a.end.value), "the storm god\nof Arinna")

  test("several occurrences: the prefix and suffix decide, then the old offset"):
    val byContext = Anchor
      .reanchor(
        body,
        Anchor.Quote("storm god"),
        Some(Anchor.Prefix("Later the ")),
        Some(Anchor.Suffix("\nof Arinna")),
        None
      )
      .get
    assertEquals(body.value.substring(byContext.start.value - 10, byContext.start.value), "Later the ")
    val byHint =
      Anchor.reanchor(body, Anchor.Quote("storm god"), None, None, Some(Anchor.ReanchorHint(60))).get
    assertEquals(byHint, byContext)
    val first =
      Anchor.reanchor(body, Anchor.Quote("storm god"), None, None, Some(Anchor.ReanchorHint(0))).get
    assert(first.start < byContext.start)

  test("a quote that is not there is not found — never guessed"):
    assertEquals(
      Anchor.reanchor(
        body,
        Anchor.Quote("Telipinu"),
        Some(Anchor.Prefix("the ")),
        None,
        Some(Anchor.ReanchorHint(10))
      ),
      None
    )
    assertEquals(Anchor.reanchor(body, Anchor.Quote(""), None, None, Some(Anchor.ReanchorHint(10))), None)

  test("the FTS chunk of a passage: the chunk holding that occurrence"):
    val text = Anchor.Text("# One\n\nalpha beta\n\n# Two\n\nalpha gamma")
    val second =
      Anchor.reanchor(text, Anchor.Quote("alpha"), Some(Anchor.Prefix("# Two\n\n")), None, None).get
    assertEquals(Anchor.chunkOf(text, second, Anchor.ChunkChars(4000)), Some(1))
    assertEquals(
      Anchor.chunkOf(
        text,
        Anchor(Anchor.Start(text.value.indexOf("alpha")), Anchor.End(text.value.indexOf("alpha") + 5)),
        Anchor.ChunkChars(4000)
      ),
      Some(0)
    )
    assertEquals(Anchor.chunkOf(text, second, Anchor.ChunkChars(0)), None)
