package com.histmersion
package domain

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.ocr.{Chunk, Chunker}

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given
import com.histmersion.api.catalogue.MatchSpan
import com.histmersion.api.library.{PassageHit, PassageResult, PassagesMarkdown}
import com.histmersion.util.time.Timer
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PassageFinderSuite extends munit.FunSuite:

  private given Timer = new Timer {
    def start(): Timer.Measure = new Timer.Measure {
      def elapsed: scala.concurrent.duration.FiniteDuration = scala.concurrent.duration.Duration.Zero
    }
  }

  private val Ck = Citekey("zz2020_body")

  // three sections as the Chunker stores them: heading lines removed, breadcrumbs kept
  private val Segs = List(
    Chunk(Chunk.Ord(0), None, Chunk.Body("Preamble line about nothing.")),
    Chunk(
      Chunk.Ord(1),
      Some(Chunk.Heading("Theseus")),
      Chunk.Body("Theseus was buried on Skyros.\nCimon brought the bones back to Athens.")
    ),
    Chunk(
      Chunk.Ord(2),
      Some(Chunk.Heading("Theseus > Cult")),
      Chunk.Body("The Theseia honoured him.\n\nAt Ḫattuša nothing like it is known; Güterbock said so.")
    )
  )

  private def find(
      q: PassageFinder.Query,
      regex: PassageFinder.Regex = PassageFinder.Regex(false),
      limit: PassageFinder.Limit = PassageFinder.Limit(10),
      context: PassageFinder.ContextChars = PassageFinder.ContextChars(600),
      segs: List[Chunk] = Segs,
      max: PassageFinder.MaxMatches = PassageFinder.MaxMatches(10000),
      timeoutMs: PassageFinder.TimeoutMs = PassageFinder.TimeoutMs(2000)
  ): PassageResult =
    PassageFinder.find(Ck, segs, q, regex, limit, context, max, timeoutMs).fold(m => fail(m.sentence), identity)

  private def err(q: PassageFinder.Query, regex: PassageFinder.Regex): String =
    PassageFinder
      .find(
        Ck,
        Segs,
        q,
        regex,
        PassageFinder.Limit(10),
        PassageFinder.ContextChars(600),
        PassageFinder.MaxMatches(10000),
        PassageFinder.TimeoutMs(2000)
      )
      .fold(_.sentence, r => fail(s"expected an error, got $r"))

  /** The body slice `get_source {offset, chars}` would return. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def slice(segs: List[Chunk], offset: PassageHit.Offset, chars: Int): String =
    val b = PassageFinder.body(segs).value
    b.substring(offset.value, (offset.value + chars).min(b.length))

  test("the body is the chunks joined by a blank line — the text get_source slices"):
    assertEquals(PassageFinder.body(Segs).value.take(40), "Preamble line about nothing.\n\nTheseus wa")

  test("a literal match is case-insensitive and reports the offset get_source reads from"):
    val r = find(PassageFinder.Query("skyros"))
    assertEquals(r.matches, 1)
    assertEquals(r.total, 1)
    val p = r.passages.head
    assertEquals(slice(Segs, p.offset, p.text.value.length), p.text, "get_source at the offset starts with the passage")
    val m = p.matches.head
    assertEquals(p.text.value.substring(m.start.value, m.end.value), "Skyros")
    assertEquals(p.heading, Some("Theseus"))
    assertEquals(r.bodyChars, PassageFinder.body(Segs).value.length)

  test("a literal query is not a regex and not stemmed"):
    assertEquals(find(PassageFinder.Query("Skyr.s")).matches, 0)
    assertEquals(find(PassageFinder.Query("bone")).matches, 1) // substring of "bones": literal, not word-stemmed
    assertEquals(find(PassageFinder.Query("(nothing")).matches, 0) // regex metacharacters are plain text
    assertEquals(find(PassageFinder.Query("nothing.")).matches, 1)

  test("a phrase matches across a line break and any run of whitespace"):
    val r = find(PassageFinder.Query("Skyros.   Cimon"))
    assertEquals(r.matches, 1)
    val p = r.passages.head
    assertEquals(p.text.value.substring(p.matches.head.start.value, p.matches.head.end.value), "Skyros.\nCimon")

  test("accents are optional: a plain query finds the transliteration, an accented one the plain text"):
    val r = find(PassageFinder.Query("hattusa"))
    assertEquals(r.matches, 1)
    val p = r.passages.head
    assertEquals(p.text.value.substring(p.matches.head.start.value, p.matches.head.end.value), "Ḫattuša")
    assertEquals(find(PassageFinder.Query("Guterbock")).matches, 1)
    assertEquals(find(PassageFinder.Query("Thêseus")).matches, 1)

  test("decomposed combining marks belong to the match; folded offsets map back to the original"):
    val segs = List(Chunk(Chunk.Ord(0), None, Chunk.Body("x Güterbock y")))
    val p = find(PassageFinder.Query("guterbock"), segs = segs).passages.head
    assertEquals(p.text.value.substring(p.matches.head.start.value, p.matches.head.end.value), "Güterbock")
    val e = find(PassageFinder.Query("u"), segs = List(Chunk(Chunk.Ord(0), None, Chunk.Body("aüb")))).passages.head
    assertEquals(
      e.text.value.substring(e.matches.head.start.value, e.matches.head.end.value),
      "ü",
      "the mark rides with its letter"
    )

  test("OCR typography: curly quotes, dashes, ligatures and soft hyphens match their plain forms"):
    val segs = List(Chunk(Chunk.Ord(0), None, Chunk.Body("Theseus’ bones — the ﬁrst cult, a Bronze Age­ hero")))
    assertEquals(find(PassageFinder.Query("Theseus' bones - the first"), segs = segs).matches, 1)
    assertEquals(find(PassageFinder.Query("Age hero"), segs = segs).matches, 1)

  test("regex: case-insensitive, MULTILINE anchors, and an empty match is not a hit"):
    assertEquals(find(PassageFinder.Query("^cimon"), regex = PassageFinder.Regex(true)).matches, 1)
    assertEquals(
      find(PassageFinder.Query("^the"), regex = PassageFinder.Regex(true)).matches,
      2
    ) // "Theseus …" and "The Theseia" start lines; "the bones" does not
    assertEquals(find(PassageFinder.Query("these(us|ia)|cimon"), regex = PassageFinder.Regex(true)).matches, 3)
    assertEquals(find(PassageFinder.Query("x*"), regex = PassageFinder.Regex(true)).matches, 0)

  test("a bad regex is a clear message, never an exception"):
    val m = err(PassageFinder.Query("(unclosed"), regex = PassageFinder.Regex(true))
    assert(m.startsWith("not a valid regex: Unclosed group"), m)
    assert(m.contains("leave regex off"), m)
    assertEquals(
      PassageFinder
        .find(
          Ck,
          Segs,
          PassageFinder.Query("(unclosed"),
          PassageFinder.Regex(false),
          PassageFinder.Limit(10),
          PassageFinder.ContextChars(600),
          PassageFinder.MaxMatches(100),
          PassageFinder.TimeoutMs(2000)
        )
        .map(_.matches),
      Right(0)
    )

  test("an empty or blank query is refused"):
    assert(err(PassageFinder.Query("   "), regex = PassageFinder.Regex(false)).contains("empty"))

  test("a pathological regex is refused when it runs past the timeout"):
    // Java memoises the textbook (a+)+b; nested greedy .* still backtracks for seconds on 28 chars
    val segs = List(Chunk(Chunk.Ord(0), None, Chunk.Body("a" * 28 + "!")))
    val r = PassageFinder.find(
      Ck,
      segs,
      PassageFinder.Query("(.*a){12}x"),
      PassageFinder.Regex(true),
      PassageFinder.Limit(10),
      PassageFinder.ContextChars(600),
      PassageFinder.MaxMatches(10000),
      PassageFinder.TimeoutMs(50)
    )(using
      new Timer {
        def start(): Timer.Measure = new Timer.Measure {
          def elapsed: scala.concurrent.duration.FiniteDuration =
            scala.concurrent.duration.FiniteDuration(51, scala.concurrent.duration.MILLISECONDS)
        }
      }
    )
    assert(r.left.exists(_.sentence.contains("ran longer than 50 ms")), r)

  test("context bounds: whole lines when a line break is in reach, else whole words, never past the budget"):
    val long = (1 to 400).map(i => s"w$i").mkString(" ") // one long line, no breaks
    val segs = List(Chunk(Chunk.Ord(0), None, Chunk.Body(long)))
    val p = find(PassageFinder.Query("w200"), segs = segs, context = PassageFinder.ContextChars(100)).passages.head
    assert(p.text.value.length <= 100 + 4, p.text.value.length)
    assert(p.text.value.split(' ').forall(_.matches("""w\d+""")), p.text) // cut at word boundaries
    assert(p.text.value.contains("w200"))
    // with a line break inside the budget the passage starts at the line
    val q = find(PassageFinder.Query("bones"), context = PassageFinder.ContextChars(60)).passages.head
    assertEquals(q.text, "Cimon brought the bones back to Athens.")
    assertEquals(slice(Segs, q.offset, q.text.value.length), q.text)
    // context 0: just the match
    val z = find(PassageFinder.Query("Skyros"), context = PassageFinder.ContextChars(0)).passages.head
    assertEquals(z.text, "Skyros")

  test("passages never cross into each other and dense matches merge instead of repeating text"):
    val body = (1 to 50).map(i => s"Line $i mentions Skyros once.").mkString("\n")
    val segs = List(Chunk(Chunk.Ord(0), None, Chunk.Body(body)))
    val r = find(
      PassageFinder.Query("skyros"),
      segs = segs,
      context = PassageFinder.ContextChars(120),
      limit = PassageFinder.Limit(50)
    )
    assertEquals(r.matches, 50)
    val ps = r.passages
    ps.zip(ps.drop(1)).foreach((a, b) => assert(a.offset.value + a.text.value.length <= b.offset.value, (a, b)))
    assertEquals(ps.map(_.matches.size).sum, 50)
    ps.foreach(p => assertEquals(slice(segs, p.offset, p.text.value.length), p.text))

  test("heading attribution: the chunk's breadcrumb, none before the first heading"):
    assertEquals(find(PassageFinder.Query("Preamble")).passages.head.heading, None)
    assertEquals(find(PassageFinder.Query("Theseia")).passages.head.heading, Some("Theseus > Cult"))
    assertEquals(find(PassageFinder.Query("Güterbock")).passages.head.heading, Some("Theseus > Cult"))

  test("an unchunked body (raw Markdown) takes the nearest heading line above the match"):
    val raw = List(Chunk(Chunk.Ord(0), None, Chunk.Body("intro\n\n# Part One\n\nalpha\n\n## Sub ##\n\nbeta")))
    assertEquals(find(PassageFinder.Query("alpha"), segs = raw).passages.head.heading, Some("Part One"))
    assertEquals(find(PassageFinder.Query("beta"), segs = raw).passages.head.heading, Some("Sub"))
    assertEquals(find(PassageFinder.Query("intro"), segs = raw).passages.head.heading, None)

  test("limit truncates the passages shown; the total and the render say how many more"):
    val body = (1 to 30).map(i => s"Paragraph $i names Skyros.").mkString("\n\n")
    val r = find(
      PassageFinder.Query("skyros"),
      segs = List(Chunk(Chunk.Ord(0), Some(Chunk.Heading("H")), Chunk.Body(body))),
      context = PassageFinder.ContextChars(10),
      limit = PassageFinder.Limit(3)
    )
    assertEquals((r.matches, r.total, r.passages.size), (30, 30, 3))
    val md = PassagesMarkdown.render(r)
    assert(md.contains("_27 more — raise limit or narrow the query._"), md)
    assert(md.contains("30 matches in 30 passages · showing 3"), md)

  test("matches are counted up to the cap, then the result says the count stopped"):
    val body = List.fill(20)("Skyros").mkString(" ")
    val r = find(
      PassageFinder.Query("skyros"),
      segs = List(Chunk(Chunk.Ord(0), None, Chunk.Body(body))),
      max = PassageFinder.MaxMatches(5),
      context = PassageFinder.ContextChars(0)
    )
    assertEquals((r.matches, r.capped.capped), (5, true))
    assert(PassagesMarkdown.render(r).contains("5+ matches (counting stopped)"))

  test("an empty body or no match renders a one-line answer"):
    val r = find(PassageFinder.Query("nowhere"))
    assertEquals(r.passages, Nil)
    assertEquals(PassagesMarkdown.render(r), s"""_No match for "nowhere" in `$Ck` (${r.bodyChars} chars searched)._""")
    assertEquals(find(PassageFinder.Query("x"), segs = Nil).bodyChars, 0)

  test("the render bolds every match and heads each passage with its offset and heading"):
    val r = find(PassageFinder.Query("theseus"), context = PassageFinder.ContextChars(2000))
    val md = PassagesMarkdown.render(r)
    assert(md.startsWith("## Passages: \"theseus\" in `zz2020_body`"), md)
    assert(md.contains(s"### 1 · offset ${r.passages.head.offset} · § Theseus"), md)
    assert(md.contains("**Theseus** was buried"), md)
    val both = PassagesMarkdown.render(
      find(
        PassageFinder.Query("these(us|ia)"),
        regex = PassageFinder.Regex(true),
        context = PassageFinder.ContextChars(3000)
      )
    )
    assert(both.contains("**Theseus** was buried") && both.contains("The **Theseia**"), both)
    assert(both.startsWith("## Passages: /these(us|ia)/ in"), both)

  test("marked tolerates spans at the edges and adjacent spans"):
    val p = PassageHit(
      PassageHit.Offset(0),
      None,
      PassageHit.Text("abcabc"),
      List(MatchSpan(MatchSpan.Start(3), MatchSpan.End(6)), MatchSpan(MatchSpan.Start(0), MatchSpan.End(3)))
    )
    assertEquals(PassagesMarkdown.marked(p), "**abc****abc**")

  test("the configured limits clamp a caller's limit and context; absent means the default"):
    val l = PassageLimits(
      limit = PassageLimits.Limit(10),
      maxLimit = PassageLimits.MaxLimit(50),
      context = PassageLimits.Context(600),
      maxContext = PassageLimits.MaxContext(3000),
      maxMatches = PassageLimits.MaxMatches(10000),
      regexTimeoutMs = PassageLimits.RegexTimeoutMs(2000)
    )
    assertEquals(
      List(None, Some(0), Some(-3), Some(7), Some(500)).map(o => l.limitOf(o.map(PassageLimits.Limit(_)))),
      List(10, 10, 10, 7, 50)
    )
    assertEquals(
      List(None, Some(-1), Some(0), Some(200), Some(9000)).map(o => l.contextOf(o.map(PassageLimits.Context(_)))),
      List(600, 600, 0, 200, 3000)
    )
    assertEquals(AppConfig.load().limits.passages, l, "reference.conf carries these defaults")
