package com.histmersion
package values

import com.histmersion.util.problem.Problem

import com.histmersion.values.testsupport.ComparedByValue.given

/** A citekey is a path segment on the server's disk (the ingest spool, the acquisition spool) and a URL segment.
  * Nothing validated one before, which is how `../../../../tmp/x` became a directory.
  */

import com.histmersion.domain.library.Citekey
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
    assertEquals(Citekey.parse("  cline2014_collapse  ").map(c => c.value: String), Right("cline2014_collapse"))

  test("no citekey is Malformed, naming the type, its constraint and the input; an empty one is Missing"):
    Citekey.parse("Bad Key!") match
      case Left(Problem.Invalid.Malformed(t, c, raw, _)) =>
        assertEquals(t, Problem.TypeName("com.histmersion.domain.library.Citekey"))
        assertEquals(raw, Problem.Raw("Bad Key!"))
        assert(s"${c.name}".contains("[a-z][a-z0-9_.-]{1,120}"), c)
      case other => fail(s"not malformed: $other")
    assert(Citekey.parse("../x").left.exists(_.isInstanceOf[Problem.Invalid.Malformed]))
    assert(Citekey.parse("ab..cd").left.exists(_.isInstanceOf[Problem.Invalid.Malformed]))
    assertEquals(Citekey.parse("  "), Left(Problem.Invalid.Missing.here(Problem.Field("citekey"))))

  test("valid classifies without a message; a literal is checked by the compiler"):
    assert(Citekey.valid("anthony2007_horse") && !Citekey.valid("x/../y"))
    assertEquals(Citekey("anthony2007_horse").value: String, "anthony2007_horse")
    assert(compileErrors("""Citekey("Not A Key")""").nonEmpty)
