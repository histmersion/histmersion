package com.histmersion
package web

import com.histmersion.api.instance.BuildId
import com.histmersion.api.instance.BuildId.Verdict
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PageBuildSuite extends munit.FunSuite:

  private val a = BuildId("0123456789abcdef0123456789abcdef")
  private val b = BuildId("fedcba9876543210fedcba9876543210")

  test("the same build is Same, another build is Updated"):
    assertEquals(a.against(a), Verdict.Same)
    assertEquals(a.against(b), Verdict.Updated)
    assertEquals(b.against(a), Verdict.Updated)

  test("a side that names no build compares nothing: the development bundle, an older server, a malformed id"):
    List(
      BuildId(""),
      PageBuild.compiled,
      BuildId("0123456789ABCDEF0123456789ABCDEF"),
      BuildId("0123456789abcdef"),
      BuildId("0123456789abcdef0123456789abcdeg")
    ).foreach { none =>
      assertEquals(none.against(a), Verdict.Unknown, s"page $none")
      assertEquals(a.against(none), Verdict.Unknown, s"server $none")
    }

  test("a header's text is a build only as the build writes it"):
    assertEquals(BuildId.parse("0123456789abcdef0123456789abcdef"), Some(a))
    List("", "HISTMERSION-BUILD-ID-UNSET-00000", "0123456789ABCDEF0123456789ABCDEF", "0123456789abcdef").foreach {
      text => assertEquals(BuildId.parse(text), None, text)
    }

  test("an id goes into an address unchanged; anything else percent-encoded"):
    assertEquals(a.encoded, "0123456789abcdef0123456789abcdef")
    assertEquals(BuildId("a b/c").encoded, "a%20b%2Fc")

  test("a bundle linked outside the server's build keeps the placeholder, which is no build, and is as long as one"):
    assert(!PageBuild.compiled.known, s"${PageBuild.compiled}")
    assertEquals(s"${PageBuild.compiled}".length, s"$a".length)

  test("the prompt's flag: set by an answer naming another build, kept whatever answers next"):
    val page = PageBuild(a)
    page.check(a)
    page.check(BuildId(""))
    assertEquals(page.updated.now(), false, "the same build, or none: no prompt")
    page.check(b)
    assertEquals(page.updated.now(), true, "another build: the prompt")
    page.check(a)
    assertEquals(page.updated.now(), true, "a later answer of the page's build does not take the prompt away")

  test("this test bundle is linked outside the server's build: no server's id sets the prompt"):
    val page = PageBuild(PageBuild.compiled)
    page.check(a)
    assertEquals(page.updated.now(), false)
