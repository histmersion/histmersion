package com.histmersion.core

/** A citekey is a path segment on the server's disk (the ingest spool, the acquisition spool) and a
  * URL segment. Nothing validated one before, which is how `../../../../tmp/x` became a directory. */
class CitekeySuite extends munit.FunSuite:

  test("the shapes the corpus actually uses are accepted"):
    List("anthony2007_horse", "cline2014_collapse", "x2020_v1.2", "smith2019_a-b", "ab")
      .foreach(k => assert(Citekey.parse(k).isRight, s"'$k' should be valid: ${Citekey.parse(k)}"))

  test("path traversal is refused"):
    List("../../../../tmp/x", "..", "../x", "a/../b", "foo/../../bar")
      .foreach(k => assert(Citekey.parse(k).isLeft, s"'$k' MUST be refused"))

  test("a separator, absolute path or NUL never gets through"):
    List("a/b", "/etc/passwd", "a\\b", "a\u0000b", "C:\\x", "a b", "a\nb")
      .foreach(k => assert(Citekey.parse(k).isLeft, s"'$k' MUST be refused"))

  test("empty, upper-case, digit-initial and over-long are refused"):
    assert(Citekey.parse("").isLeft)
    assert(Citekey.parse("   ").isLeft)
    assert(Citekey.parse("Anthony2007").isLeft, "lower-case only")
    assert(Citekey.parse("2007anthony").isLeft, "must start with a letter")
    assert(Citekey.parse("a").isLeft, "one character is not a citekey")
    assert(Citekey.parse("a" * 122).isLeft, "bounded — it becomes a file name")
    assert(Citekey.parse("a" * 121).isRight)

  test("surrounding whitespace is trimmed, not rejected"):
    assertEquals(Citekey.parse("  cline2014_collapse  ").map(_.toString),
      Right("cline2014_collapse"))

  test("the message says what a citekey looks like"):
    val e = Citekey.parse("Bad Key!").swap.toOption.get
    assert(e.contains("invalid citekey"), e)
    assert(e.contains("anthony2007_horse"), e)

  test("the traversal guard has its own message"):
    assert(Citekey.parse("../x").swap.toOption.get.contains("'..'"))
