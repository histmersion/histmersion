package com.histmersion.util.text

/** The `[[target]]` links of wiki text, whatever record holds it. */

import com.histmersion.api.text.WikiLinks
class WikiLinksSuite extends munit.FunSuite:

  test("targets: links with a label or an anchor, escaped ones too; not embeds or in-page anchors"):
    assertEquals(
      WikiLinks.targets("""[[a2000_x|A]] \[\[b2001_y\]\] ![[map.png]] [[#Sources|s]] [[C2002_z#p]] [[a2000_x]]"""),
      List("a2000_x", "b2001_y", "c2002_z")
    )

  test("links: a text links a key in any case"):
    assert(WikiLinks.links("see [[Beckman2011_ahhiyawa|B]]", "beckman2011_ahhiyawa"))
    assert(!WikiLinks.links("see beckman2011_ahhiyawa", "beckman2011_ahhiyawa"))

  test("retarget: the moved links only, labels and anchors kept, with how many moved"):
    val (out, n) = WikiLinks.retarget(
      "[[old2000_x|O]] [[ old2000_x #p]] [[keep2001_y]]",
      t => Option.when(t == "old2000_x")("new2000_x")
    )
    assertEquals(out, "[[new2000_x|O]] [[ new2000_x #p]] [[keep2001_y]]")
    assertEquals(n, 2)

  test("isCitekeyShaped: a year or an underscore, lower case"):
    assert(WikiLinks.isCitekeyShaped("page1959_history"))
    assert(WikiLinks.isCitekeyShaped("page_history"))
    assert(!WikiLinks.isCitekeyShaped("history"))
    assert(!WikiLinks.isCitekeyShaped("Page1959"))
