package com.histmersion
package api
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Acquire.{EdgeKinds, Verdicts}
import com.histmersion.api.library.{Acquire, FollowupRow, RetractionNotice, UpdateNotice, WorkRef}
import com.histmersion.api.library.Source
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Title
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.acquisition.Ident
import com.histmersion.domain.library.ArxivId
import com.histmersion.api.library.Citekey

/** The pure rules of stream 2.6: citekeys, identifier parsing, verdicts, follow-up classification and the Retraction
  * Watch CSV. No network, no database.
  */
class AcquireSuite extends munit.FunSuite:

  // ── citekeys ────────────────────────────────────────────────────────────────────────────────
  test("citekey follows the corpus convention: surname + year + first significant title word"):
    assertEquals(
      Acquire.citekey(
        List(AuthorName("Wolfgang Haak"), AuthorName("Iosif Lazaridis")),
        Some(Year(2015)),
        Some(Title("Massive migration from the steppe was a source for Indo-European languages in Europe"))
      ),
      "haak2015_massive"
    )
    assertEquals(
      Acquire.citekey(List(AuthorName("Robert Drews")), Some(Year(1993)), Some(Title("The End of the Bronze Age"))),
      "drews1993_bronze"
    ) // "The" and "of" are stop words, "End" and "Age" are too short

  test("citekey handles 'Surname, Given', non-ASCII and missing pieces"):
    assertEquals(
      Acquire.citekey(List(AuthorName("Drews, Robert")), Some(Year(1993)), Some(Title("Collapse"))),
      "drews1993_collapse"
    )
    assertEquals(
      Acquire.citekey(List(AuthorName("Kristian Kristiansen")), Some(Year(2022)), Some(Title("Archaeology"))),
      "kristiansen2022_archaeology"
    )
    assertEquals(
      Acquire.citekey(List(AuthorName("Émilie Renard")), Some(Year(2001)), Some(Title("Étude des tombes"))),
      "renard2001_etude"
    )
    // the pre-#878 generator dropped the accented letter; records created then keep that key
    assertEquals(
      Acquire.legacyCitekey(List(AuthorName("Émilie Renard")), Some(Year(2001)), Some(Title("Étude des tombes"))),
      "renard2001_tude"
    )
    assertEquals(Acquire.citekey(Nil, None, None), "anonnd")
    assertEquals(
      Acquire.citekey(List(AuthorName(" ")), Some(Year(2015)), Some(Title("A New Study of the"))),
      "anon2015"
    )

  // #714: "Clunies Ross, Margaret" became `clunies…`, and Calibre's placeholder year 101 a key year
  test("citekey keeps a multi-word surname given as 'Surname, Given', and drops placeholder years"):
    assertEquals(
      Acquire.citekey(
        List(AuthorName("Clunies Ross, Margaret (ed.)")),
        Some(Year(2018)),
        Some(Title("The Pre-Christian Religions"))
      ),
      "cluniesross2018_christian"
    )
    assertEquals(
      Acquire.citekey(List(AuthorName("Young, Ian")), Some(Year(101)), Some(Title("Twilight"))),
      "youngnd_twilight"
    )
    assertEquals(
      Acquire.citekey(List(AuthorName("Young, Ian")), Some(Year(1000)), Some(Title("Twilight"))),
      "young1000_twilight"
    )

  // #699
  test("assess --apply: only unassessed is overwritten, unless forced; a retraction always is"):
    import com.histmersion.api.library.Source.Status.*
    assertEquals(Acquire.statusDecision(Verdicts.Current, Unassessed, force = false), Some(Right(Current)))
    assertEquals(Acquire.statusDecision(Verdicts.Current, Contested, force = false), Some(Left(Contested)))
    assertEquals(Acquire.statusDecision(Verdicts.Current, Foundational, force = false), Some(Left(Foundational)))
    assertEquals(Acquire.statusDecision(Verdicts.Current, Foundational, force = true), Some(Right(Current)))
    assertEquals(Acquire.statusDecision(Verdicts.Retracted, Foundational, force = false), Some(Right(Retracted)))
    assertEquals(Acquire.statusDecision(Verdicts.Corrected, Current, force = false), Some(Left(Current)))
    assertEquals(Acquire.statusDecision(Verdicts.LowImpact, Current, force = true), None)

  test("citekey is stable: the same metadata always yields the same key"):
    val a = Acquire.citekey(
      List(AuthorName("Eric H. Cline")),
      Some(Year(2014)),
      Some(Title("1177 B.C.: The Year Civilization Collapsed"))
    )
    assertEquals(
      a,
      Acquire.citekey(
        List(AuthorName("Eric H. Cline")),
        Some(Year(2014)),
        Some(Title("1177 B.C.: The Year Civilization Collapsed"))
      )
    )
    assertEquals(a, "cline2014_year") // digits are not letters, so "1177" is skipped

  // ── identifiers ─────────────────────────────────────────────────────────────────────────────
  test("DOIs are normalised: resolver prefix, doi: scheme, case and trailing punctuation"):
    assertEquals(Doi.normalize("https://doi.org/10.1038/Nature14317"), Some("10.1038/nature14317"))
    assertEquals(Doi.normalize("doi:10.1038/nature14317"), Some("10.1038/nature14317"))
    assertEquals(Doi.normalize("  10.1038/nature14317.  "), Some("10.1038/nature14317"))
    assertEquals(Doi.normalize("10.1126/science.1078208|"), Some("10.1126/science.1078208"))
    assertEquals(Doi.normalize("not a doi"), None)

  test("arXiv ids: prefix and version stripped, both id shapes"):
    assertEquals(Ident.arxiv(Ident.Written("arXiv:2301.01234v2")), Some("2301.01234"))
    assertEquals(Ident.arxiv(Ident.Written("2301.01234")), Some("2301.01234"))
    assertEquals(Ident.arxiv(Ident.Written("math.GT/0211159")), Some("math.GT/0211159"))
    assertEquals(Ident.arxiv(Ident.Written("haak2015_massive")), None)

  test("parseIdent prefers DOI, then arXiv, then citekey"):
    assertEquals(Ident.parse(Ident.Written("10.1038/nature14317")), Right(Ident.Doi(Doi("10.1038/nature14317"))))
    assertEquals(Ident.parse(Ident.Written("arxiv:2301.01234")), Right(Ident.Arxiv(ArxivId("2301.01234"))))
    assertEquals(
      Ident.parse(Ident.Written("haak2015_massive")),
      Right(Ident.Citekey(Ident.Citekey.Value("haak2015_massive")))
    )
    assert(Ident.parse(Ident.Written("")).isLeft)
    assert(Ident.parse(Ident.Written("Some Paper Title")).isLeft)

  // ── verdicts ────────────────────────────────────────────────────────────────────────────────
  private def v(
      retr: List[RetractionNotice] = Nil,
      upd: List[UpdateNotice] = Nil,
      oaRetracted: Boolean = false,
      cited: Option[Acquire.CitedByCount] = Some(Acquire.CitedByCount(10)),
      year: Option[Year] = Some(Year(2015))
  ) = Acquire.verdict(
    retr,
    upd,
    oaRetracted,
    cited,
    year,
    wellCitedAt = Acquire.CitationThreshold(50),
    lowCitedAt = Acquire.CitationThreshold(3),
    staleBefore = Year(2018)
  )

  test("a Retraction Watch retraction, an OpenAlex flag or a Crossref retraction all mean retracted"):
    assertEquals(
      v(retr = List(RetractionNotice(Some(RetractionNotice.Nature("Retraction")), None, None))),
      Verdicts.Retracted
    )
    assertEquals(v(oaRetracted = true), Verdicts.Retracted)
    assertEquals(v(upd = List(UpdateNotice(Some(UpdateNotice.Kind("retraction")), None, None))), Verdicts.Retracted)
    assertEquals(v(upd = List(UpdateNotice(Some(UpdateNotice.Kind("withdrawal")), None, None))), Verdicts.Retracted)

  test("a reinstatement is NOT a retraction"):
    assertEquals(
      v(
        retr = List(RetractionNotice(Some(RetractionNotice.Nature("Reinstatement")), None, None)),
        cited = Some(Acquire.CitedByCount(80))
      ),
      Verdicts.Corrected
    )

  test("corrections and expressions of concern mean corrected"):
    assertEquals(v(upd = List(UpdateNotice(Some(UpdateNotice.Kind("correction")), None, None))), Verdicts.Corrected)
    assertEquals(
      v(upd = List(UpdateNotice(Some(UpdateNotice.Kind("expression_of_concern")), None, None))),
      Verdicts.Corrected
    )
    assertEquals(
      v(retr = List(RetractionNotice(Some(RetractionNotice.Nature("Correction")), None, None))),
      Verdicts.Corrected
    )

  test("citation metrics decide when nothing is flagged"):
    assertEquals(v(cited = Some(Acquire.CitedByCount(900))), Verdicts.Current)
    assertEquals(v(cited = Some(Acquire.CitedByCount(1)), year = Some(Year(2001))), Verdicts.LowImpact)
    assertEquals(
      v(cited = Some(Acquire.CitedByCount(1)), year = Some(Year(2024))),
      Verdicts.Current
    ) // too new to judge by citations
    assertEquals(v(cited = None), Verdicts.Unassessed)

  test("verdict → flagged, status and recommendation"):
    assert(Acquire.flagged(Verdicts.Retracted) && Acquire.flagged(Verdicts.Corrected))
    assert(!Acquire.flagged(Verdicts.Current) && !Acquire.flagged(Verdicts.LowImpact))
    assertEquals(Acquire.statusFor(Verdicts.Retracted), Some(Source.Status.Retracted))
    assertEquals(Acquire.statusFor(Verdicts.Corrected), Some(Source.Status.Contested))
    assertEquals(Acquire.statusFor(Verdicts.Current), Some(Source.Status.Current))
    // low-impact is about reach, not correctness: it must never overwrite a curated status
    assertEquals(Acquire.statusFor(Verdicts.LowImpact), None)
    assertEquals(Acquire.statusFor(Verdicts.Unassessed), None)
    assert(Acquire.recommendation(Verdicts.Retracted, Some(Acquire.CitedByCount(3))).value.startsWith("EXCLUDE"))
    assert(Acquire.recommendation(Verdicts.Current, Some(Acquire.CitedByCount(90))).value.contains("90 citation(s)"))

  // ── follow-up classification ────────────────────────────────────────────────────────────────
  private def c(
      ctx: Acquire.CitationContext,
      intents: List[Acquire.Intent.Wire] = Nil,
      infl: Boolean = false,
      citing: Option[Year] = Some(Year(2020)),
      target: Option[Year] = Some(Year(2015))
  ) =
    Acquire.classify(intents, List(ctx), infl, citing, target)

  test("refutation cues win over the support word they contain"):
    assertEquals(c(Acquire.CitationContext("We find no support for the steppe hypothesis")), EdgeKinds.Refutes)
    assertEquals(c(Acquire.CitationContext("These data contradict the earlier claim")), EdgeKinds.Refutes)
    assertEquals(c(Acquire.CitationContext("The result could not be replicated")), EdgeKinds.Refutes)

  test("supersede, challenge and confirm cues"):
    assertEquals(c(Acquire.CitationContext("this model is now obsolete")), EdgeKinds.Supersedes)
    assertEquals(c(Acquire.CitationContext("However, the chronology is disputed")), EdgeKinds.Challenges)
    assertEquals(c(Acquire.CitationContext("Our results are consistent with Haak et al.")), EdgeKinds.Confirms)
    assertEquals(c(Acquire.CitationContext("as background, see Haak et al.")), EdgeKinds.Cites)

  test("an influential, much later result/method paper that reanalyses supersedes"):
    assertEquals(
      c(
        Acquire.CitationContext("we reanalyse the same samples with a larger dataset"),
        List(Acquire.Intent.Wire("result")),
        infl = true,
        citing = Some(Year(2023)),
        target = Some(Year(2015))
      ),
      EdgeKinds.Supersedes
    )
    // not influential → merely cites (the same words, none of the metadata)
    assertEquals(
      c(
        Acquire.CitationContext("we reanalyse the same samples with a larger dataset"),
        List(Acquire.Intent.Wire("result")),
        infl = false,
        citing = Some(Year(2023)),
        target = Some(Year(2015))
      ),
      EdgeKinds.Cites
    )
    // influential but published immediately after → not a superseding paper
    assertEquals(
      c(
        Acquire.CitationContext("we reanalyse the samples"),
        List(Acquire.Intent.Wire("result")),
        infl = true,
        citing = Some(Year(2016)),
        target = Some(Year(2015))
      ),
      EdgeKinds.Cites
    )

  test("empty context is a plain citation"):
    assertEquals(Acquire.classify(Nil, Nil, false, None, None), EdgeKinds.Cites)

  test("rankFollowups: influential first, then result/method intent, then recency"):
    val rows = List(
      FollowupRow(title = Some(Title("old plain")), year = Some(Year(2016))),
      FollowupRow(
        title = Some(Title("influential")),
        year = Some(Year(2017)),
        isInfluential = FollowupRow.IsInfluential(true)
      ),
      FollowupRow(title = Some(Title("recent plain")), year = Some(Year(2024))),
      FollowupRow(
        title = Some(Title("method")),
        year = Some(Year(2018)),
        intents = List(FollowupRow.Intents("methodology"))
      )
    )
    assertEquals(
      Acquire.rankFollowups(rows).flatMap(_.title),
      List("influential", "method", "recent plain", "old plain")
    )

  // ── Retraction Watch CSV ────────────────────────────────────────────────────────────────────
  private def csv = scala.io.Source.fromResource("acquire/retraction_watch.csv").mkString

  test("Retraction Watch CSV parses by header name, through quoted commas and newlines"):
    val rows = Acquire.parseRetractionWatch(csv)
    assertEquals(rows.size, 3)
    assertEquals(rows.head.doi, "10.1177/1758835919874651")
    assertEquals(rows.head.nature, Some("Retraction"))
    assert(rows.head.reason.exists(_.sentence.contains("Duplication of Image")))
    // the second row's Notes column contains a comma AND a newline inside quotes
    assertEquals(rows(1).doi, "10.1038/nature00000") // lower-cased by normalizeDoi
    assertEquals(rows(2).doi, "10.1126/science.yyy") // resolver prefix stripped
    assertEquals(rows(2).nature, Some("Reinstatement"))

  test("Retraction Watch dates become ISO, and junk becomes None"):
    assertEquals(Acquire.rwDate(Some(RetractionNotice.Date("12/15/2021 0:00"))), Some("2021-12-15"))
    assertEquals(Acquire.rwDate(Some(RetractionNotice.Date("2021-12-15"))), Some("2021-12-15"))
    assertEquals(Acquire.rwDate(Some(RetractionNotice.Date(""))), None)
    assertEquals(Acquire.rwDate(Some(RetractionNotice.Date("unknown"))), None)
    assertEquals(Acquire.rwDate(None), None)

  test("the CSV reader handles quotes, doubled quotes, empty fields and CRLF"):
    assertEquals(Csv.parse("""a,b,c""" + "\r\n" + """1,"x,y",""").head, List("a", "b", "c"))
    assertEquals(Csv.parse("a,b\n1,\"he said \"\"hi\"\"\"")(1), List("1", """he said "hi""""))
    assertEquals(Csv.parse("a\n\n\nb"), List(List("a"), List("b"))) // blank lines dropped
    assertEquals(Csv.parse(""), Nil)

  test("browserDownloadText: an open-access work no script may fetch, as a download in the browser (#1581)"):
    val w = WorkRef(
      title = (Some("Archaeology and Religion in Late Bronze Age Canaan")).map(Title.apply),
      year = (Some(2019)).map(Year.apply),
      authors = (List("Aaron Greener")).map(AuthorName.apply),
      isOa = WorkRef.IsOa(true),
      oaStatus = Some(WorkRef.OaStatus("gold")),
      oaUrl = Some(WorkRef.OaUrl("https://www.mdpi.com/2077-1444/10/4/258/pdf"))
    )
    assertEquals(
      Acquire.browserDownloadText(
        w,
        com.histmersion.api.library.Doi("10.3390/rel10040258"),
        Citekey("greener2019_archaeology")
      ),
      Some(
        "Aaron Greener (2019). Archaeology and Religion in Late Bronze Age Canaan. https://doi.org/10.3390/rel10040258 — " +
          "gold open access at https://www.mdpi.com/2077-1444/10/4/258/pdf, but no script may download it: download it in the " +
          "browser, then histmersion ingest <file> --citekey greener2019_archaeology"
      )
    )
    // the DOI link is not repeated as the location; no status says just "open access"; a closed work gets none
    val doiOnly = w
      .modify(_.oaStatus)
      .setTo(None)
      .modify(_.oaUrl)
      .setTo(Some(WorkRef.OaUrl("https://doi.org/10.3390/rel10040258")))
      .modify(_.authors)
      .setTo(List("A", "B").map(AuthorName.apply))
    assert(
      Acquire
        .browserDownloadText(doiOnly, com.histmersion.api.library.Doi("10.3390/REL10040258"), Citekey("k"))
        .exists(t =>
          t.value.startsWith("A et al. (2019).") && t.value.contains("— open access, but") && !t.value
            .contains(" at https://doi.org")
        )
    )
    assertEquals(
      Acquire.browserDownloadText(
        w.modify(_.isOa).setTo(WorkRef.IsOa(false)),
        com.histmersion.api.library.Doi("10.3390/rel10040258"),
        Citekey("k")
      ),
      None
    )

  test("browserDownloadText: a fetcher's refusal says where to download it and under which licence"):
    val w = WorkRef(
      title = (Some("Gods of the Bronze Age")).map(Title.apply),
      year = (Some(2020)).map(Year.apply),
      authors = (List("A")).map(AuthorName.apply),
      isOa = WorkRef.IsOa(true),
      oaStatus = Some(WorkRef.OaStatus("gold"))
    )
    val refusal = Problem.Unclassified(
      Legacy.Text(
        "oapen: an openly licensed copy exists but could not be downloaded: 20.500.12657/23223 " +
          "(https://library.oapen.org/handle/20.500.12657/23223), licence CC BY 4.0"
      )
    )
    val text = Acquire
      .browserDownloadText(
        w,
        com.histmersion.api.library.Doi("10.1515/zz"),
        Citekey("k"),
        List(refusal, refusal, Problem.Unclassified(Legacy.Text(" ")))
      )
      .getOrElse(fail("an item"))
      .value
    assert(
      text.contains(s"download it in the browser (${refusal.sentence}), then histmersion ingest <file> --citekey k"),
      text
    )
    assertEquals(text.sliding(refusal.sentence.length).count(_ == refusal.sentence), 1, "each refusal once")
