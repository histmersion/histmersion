package com.histmersion.core

class PassageFinderSuite extends munit.FunSuite:

  private val Ck = "zz2020_body"

  // three sections as the Chunker stores them: heading lines removed, breadcrumbs kept
  private val Segs = List(
    Chunk(0, None, "Preamble line about nothing."),
    Chunk(1, Some("Theseus"), "Theseus was buried on Skyros.\nCimon brought the bones back to Athens."),
    Chunk(2, Some("Theseus > Cult"), "The Theseia honoured him.\n\nAt Ḫattuša nothing like it is known; Güterbock said so.")
  )

  private def find(q: String, regex: Boolean = false, limit: Int = 10, context: Int = 600,
      segs: List[Chunk] = Segs, max: Int = 10000, timeoutMs: Long = 2000): PassageResult =
    PassageFinder.find(Ck, segs, q, regex, limit, context, max, timeoutMs).fold(m => fail(m), identity)

  private def err(q: String, regex: Boolean): String =
    PassageFinder.find(Ck, Segs, q, regex, 10, 600, 10000, 2000).fold(identity, r => fail(s"expected an error, got $r"))

  /** The body slice `get_source {offset, chars}` would return. */
  private def slice(segs: List[Chunk], offset: Int, chars: Int): String =
    val b = PassageFinder.body(segs)
    b.substring(offset, (offset + chars).min(b.length))

  test("the body is the chunks joined by a blank line — the text get_source slices"):
    assertEquals(PassageFinder.body(Segs).take(40), "Preamble line about nothing.\n\nTheseus wa")

  test("a literal match is case-insensitive and reports the offset get_source reads from"):
    val r = find("skyros")
    assertEquals(r.matches, 1)
    assertEquals(r.total, 1)
    val p = r.passages.head
    assertEquals(slice(Segs, p.offset, p.text.length), p.text, "get_source at the offset starts with the passage")
    val m = p.matches.head
    assertEquals(p.text.substring(m.start, m.end), "Skyros")
    assertEquals(p.heading, Some("Theseus"))
    assertEquals(r.bodyChars, PassageFinder.body(Segs).length)

  test("a literal query is not a regex and not stemmed"):
    assertEquals(find("Skyr.s").matches, 0)
    assertEquals(find("bone").matches, 1) // substring of "bones": literal, not word-stemmed
    assertEquals(find("(nothing").matches, 0) // regex metacharacters are plain text
    assertEquals(find("nothing.").matches, 1)

  test("a phrase matches across a line break and any run of whitespace"):
    val r = find("Skyros.   Cimon")
    assertEquals(r.matches, 1)
    val p = r.passages.head
    assertEquals(p.text.substring(p.matches.head.start, p.matches.head.end), "Skyros.\nCimon")

  test("accents are optional: a plain query finds the transliteration, an accented one the plain text"):
    val r = find("hattusa")
    assertEquals(r.matches, 1)
    val p = r.passages.head
    assertEquals(p.text.substring(p.matches.head.start, p.matches.head.end), "Ḫattuša")
    assertEquals(find("Guterbock").matches, 1)
    assertEquals(find("Thêseus").matches, 1)

  test("decomposed combining marks belong to the match; folded offsets map back to the original"):
    val segs = List(Chunk(0, None, "x Güterbock y"))
    val p    = find("guterbock", segs = segs).passages.head
    assertEquals(p.text.substring(p.matches.head.start, p.matches.head.end), "Güterbock")
    val e = find("u", segs = List(Chunk(0, None, "aüb"))).passages.head
    assertEquals(e.text.substring(e.matches.head.start, e.matches.head.end), "ü", "the mark rides with its letter")

  test("OCR typography: curly quotes, dashes, ligatures and soft hyphens match their plain forms"):
    val segs = List(Chunk(0, None, "Theseus’ bones — the ﬁrst cult, a Bronze Age­ hero"))
    assertEquals(find("Theseus' bones - the first", segs = segs).matches, 1)
    assertEquals(find("Age hero", segs = segs).matches, 1)

  test("regex: case-insensitive, MULTILINE anchors, and an empty match is not a hit"):
    assertEquals(find("^cimon", regex = true).matches, 1)
    assertEquals(find("^the", regex = true).matches, 2) // "Theseus …" and "The Theseia" start lines; "the bones" does not
    assertEquals(find("these(us|ia)|cimon", regex = true).matches, 3)
    assertEquals(find("x*", regex = true).matches, 0)

  test("a bad regex is a clear message, never an exception"):
    val m = err("(unclosed", regex = true)
    assert(m.startsWith("not a valid regex: Unclosed group"), m)
    assert(m.contains("leave regex off"), m)
    assertEquals(PassageFinder.find(Ck, Segs, "(unclosed", false, 10, 600, 100, 2000).map(_.matches), Right(0))

  test("an empty or blank query is refused"):
    assert(err("   ", regex = false).contains("empty"))

  test("a pathological regex is refused when it runs past the timeout"):
    // Java memoises the textbook (a+)+b; nested greedy .* still backtracks for seconds on 28 chars
    val segs = List(Chunk(0, None, "a" * 28 + "!"))
    val r = PassageFinder.find(Ck, segs, "(.*a){12}x", true, 10, 600, 10000, 50)
    assert(r.left.exists(_.contains("ran longer than 50 ms")), r)

  test("context bounds: whole lines when a line break is in reach, else whole words, never past the budget"):
    val long  = (1 to 400).map(i => s"w$i").mkString(" ") // one long line, no breaks
    val segs  = List(Chunk(0, None, long))
    val p     = find("w200", segs = segs, context = 100).passages.head
    assert(p.text.length <= 100 + 4, p.text.length)
    assert(p.text.split(' ').forall(_.matches("""w\d+""")), p.text) // cut at word boundaries
    assert(p.text.contains("w200"))
    // with a line break inside the budget the passage starts at the line
    val q = find("bones", context = 60).passages.head
    assertEquals(q.text, "Cimon brought the bones back to Athens.")
    assertEquals(slice(Segs, q.offset, q.text.length), q.text)
    // context 0: just the match
    val z = find("Skyros", context = 0).passages.head
    assertEquals(z.text, "Skyros")

  test("passages never cross into each other and dense matches merge instead of repeating text"):
    val body = (1 to 50).map(i => s"Line $i mentions Skyros once.").mkString("\n")
    val segs = List(Chunk(0, None, body))
    val r    = find("skyros", segs = segs, context = 120, limit = 50)
    assertEquals(r.matches, 50)
    val ps = r.passages
    ps.zip(ps.drop(1)).foreach((a, b) => assert(a.offset + a.text.length <= b.offset, (a, b)))
    assertEquals(ps.map(_.matches.size).sum, 50)
    ps.foreach(p => assertEquals(slice(segs, p.offset, p.text.length), p.text))

  test("heading attribution: the chunk's breadcrumb, none before the first heading"):
    assertEquals(find("Preamble").passages.head.heading, None)
    assertEquals(find("Theseia").passages.head.heading, Some("Theseus > Cult"))
    assertEquals(find("Güterbock").passages.head.heading, Some("Theseus > Cult"))

  test("an unchunked body (raw Markdown) takes the nearest heading line above the match"):
    val raw = List(Chunk(0, None, "intro\n\n# Part One\n\nalpha\n\n## Sub ##\n\nbeta"))
    assertEquals(find("alpha", segs = raw).passages.head.heading, Some("Part One"))
    assertEquals(find("beta", segs = raw).passages.head.heading, Some("Sub"))
    assertEquals(find("intro", segs = raw).passages.head.heading, None)

  test("limit truncates the passages shown; the total and the render say how many more"):
    val body = (1 to 30).map(i => s"Paragraph $i names Skyros.").mkString("\n\n")
    val r    = find("skyros", segs = List(Chunk(0, Some("H"), body)), context = 10, limit = 3)
    assertEquals((r.matches, r.total, r.passages.size), (30, 30, 3))
    val md = PassagesMarkdown.render(r)
    assert(md.contains("_27 more — raise limit or narrow the query._"), md)
    assert(md.contains("30 matches in 30 passages · showing 3"), md)

  test("matches are counted up to the cap, then the result says the count stopped"):
    val body = List.fill(20)("Skyros").mkString(" ")
    val r    = find("skyros", segs = List(Chunk(0, None, body)), max = 5, context = 0)
    assertEquals((r.matches, r.capped), (5, true))
    assert(PassagesMarkdown.render(r).contains("5+ matches (counting stopped)"))

  test("an empty body or no match renders a one-line answer"):
    val r = find("nowhere")
    assertEquals(r.passages, Nil)
    assertEquals(PassagesMarkdown.render(r), s"""_No match for "nowhere" in `$Ck` (${r.bodyChars} chars searched)._""")
    assertEquals(find("x", segs = Nil).bodyChars, 0)

  test("the render bolds every match and heads each passage with its offset and heading"):
    val r  = find("theseus", context = 2000)
    val md = PassagesMarkdown.render(r)
    assert(md.startsWith("## Passages: \"theseus\" in `zz2020_body`"), md)
    assert(md.contains(s"### 1 · offset ${r.passages.head.offset} · § Theseus"), md)
    assert(md.contains("**Theseus** was buried"), md)
    val both = PassagesMarkdown.render(find("these(us|ia)", regex = true, context = 3000))
    assert(both.contains("**Theseus** was buried") && both.contains("The **Theseia**"), both)
    assert(both.startsWith("## Passages: /these(us|ia)/ in"), both)

  test("marked tolerates spans at the edges and adjacent spans"):
    val p = PassageHit(0, None, "abcabc", List(MatchSpan(3, 6), MatchSpan(0, 3)))
    assertEquals(PassagesMarkdown.marked(p), "**abc****abc**")

  test("the configured limits clamp a caller's limit and context; absent means the default"):
    val l = PassageLimits(limit = 10, maxLimit = 50, context = 600, maxContext = 3000, maxMatches = 10000, regexTimeoutMs = 2000)
    assertEquals(List(None, Some(0), Some(-3), Some(7), Some(500)).map(l.limitOf), List(10, 10, 10, 7, 50))
    assertEquals(List(None, Some(-1), Some(0), Some(200), Some(9000)).map(l.contextOf), List(600, 600, 0, 200, 3000))
    assertEquals(AppConfig.load().limits.passages, l, "reference.conf carries these defaults")
