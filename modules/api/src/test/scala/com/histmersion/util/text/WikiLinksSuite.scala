package com.histmersion
package util.text

import com.histmersion.testsupport.ComparedByValue.given

/** The `[[target]]` links of wiki text, whatever record holds it. */

import com.histmersion.api.text.WikiLinks
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class WikiLinksSuite extends munit.FunSuite:

  test("targets: links with a label or an anchor, escaped ones too; not embeds or in-page anchors"):
    assertEquals(
      WikiLinks.targets(
        WikiLinks.Body("""[[a2000_x|A]] \[\[b2001_y\]\] ![[map.png]] [[#Sources|s]] [[C2002_z#p]] [[a2000_x]]""")
      ),
      List("a2000_x", "b2001_y", "c2002_z")
    )

  test("links: a text links a key in any case"):
    assert(WikiLinks.links(WikiLinks.Body("see [[Beckman2011_ahhiyawa|B]]"), Citekey("beckman2011_ahhiyawa")))
    assert(!WikiLinks.links(WikiLinks.Body("see beckman2011_ahhiyawa"), Citekey("beckman2011_ahhiyawa")))

  test("retarget: the moved links only, labels and anchors kept, with how many moved"):
    val (out, n) = WikiLinks.retarget(
      WikiLinks.Body("[[old2000_x|O]] [[ old2000_x #p]] [[keep2001_y]]"),
      t => Option.when(t.value == "old2000_x")(WikiLinks.Occurrence.Target("new2000_x"))
    )
    assertEquals(out, "[[new2000_x|O]] [[ new2000_x #p]] [[keep2001_y]]")
    assertEquals(n, 2)

  test("isCitekeyShaped: a year or an underscore, lower case"):
    assert(WikiLinks.isCitekeyShaped("page1959_history"))
    assert(WikiLinks.isCitekeyShaped("page_history"))
    assert(!WikiLinks.isCitekeyShaped("history"))
    assert(!WikiLinks.isCitekeyShaped("Page1959"))
