package com.histmersion
package domain.catalogue.evidence

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.util.text.Anchor

/** Re-anchoring a quoted passage in another OCR run of the same text (TextQuoteSelector). */
class TextAnchorSuite extends munit.FunSuite:

  private val body =
    TextAnchor.Text("Preface.\n\nThe storm god Tarhunna ruled Hatti. Later the storm god\nof Arinna ruled beside him.")

  test("an exact quote is found where it is, whatever its old offset"):
    val a = TextAnchor
      .reanchor(body, TextAnchor.Quote("Tarhunna ruled Hatti"), None, None, Some(TextAnchor.ReanchorHint(3)))
      .get
    assertEquals(body.value.substring(a.start.value, a.end.value), "Tarhunna ruled Hatti")

  test("spacing is not text: a quote across another line break is found, and the offsets are the original text's"):
    val a = TextAnchor.reanchor(body, TextAnchor.Quote("the storm god of  Arinna"), None, None, None).get
    assertEquals(body.value.substring(a.start.value, a.end.value), "the storm god\nof Arinna")

  test("several occurrences: the prefix and suffix decide, then the old offset"):
    val byContext = TextAnchor
      .reanchor(
        body,
        TextAnchor.Quote("storm god"),
        Some(TextAnchor.Prefix("Later the ")),
        Some(TextAnchor.Suffix("\nof Arinna")),
        None
      )
      .get
    assertEquals(body.value.substring(byContext.start.value - 10, byContext.start.value), "Later the ")
    val byHint =
      TextAnchor.reanchor(body, TextAnchor.Quote("storm god"), None, None, Some(TextAnchor.ReanchorHint(60))).get
    assertEquals(byHint, byContext)
    val first =
      TextAnchor.reanchor(body, TextAnchor.Quote("storm god"), None, None, Some(TextAnchor.ReanchorHint(0))).get
    assert(first.start.value < byContext.start.value)

  test("a quote that is not there is not found — never guessed"):
    assertEquals(
      TextAnchor.reanchor(
        body,
        TextAnchor.Quote("Telipinu"),
        Some(TextAnchor.Prefix("the ")),
        None,
        Some(TextAnchor.ReanchorHint(10))
      ),
      None
    )
    assertEquals(TextAnchor.reanchor(body, TextAnchor.Quote(""), None, None, Some(TextAnchor.ReanchorHint(10))), None)

  test("the FTS chunk of a passage: the chunk holding that occurrence"):
    val text = TextAnchor.Text("# One\n\nalpha beta\n\n# Two\n\nalpha gamma")
    val second =
      TextAnchor.reanchor(text, TextAnchor.Quote("alpha"), Some(TextAnchor.Prefix("# Two\n\n")), None, None).get
    assertEquals(TextAnchor.chunkOf(text, second, TextAnchor.ChunkChars(4000)), Some(1))
    assertEquals(
      TextAnchor.chunkOf(
        text,
        Anchor(Anchor.Start(text.value.indexOf("alpha")), Anchor.End(text.value.indexOf("alpha") + 5)),
        TextAnchor.ChunkChars(4000)
      ),
      Some(0)
    )
    assertEquals(TextAnchor.chunkOf(text, second, TextAnchor.ChunkChars(0)), None)
