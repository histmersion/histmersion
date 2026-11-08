package com.histmersion
package domain.library.sources.referrers

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.domain.library.Citekey

/** What a referrer asks of a source a delete or a rename touches. */
class SourceKeysSuite extends munit.FunSuite:

  private val Ck = Citekey("beckman2011_ahhiyawa")

  test("linkedIn: any [[key]], with a label or an anchor, case-insensitively, under the citekey or an old one"):
    val source = SourceKeys(Ck, List(Ck, Citekey("beckman2010_ahh")), _ => Map.empty)
    assert(source.linkedIn(SourceKeys.Text(s"see [[$Ck|Beckman]]")))
    assert(source.linkedIn(SourceKeys.Text(s"ingested as [[${Ck.value.toUpperCase}]]")))
    assert(source.linkedIn(SourceKeys.Text(s"[[$Ck#ch-2]]")))
    assert(source.linkedIn(SourceKeys.Text("an old [[beckman2010_ahh]]")))
    assert(!source.linkedIn(SourceKeys.Text(s"plain $Ck")))
    assert(!source.linkedIn(SourceKeys.Text(s"[[${Ck}_v2]]")))

  test("tokenIn: the citekey as a whole token only"):
    val source = SourceKeys(Ck, List(Ck), _ => Map.empty)
    assert(source.tokenIn(SourceKeys.Text(s"fall_of_troy|$Ck")))
    assert(!source.tokenIn(SourceKeys.Text(s"fall_of_troy|${Ck}_v2")))

  test("citedIn: the texts' links as the library resolves them"):
    val source = SourceKeys(Ck, List(Ck), targets => targets.filter(_.value == "beckman2011").map(_ -> Ck).toMap)
    assert(source.citedIn(List(SourceKeys.Text("see [[beckman2011]]"), SourceKeys.Text("nothing"))))
    assert(!source.citedIn(List(SourceKeys.Text("see [[other2000_x]]"))))

  test("a rename: where each link points now, the needle, the finding prefix, the whole-token replacement"):
    val rename = SourceRename(Ck, Citekey("beckman2012_ahhiyawa"), Set(Citekey("beckman2011")))
    assertEquals(rename.target(Ck), Some("beckman2012_ahhiyawa"))
    assertEquals(rename.target(Citekey("beckman2011")), Some("beckman2012_ahhiyawa"))
    assertEquals(rename.target(Citekey("cline2014_1177")), None)
    assertEquals(rename.needle, "beckman2011")
    assertEquals(rename.findingPrefix, s"$Ck#")
    assertEquals(
      rename.inText(SourceKeys.Text(s"fall_of_troy|$Ck|${Ck}_v2")),
      s"fall_of_troy|beckman2012_ahhiyawa|${Ck}_v2"
    )
