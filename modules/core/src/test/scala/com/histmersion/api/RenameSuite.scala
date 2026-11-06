package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.SourcesMarkdown

import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** The pure half of a citekey rename: which new keys are acceptable, which short forms pointed at the old key, how
  * finding ids and composite ids move, and the link rewrite that must change the link target and nothing else.
  */
class RenameSuite extends munit.FunSuite:

  test("validate: a valid, link-shaped, different key passes (trimmed)"):
    assertEquals(
      com.histmersion.domain.library.sources.Citekeys.renameTo(com.histmersion.domain.library.Citekey("page2024_history"), com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw(" page1959_history ")).map(ck => ck.value: String),
      Right("page1959_history")
    )
    assertEquals(com.histmersion.domain.library.sources.Citekeys.renameTo(com.histmersion.domain.library.Citekey("x2020_a"), com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw("knapp2016")).map(ck => ck.value: String), Right("knapp2016"))

  test("validate: the same key, an invalid key and a key the link resolver would not take are refused"):
    def refused(to: com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw, says: String) =
      com.histmersion.domain.library.sources.Citekeys.renameTo(com.histmersion.domain.library.Citekey("page2024_history"), to) match
        case Left(e) =>
          assert(e.value.nonEmpty)
          assert(e.value.contains(says), e)
        case Right(k) => fail(s"$to should be refused, got $k")
    refused(com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw("page2024_history"), "already the citekey")
    refused(com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw("Page 1959"), "invalid citekey")
    refused(com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw("../etc"), "invalid citekey")
    refused(com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw("page-1959"), "LINK-shaped") // a valid citekey, but [[page-1959]] is an article link
    refused(com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw("page1959.v2"), "LINK-shaped")
    refused(com.histmersion.domain.library.sources.Citekeys.RenameToNewRaw("nodigits"), "LINK-shaped")

  test("shortForms: every prefix that ends right before an underscore"):
    assertEquals(Rename.shortForms(com.histmersion.api.library.Citekey("beckman2025_ahhiyawa")), List("beckman2025"))
    assertEquals(Rename.shortForms(com.histmersion.api.library.Citekey("a2020_b_c")), List("a2020", "a2020_b"))
    assertEquals(Rename.shortForms(com.histmersion.api.library.Citekey("knapp2016")), Nil)
    assertEquals(Rename.shortForms(com.histmersion.api.library.Citekey("_x")), Nil)

  test("findingId: only `<old>#…` moves"):
    assertEquals(Rename.findingId(com.histmersion.api.library.Finding.Id("old2020_a#3"), com.histmersion.api.library.Citekey("old2020_a"), com.histmersion.api.library.Citekey("new1999_a")), Some("new1999_a#3"))
    assertEquals(Rename.findingId(com.histmersion.api.library.Finding.Id("old2020_ab#3"), com.histmersion.api.library.Citekey("old2020_a"), com.histmersion.api.library.Citekey("new1999_a")), None)
    assertEquals(Rename.findingId(com.histmersion.api.library.Finding.Id("custom-id"), com.histmersion.api.library.Citekey("old2020_a"), com.histmersion.api.library.Citekey("new1999_a")), None)

  test("replaceToken: the whole key only, never inside a longer one"):
    assertEquals(
      Rename.replaceToken("b2021_y|old2020_a|references", com.histmersion.api.library.Citekey("old2020_a"), com.histmersion.api.library.Citekey("new1999_a")),
      "b2021_y|new1999_a|references"
    )
    assertEquals(Rename.replaceToken("old2020_a#2", com.histmersion.api.library.Citekey("old2020_a"), com.histmersion.api.library.Citekey("new1999_a")), "new1999_a#2")
    assertEquals(
      Rename.replaceToken("zold2020_a|old2020_ab|old2020_a.x", com.histmersion.api.library.Citekey("old2020_a"), com.histmersion.api.library.Citekey("new1999_a")),
      "zold2020_a|old2020_ab|old2020_a.x"
    )
    // a `.` in the key is literal, not a regex wildcard
    assertEquals(Rename.replaceToken("x2020_v1x2", com.histmersion.api.library.Citekey("x2020_v1.2"), com.histmersion.api.library.Citekey("y")), "x2020_v1x2")

  test("retarget: target moved; label, anchor, escapes, spacing and embeds kept"):
    val to: com.histmersion.api.text.WikiLinks.Occurrence.Target => Option[com.histmersion.api.text.WikiLinks.Occurrence.Target] =
      t => Option.when(t.value == "old2020_a" || t.value == "old2020")(com.histmersion.api.text.WikiLinks.Occurrence.Target("new1999_a"))
    val md = com.histmersion.api.text.WikiLinks.Body(
      """[[old2020_a]] [[old2020_a|the book]] [[old2020_a#ch-1|ch. 1]] [[ Old2020_A ]]
        |\[\[old2020_a\]\] ![[old2020_a]] [[old2020]] [[old2020_ab]] [[other2019_x]] [old2020_a]""".stripMargin
    )
    val (out, n) = Article.Links.retarget(md, to)
    assertEquals(
      out,
      """[[new1999_a]] [[new1999_a|the book]] [[new1999_a#ch-1|ch. 1]] [[ new1999_a ]]
        |\[\[new1999_a\]\] ![[old2020_a]] [[new1999_a]] [[old2020_ab]] [[other2019_x]] [old2020_a]""".stripMargin
    )
    assertEquals(n, 6)

  test("retarget: nothing to move is the identity"):
    val md = com.histmersion.api.text.WikiLinks.Body("No [[links]] here to [[other2019_x]].")
    assertEquals(Article.Links.retarget(md, _ => None), (md, 0))

  test("the rename report names the alias, the counts, and what Calibre and the mirror need"):
    val src = com.histmersion.api.library.Source.Detail(
      com.histmersion.api.library.Citekey("page1959_history"),
      com.histmersion.api.library.Title("History"),
      (List("Page")).map(com.histmersion.api.library.AuthorName.apply),
      (Some(1959)).map(com.histmersion.api.catalogue.Year.apply),
      None,
      Source.Status.Foundational,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None
    )
    val counts = Rename.Counts(com.histmersion.api.library.Rename.Counts.Findings(3), List(com.histmersion.api.library.Rename.Counts.Articles("troy")), List(com.histmersion.api.library.Rename.Counts.Sources("x2020_y")), com.histmersion.api.library.Rename.Counts.Links(5), List(Rename.Count(com.histmersion.api.library.Rename.Count.Table("jobs.citekey"), com.histmersion.api.library.Rename.Count.Rows(1))), com.histmersion.api.library.Rename.Counts.EdgesPromoted(0))
    val pushed =
      SourcesMarkdown.sourceRenamed(
        com.histmersion.api.library.Source.Renamed(com.histmersion.api.library.Citekey("page2024_history"), com.histmersion.api.library.Citekey("page1959_history"), src, counts, Some(com.histmersion.api.library.Source.CalibreId("12")))
      )
    List(
      "`page2024_history` → `page1959_history`",
      "findings renumbered: 3",
      "`troy`",
      "jobs.citekey: 1",
      "is now an alias",
      "book 12",
      "sources/page2024_history.md"
    )
      .foreach(s => assert(pushed.contains(s), s"missing '$s' in\n$pushed"))
    val notPushed =
      SourcesMarkdown.sourceRenamed(
        com.histmersion.api.library.Source.Renamed(com.histmersion.api.library.Citekey("page2024_history"), com.histmersion.api.library.Citekey("page1959_history"), src, counts, None)
      )
    assert(notPushed.contains("calibre push page1959_history"), notPushed)
    assert(notPushed.contains("`page2024_history` directory"), notPushed)
