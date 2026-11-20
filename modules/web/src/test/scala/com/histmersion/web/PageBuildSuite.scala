package com.histmersion
package web

import com.histmersion.api.instance.UiConfig
import com.histmersion.api.instance.UiConfig.BuildId.Verdict

/** G109 (review finding W6): the page and the server agree on a build. The comparison, its unknown sides, and the
  * prompt's flag, which an answer naming another build sets and nothing clears until the page reloads.
  */
class PageBuildSuite extends munit.FunSuite:

  private val a = UiConfig.BuildId("0123456789abcdef0123456789abcdef")
  private val b = UiConfig.BuildId("fedcba9876543210fedcba9876543210")

  test("the same build is Same, another build is Updated"):
    assertEquals(a.against(a), Verdict.Same)
    assertEquals(a.against(b), Verdict.Updated)
    assertEquals(b.against(a), Verdict.Updated)

  test("a side that names no build compares nothing: the development bundle, an older server, a malformed id"):
    List(
      UiConfig.BuildId(""),
      PageBuild.compiled,
      UiConfig.BuildId("0123456789ABCDEF0123456789ABCDEF"),
      UiConfig.BuildId("0123456789abcdef"),
      UiConfig.BuildId("0123456789abcdef0123456789abcdeg")
    ).foreach { none =>
      assertEquals(none.against(a), Verdict.Unknown, s"page $none")
      assertEquals(a.against(none), Verdict.Unknown, s"server $none")
    }

  test("a bundle linked outside the server's build keeps the placeholder, which is no build, and is as long as one"):
    assert(!PageBuild.compiled.known, s"${PageBuild.compiled}")
    assertEquals(s"${PageBuild.compiled}".length, s"$a".length)

  test("the prompt's flag: set by an answer naming another build, kept whatever answers next"):
    PageBuild.updated.set(false)
    PageBuild.checkAs(a, a)
    PageBuild.checkAs(a, UiConfig.BuildId(""))
    assertEquals(PageBuild.updated.now(), false, "the same build, or none: no prompt")
    PageBuild.checkAs(a, b)
    assertEquals(PageBuild.updated.now(), true, "another build: the prompt")
    PageBuild.checkAs(a, a)
    assertEquals(PageBuild.updated.now(), true, "a later answer of the page's build does not take the prompt away")
    PageBuild.updated.set(false)

  test("this test bundle is linked outside the server's build: no server's id sets the prompt"):
    PageBuild.updated.set(false)
    PageBuild.check(a)
    assertEquals(PageBuild.updated.now(), false)
