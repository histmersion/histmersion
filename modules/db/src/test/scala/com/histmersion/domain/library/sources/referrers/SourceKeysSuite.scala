package com.histmersion.domain.library.sources.referrers

/** What a referrer asks of a source a delete or a rename touches. */
class SourceKeysSuite extends munit.FunSuite:

  private val Ck = "beckman2011_ahhiyawa"

  test("linkedIn: any [[key]], with a label or an anchor, case-insensitively, under the citekey or an old one"):
    val source = SourceKeys(Ck, List(Ck, "beckman2010_ahh"), _ => Map.empty)
    assert(source.linkedIn(s"see [[$Ck|Beckman]]"))
    assert(source.linkedIn(s"ingested as [[${Ck.toUpperCase}]]"))
    assert(source.linkedIn(s"[[$Ck#ch-2]]"))
    assert(source.linkedIn("an old [[beckman2010_ahh]]"))
    assert(!source.linkedIn(s"plain $Ck"))
    assert(!source.linkedIn(s"[[${Ck}_v2]]"))

  test("tokenIn: the citekey as a whole token only"):
    val source = SourceKeys(Ck, List(Ck), _ => Map.empty)
    assert(source.tokenIn(s"fall_of_troy|$Ck"))
    assert(!source.tokenIn(s"fall_of_troy|${Ck}_v2"))

  test("citedIn: the texts' links as the library resolves them"):
    val source = SourceKeys(Ck, List(Ck), targets => targets.filter(_ == "beckman2011").map(_ -> Ck).toMap)
    assert(source.citedIn(List("see [[beckman2011]]", "nothing")))
    assert(!source.citedIn(List("see [[other2000_x]]")))

  test("a rename: where each link points now, the needle, the finding prefix, the whole-token replacement"):
    val rename = SourceRename(Ck, "beckman2012_ahhiyawa", Set("beckman2011"))
    assertEquals(rename.target(Ck), Some("beckman2012_ahhiyawa"))
    assertEquals(rename.target("beckman2011"), Some("beckman2012_ahhiyawa"))
    assertEquals(rename.target("cline2014_1177"), None)
    assertEquals(rename.needle, "beckman2011")
    assertEquals(rename.findingPrefix, s"$Ck#")
    assertEquals(rename.inText(s"fall_of_troy|$Ck|${Ck}_v2"), s"fall_of_troy|beckman2012_ahhiyawa|${Ck}_v2")
