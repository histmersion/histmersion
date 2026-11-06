package com.histmersion.domain.catalogue.evidence

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.util.text.Anchor

/** Re-anchoring a quoted passage in another OCR run of the same text (TextQuoteSelector). */
class TextAnchorSuite extends munit.FunSuite:

  private val body = "Preface.\n\nThe storm god Tarhunna ruled Hatti. Later the storm god\nof Arinna ruled beside him."

  test("an exact quote is found where it is, whatever its old offset"):
    val a = TextAnchor.reanchor(body, com.histmersion.domain.catalogue.evidence.TextAnchor.Quote("Tarhunna ruled Hatti"), None, None, Some(com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorHint(3))).get
    assertEquals(body.substring(a.start, a.end), "Tarhunna ruled Hatti")

  test("spacing is not text: a quote across another line break is found, and the offsets are the original text's"):
    val a = TextAnchor.reanchor(body, com.histmersion.domain.catalogue.evidence.TextAnchor.Quote("the storm god of  Arinna"), None, None, None).get
    assertEquals(body.substring(a.start, a.end), "the storm god\nof Arinna")

  test("several occurrences: the prefix and suffix decide, then the old offset"):
    val byContext = TextAnchor.reanchor(body, com.histmersion.domain.catalogue.evidence.TextAnchor.Quote("storm god"), Some(com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix("Later the ")), Some(com.histmersion.domain.catalogue.evidence.TextAnchor.Suffix("\nof Arinna")), None).get
    assertEquals(body.substring(byContext.start - 10, byContext.start), "Later the ")
    val byHint = TextAnchor.reanchor(body, com.histmersion.domain.catalogue.evidence.TextAnchor.Quote("storm god"), None, None, Some(com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorHint(60))).get
    assertEquals(byHint, byContext)
    val first = TextAnchor.reanchor(body, com.histmersion.domain.catalogue.evidence.TextAnchor.Quote("storm god"), None, None, Some(com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorHint(0))).get
    assert(first.start < byContext.start)

  test("a quote that is not there is not found — never guessed"):
    assertEquals(TextAnchor.reanchor(body, com.histmersion.domain.catalogue.evidence.TextAnchor.Quote("Telipinu"), Some(com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix("the ")), None, Some(com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorHint(10))), None)
    assertEquals(TextAnchor.reanchor(body, com.histmersion.domain.catalogue.evidence.TextAnchor.Quote(""), None, None, Some(com.histmersion.domain.catalogue.evidence.TextAnchor.ReanchorHint(10))), None)

  test("the FTS chunk of a passage: the chunk holding that occurrence"):
    val text = "# One\n\nalpha beta\n\n# Two\n\nalpha gamma"
    val second = TextAnchor.reanchor(text, com.histmersion.domain.catalogue.evidence.TextAnchor.Quote("alpha"), Some(com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix("# Two\n\n")), None, None).get
    assertEquals(TextAnchor.chunkOf(text, second, com.histmersion.domain.catalogue.evidence.TextAnchor.ChunkChars(4000)), Some(1))
    assertEquals(TextAnchor.chunkOf(text, Anchor(text.indexOf("alpha"), text.indexOf("alpha") + 5), com.histmersion.domain.catalogue.evidence.TextAnchor.ChunkChars(4000)), Some(0))
    assertEquals(TextAnchor.chunkOf(text, second, com.histmersion.domain.catalogue.evidence.TextAnchor.ChunkChars(0)), None)
