package com.histmersion.core

class ChunkerSuite extends munit.FunSuite:

  test("empty / blank text yields no chunks"):
    assertEquals(Chunker.chunk("", 100), Nil)
    assertEquals(Chunker.chunk("   \n\n \t \n", 100), Nil)

  test("text with no headings becomes headingless chunks"):
    val cs = Chunker.chunk("alpha beta\n\ngamma delta", 100)
    assertEquals(cs.map(_.heading), List(None))
    assertEquals(cs.head.body, "alpha beta\n\ngamma delta")

  test("ord is 0-based, gapless and in document order"):
    val md = (1 to 20).map(i => s"## H$i\n\npara $i").mkString("\n\n")
    val cs = Chunker.chunk(md, 100)
    assertEquals(cs.map(_.ord), cs.indices.toList)
    assertEquals(cs.head.body, "para 1")
    assertEquals(cs.last.body, "para 20")

  test("headings are inherited: a sub-section carries its parents as a breadcrumb"):
    val md =
      """|# Bronze Age
         |
         |intro text
         |
         |## Collapse
         |
         |collapse text
         |
         |### Sea Peoples
         |
         |raiders
         |
         |## Aftermath
         |
         |later text
         |""".stripMargin
    val cs = Chunker.chunk(md, 1000)
    assertEquals(cs.map(c => (c.heading, c.body)), List(
      Some("Bronze Age")                            -> "intro text",
      Some("Bronze Age > Collapse")                 -> "collapse text",
      Some("Bronze Age > Collapse > Sea Peoples")   -> "raiders",
      Some("Bronze Age > Aftermath")                -> "later text"
    ))

  test("text before the first heading has no heading; heading lines are not part of the body"):
    val cs = Chunker.chunk("preamble\n\n# Title\n\nbody", 1000)
    assertEquals(cs.map(_.heading), List(None, Some("Title")))
    assert(!cs.exists(_.body.contains("#")), cs)

  test("a heading with no text of its own produces no chunk"):
    val cs = Chunker.chunk("# A\n\n## B\n\nonly here", 1000)
    assertEquals(cs.map(c => (c.heading, c.body)), List(Some("A > B") -> "only here"))

  test("long sections split on paragraph boundaries, never exceeding maxChars"):
    val para = "x" * 90
    val md   = "# H\n\n" + List.fill(6)(para).mkString("\n\n")
    val cs   = Chunker.chunk(md, 200)
    assert(cs.forall(_.body.length <= 200), cs.map(_.body.length))
    assert(cs.forall(_.heading.contains("H")))
    assertEquals(cs.flatMap(_.body.split("\n\n").toList).size, 6) // no paragraph lost

  test("a single huge paragraph is split at whitespace — never mid-word"):
    val words = (1 to 500).map(i => s"word$i").mkString(" ")
    val cs    = Chunker.chunk(words, 200)
    assert(cs.size > 1)
    assert(cs.forall(_.body.length <= 200), cs.map(_.body.length))
    cs.foreach(c => assert(c.body.split(" ").forall(_.matches("""word\d+""")), c.body))
    // every word survives, in order
    assertEquals(cs.flatMap(_.body.split(" ").toList), words.split(" ").toList)

  test("a single token longer than maxChars is hard-split so chunks stay bounded"):
    val cs = Chunker.chunk("y" * 1000, 100)
    assertEquals(cs.size, 10)
    assert(cs.forall(_.body.length <= 100))

  test("NUL bytes are stripped (Postgres text rejects them)"):
    val cs = Chunker.chunk("a\u0000b\n\n# H\u0000ead\n\nc\u0000d", 100)
    assertEquals(cs.map(c => (c.heading, c.body)), List(None -> "ab", Some("Head") -> "cd"))

  test("CRLF is normalised so lengths and headings match the plain-LF case"):
    val a = Chunker.chunk("# H\r\n\r\none\r\n\r\ntwo", 1000)
    val b = Chunker.chunk("# H\n\none\n\ntwo", 1000)
    assertEquals(a, b)

  test("a 5 MB body chunks into bounded pieces (the tsvector 1 MB limit is per row)"):
    val body = ("lorem ipsum dolor sit amet " * 200_000).trim // ~5.4 MB
    val cs   = Chunker.chunk(body, 4000)
    assert(cs.size > 1000, cs.size)
    assert(cs.forall(_.body.length <= 4000))
    assertEquals(cs.map(_.ord), cs.indices.toList)

  test("maxChars <= 0 is clamped rather than looping forever"):
    assertEquals(Chunker.chunk("ab", 0).map(_.body), List("a", "b"))
