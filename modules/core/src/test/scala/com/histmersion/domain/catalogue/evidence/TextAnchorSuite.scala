package com.histmersion.domain.catalogue.evidence

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.util.text.Anchor

/** Re-anchoring a quoted passage in another OCR run of the same text (TextQuoteSelector). */
class TextAnchorSuite extends munit.FunSuite:

  private val body = "Preface.\n\nThe storm god Tarhunna ruled Hatti. Later the storm god\nof Arinna ruled beside him."

  test("an exact quote is found where it is, whatever its old offset"):
    val a = TextAnchor.reanchor(body, "Tarhunna ruled Hatti", None, None, Some(3)).get
    assertEquals(body.substring(a.start, a.end), "Tarhunna ruled Hatti")

  test("spacing is not text: a quote across another line break is found, and the offsets are the original text's"):
    val a = TextAnchor.reanchor(body, "the storm god of  Arinna", None, None, None).get
    assertEquals(body.substring(a.start, a.end), "the storm god\nof Arinna")

  test("several occurrences: the prefix and suffix decide, then the old offset"):
    val byContext = TextAnchor.reanchor(body, "storm god", Some("Later the "), Some("\nof Arinna"), None).get
    assertEquals(body.substring(byContext.start - 10, byContext.start), "Later the ")
    val byHint = TextAnchor.reanchor(body, "storm god", None, None, Some(60)).get
    assertEquals(byHint, byContext)
    val first = TextAnchor.reanchor(body, "storm god", None, None, Some(0)).get
    assert(first.start < byContext.start)

  test("a quote that is not there is not found — never guessed"):
    assertEquals(TextAnchor.reanchor(body, "Telipinu", Some("the "), None, Some(10)), None)
    assertEquals(TextAnchor.reanchor(body, "", None, None, Some(10)), None)

  test("the FTS chunk of a passage: the chunk holding that occurrence"):
    val text = "# One\n\nalpha beta\n\n# Two\n\nalpha gamma"
    val second = TextAnchor.reanchor(text, "alpha", Some("# Two\n\n"), None, None).get
    assertEquals(TextAnchor.chunkOf(text, second, 4000), Some(1))
    assertEquals(TextAnchor.chunkOf(text, Anchor(text.indexOf("alpha"), text.indexOf("alpha") + 5), 4000), Some(0))
    assertEquals(TextAnchor.chunkOf(text, second, 0), None)
