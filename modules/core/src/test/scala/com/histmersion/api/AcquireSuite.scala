package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.Acquire.{EdgeKinds, Verdicts}
import com.histmersion.api.library.{Acquire, FollowupRow, RetractionNotice, UpdateNotice, WorkRef}
import com.histmersion.api.library.Source

/** The pure rules of stream 2.6: citekeys, identifier parsing, verdicts, follow-up classification and the Retraction
  * Watch CSV. No network, no database.
  */
class AcquireSuite extends munit.FunSuite:

  // ── citekeys ────────────────────────────────────────────────────────────────────────────────
  test("citekey follows the corpus convention: surname + year + first significant title word"):
    assertEquals(
      Acquire.citekey(
        List(com.histmersion.api.library.AuthorName("Wolfgang Haak"), com.histmersion.api.library.AuthorName("Iosif Lazaridis")),
        Some(com.histmersion.api.catalogue.Year(2015)),
        Some("Massive migration from the steppe was a source for Indo-European languages in Europe")
      ),
      "haak2015_massive"
    )
    assertEquals(
      Acquire.citekey(List(com.histmersion.api.library.AuthorName("Robert Drews")), Some(com.histmersion.api.catalogue.Year(1993)), Some(com.histmersion.api.library.Title("The End of the Bronze Age"))),
      "drews1993_bronze"
    ) // "The" and "of" are stop words, "End" and "Age" are too short

  test("citekey handles 'Surname, Given', non-ASCII and missing pieces"):
    assertEquals(Acquire.citekey(List(com.histmersion.api.library.AuthorName("Drews, Robert")), Some(com.histmersion.api.catalogue.Year(1993)), Some(com.histmersion.api.library.Title("Collapse"))), "drews1993_collapse")
    assertEquals(
      Acquire.citekey(List(com.histmersion.api.library.AuthorName("Kristian Kristiansen")), Some(com.histmersion.api.catalogue.Year(2022)), Some(com.histmersion.api.library.Title("Archaeology"))),
      "kristiansen2022_archaeology"
    )
    assertEquals(Acquire.citekey(List(com.histmersion.api.library.AuthorName("Émilie Renard")), Some(com.histmersion.api.catalogue.Year(2001)), Some(com.histmersion.api.library.Title("Étude des tombes"))), "renard2001_etude")
    // the pre-#878 generator dropped the accented letter; records created then keep that key
    assertEquals(Acquire.legacyCitekey(List(com.histmersion.api.library.AuthorName("Émilie Renard")), Some(com.histmersion.api.catalogue.Year(2001)), Some(com.histmersion.api.library.Title("Étude des tombes"))), "renard2001_tude")
    assertEquals(Acquire.citekey(Nil, None, None), "anonnd")
    assertEquals(Acquire.citekey(List(com.histmersion.api.library.AuthorName(" ")), Some(com.histmersion.api.catalogue.Year(2015)), Some(com.histmersion.api.library.Title("A New Study of the"))), "anon2015")

  // #714: "Clunies Ross, Margaret" became `clunies…`, and Calibre's placeholder year 101 a key year
  test("citekey keeps a multi-word surname given as 'Surname, Given', and drops placeholder years"):
    assertEquals(
      Acquire.citekey(List(com.histmersion.api.library.AuthorName("Clunies Ross, Margaret (ed.)")), Some(com.histmersion.api.catalogue.Year(2018)), Some(com.histmersion.api.library.Title("The Pre-Christian Religions"))),
      "cluniesross2018_christian"
    )
    assertEquals(Acquire.citekey(List(com.histmersion.api.library.AuthorName("Young, Ian")), Some(com.histmersion.api.catalogue.Year(101)), Some(com.histmersion.api.library.Title("Twilight"))), "youngnd_twilight")
    assertEquals(Acquire.citekey(List(com.histmersion.api.library.AuthorName("Young, Ian")), Some(com.histmersion.api.catalogue.Year(1000)), Some(com.histmersion.api.library.Title("Twilight"))), "young1000_twilight")

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
    val a = Acquire.citekey(List(com.histmersion.api.library.AuthorName("Eric H. Cline")), Some(com.histmersion.api.catalogue.Year(2014)), Some(com.histmersion.api.library.Title("1177 B.C.: The Year Civilization Collapsed")))
    assertEquals(
      a,
      Acquire.citekey(List(com.histmersion.api.library.AuthorName("Eric H. Cline")), Some(com.histmersion.api.catalogue.Year(2014)), Some(com.histmersion.api.library.Title("1177 B.C.: The Year Civilization Collapsed")))
    )
    assertEquals(a, "cline2014_year") // digits are not letters, so "1177" is skipped

  // ── identifiers ─────────────────────────────────────────────────────────────────────────────
  test("DOIs are normalised: resolver prefix, doi: scheme, case and trailing punctuation"):
    assertEquals(com.histmersion.domain.library.Doi.normalize("https://doi.org/10.1038/Nature14317"), Some("10.1038/nature14317"))
    assertEquals(com.histmersion.domain.library.Doi.normalize("doi:10.1038/nature14317"), Some("10.1038/nature14317"))
    assertEquals(com.histmersion.domain.library.Doi.normalize("  10.1038/nature14317.  "), Some("10.1038/nature14317"))
    assertEquals(com.histmersion.domain.library.Doi.normalize("10.1126/science.1078208|"), Some("10.1126/science.1078208"))
    assertEquals(com.histmersion.domain.library.Doi.normalize("not a doi"), None)

  test("arXiv ids: prefix and version stripped, both id shapes"):
    assertEquals(com.histmersion.domain.library.acquisition.Ident.arxiv(com.histmersion.domain.library.acquisition.Ident.Written("arXiv:2301.01234v2")), Some("2301.01234"))
    assertEquals(com.histmersion.domain.library.acquisition.Ident.arxiv(com.histmersion.domain.library.acquisition.Ident.Written("2301.01234")), Some("2301.01234"))
    assertEquals(com.histmersion.domain.library.acquisition.Ident.arxiv(com.histmersion.domain.library.acquisition.Ident.Written("math.GT/0211159")), Some("math.GT/0211159"))
    assertEquals(com.histmersion.domain.library.acquisition.Ident.arxiv(com.histmersion.domain.library.acquisition.Ident.Written("haak2015_massive")), None)

  test("parseIdent prefers DOI, then arXiv, then citekey"):
    assertEquals(com.histmersion.domain.library.acquisition.Ident.parse(com.histmersion.domain.library.acquisition.Ident.Written("10.1038/nature14317")), Right(com.histmersion.domain.library.acquisition.Ident.Doi(com.histmersion.domain.library.Doi("10.1038/nature14317"))))
    assertEquals(com.histmersion.domain.library.acquisition.Ident.parse(com.histmersion.domain.library.acquisition.Ident.Written("arxiv:2301.01234")), Right(com.histmersion.domain.library.acquisition.Ident.Arxiv(com.histmersion.domain.library.ArxivId("2301.01234"))))
    assertEquals(com.histmersion.domain.library.acquisition.Ident.parse(com.histmersion.domain.library.acquisition.Ident.Written("haak2015_massive")), Right(com.histmersion.domain.library.acquisition.Ident.Citekey(com.histmersion.domain.library.acquisition.Ident.Citekey.Value("haak2015_massive"))))
    assert(com.histmersion.domain.library.acquisition.Ident.parse(com.histmersion.domain.library.acquisition.Ident.Written("")).isLeft)
    assert(com.histmersion.domain.library.acquisition.Ident.parse(com.histmersion.domain.library.acquisition.Ident.Written("Some Paper Title")).isLeft)

  // ── verdicts ────────────────────────────────────────────────────────────────────────────────
  private def v(
      retr: List[RetractionNotice] = Nil,
      upd: List[UpdateNotice] = Nil,
      oaRetracted: Boolean = false,
      cited: Option[Int] = Some(10),
      year: Option[Int] = Some(2015)
  ) = Acquire.verdict(retr, upd, oaRetracted, cited, year, wellCitedAt = com.histmersion.api.library.Acquire.CitationThreshold(50), lowCitedAt = com.histmersion.api.library.Acquire.CitationThreshold(3), staleBefore = com.histmersion.api.catalogue.Year(2018))

  test("a Retraction Watch retraction, an OpenAlex flag or a Crossref retraction all mean retracted"):
    assertEquals(v(retr = List(RetractionNotice(Some(com.histmersion.api.library.RetractionNotice.Nature("Retraction")), None, None))), Verdicts.Retracted)
    assertEquals(v(oaRetracted = true), Verdicts.Retracted)
    assertEquals(v(upd = List(UpdateNotice(Some(com.histmersion.api.library.UpdateNotice.Kind("retraction")), None, None))), Verdicts.Retracted)
    assertEquals(v(upd = List(UpdateNotice(Some(com.histmersion.api.library.UpdateNotice.Kind("withdrawal")), None, None))), Verdicts.Retracted)

  test("a reinstatement is NOT a retraction"):
    assertEquals(
      v(retr = List(RetractionNotice(Some(com.histmersion.api.library.RetractionNotice.Nature("Reinstatement")), None, None)), cited = Some(80)),
      Verdicts.Corrected
    )

  test("corrections and expressions of concern mean corrected"):
    assertEquals(v(upd = List(UpdateNotice(Some(com.histmersion.api.library.UpdateNotice.Kind("correction")), None, None))), Verdicts.Corrected)
    assertEquals(v(upd = List(UpdateNotice(Some(com.histmersion.api.library.UpdateNotice.Kind("expression_of_concern")), None, None))), Verdicts.Corrected)
    assertEquals(v(retr = List(RetractionNotice(Some(com.histmersion.api.library.RetractionNotice.Nature("Correction")), None, None))), Verdicts.Corrected)

  test("citation metrics decide when nothing is flagged"):
    assertEquals(v(cited = Some(900)), Verdicts.Current)
    assertEquals(v(cited = Some(1), year = Some(2001)), Verdicts.LowImpact)
    assertEquals(v(cited = Some(1), year = Some(2024)), Verdicts.Current) // too new to judge by citations
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
    assert(Acquire.recommendation(Verdicts.Retracted, Some(com.histmersion.api.library.Acquire.CitedByCount(3))).startsWith("EXCLUDE"))
    assert(Acquire.recommendation(Verdicts.Current, Some(com.histmersion.api.library.Acquire.CitedByCount(90))).contains("90 citation(s)"))

  // ── follow-up classification ────────────────────────────────────────────────────────────────
  private def c(
      ctx: String,
      intents: List[String] = Nil,
      infl: Boolean = false,
      citing: Option[Int] = Some(2020),
      target: Option[Int] = Some(2015)
  ) =
    Acquire.classify(intents, List(ctx), infl, citing, target)

  test("refutation cues win over the support word they contain"):
    assertEquals(c("We find no support for the steppe hypothesis"), EdgeKinds.Refutes)
    assertEquals(c("These data contradict the earlier claim"), EdgeKinds.Refutes)
    assertEquals(c("The result could not be replicated"), EdgeKinds.Refutes)

  test("supersede, challenge and confirm cues"):
    assertEquals(c("this model is now obsolete"), EdgeKinds.Supersedes)
    assertEquals(c("However, the chronology is disputed"), EdgeKinds.Challenges)
    assertEquals(c("Our results are consistent with Haak et al."), EdgeKinds.Confirms)
    assertEquals(c("as background, see Haak et al."), EdgeKinds.Cites)

  test("an influential, much later result/method paper that reanalyses supersedes"):
    assertEquals(
      c(
        "we reanalyse the same samples with a larger dataset",
        List("result"),
        infl = true,
        citing = Some(2023),
        target = Some(2015)
      ),
      EdgeKinds.Supersedes
    )
    // not influential → merely cites (the same words, none of the metadata)
    assertEquals(
      c(
        "we reanalyse the same samples with a larger dataset",
        List("result"),
        infl = false,
        citing = Some(2023),
        target = Some(2015)
      ),
      EdgeKinds.Cites
    )
    // influential but published immediately after → not a superseding paper
    assertEquals(
      c("we reanalyse the samples", List("result"), infl = true, citing = Some(2016), target = Some(2015)),
      EdgeKinds.Cites
    )

  test("empty context is a plain citation"):
    assertEquals(Acquire.classify(Nil, Nil, false, None, None), EdgeKinds.Cites)

  test("rankFollowups: influential first, then result/method intent, then recency"):
    val rows = List(
      FollowupRow(title = Some(com.histmersion.api.library.Title("old plain")), year = Some(com.histmersion.api.catalogue.Year(2016))),
      FollowupRow(title = Some(com.histmersion.api.library.Title("influential")), year = Some(com.histmersion.api.catalogue.Year(2017)), isInfluential = true),
      FollowupRow(title = Some(com.histmersion.api.library.Title("recent plain")), year = Some(com.histmersion.api.catalogue.Year(2024))),
      FollowupRow(title = Some(com.histmersion.api.library.Title("method")), year = Some(com.histmersion.api.catalogue.Year(2018)), intents = List("methodology"))
    )
    assertEquals(
      Acquire.rankFollowups(rows).flatMap(_.title),
      List("influential", "method", "recent plain", "old plain")
    )

  // ── Retraction Watch CSV ────────────────────────────────────────────────────────────────────
  private def csv = scala.io.Source.fromResource("acquire/retraction_watch.csv").mkString

  test("Retraction Watch CSV parses by header name, through quoted commas and newlines"):
    val rows = Acquire.parseRetractionWatch(csv).flatMap(r => com.histmersion.domain.library.acquisition.Ident.doi(r.doi).map(Acquire.RetractionRow(_, r.reason, r.date, r.nature)))
    assertEquals(rows.size, 3)
    assertEquals(rows.head.doi, "10.1177/1758835919874651")
    assertEquals(rows.head.nature, Some("Retraction"))
    assert(rows.head.reason.exists(_.contains("Duplication of Image")))
    // the second row's Notes column contains a comma AND a newline inside quotes
    assertEquals(rows(1).doi, "10.1038/nature00000") // lower-cased by normalizeDoi
    assertEquals(rows(2).doi, "10.1126/science.yyy") // resolver prefix stripped
    assertEquals(rows(2).nature, Some("Reinstatement"))

  test("Retraction Watch dates become ISO, and junk becomes None"):
    assertEquals(Acquire.rwDate(Some(com.histmersion.api.library.RetractionNotice.Date("12/15/2021 0:00"))), Some("2021-12-15"))
    assertEquals(Acquire.rwDate(Some(com.histmersion.api.library.RetractionNotice.Date("2021-12-15"))), Some("2021-12-15"))
    assertEquals(Acquire.rwDate(Some(com.histmersion.api.library.RetractionNotice.Date(""))), None)
    assertEquals(Acquire.rwDate(Some(com.histmersion.api.library.RetractionNotice.Date("unknown"))), None)
    assertEquals(Acquire.rwDate(None), None)

  test("the CSV reader handles quotes, doubled quotes, empty fields and CRLF"):
    assertEquals(Csv.parse("""a,b,c""" + "\r\n" + """1,"x,y",""").head, List("a", "b", "c"))
    assertEquals(Csv.parse("a,b\n1,\"he said \"\"hi\"\"\"")(1), List("1", """he said "hi""""))
    assertEquals(Csv.parse("a\n\n\nb"), List(List("a"), List("b"))) // blank lines dropped
    assertEquals(Csv.parse(""), Nil)

  test("browserDownloadText: an open-access work no script may fetch, as a download in the browser (#1581)"):
    val w = WorkRef(
      title = (Some("Archaeology and Religion in Late Bronze Age Canaan")).map(com.histmersion.api.library.Title.apply),
      year = (Some(2019)).map(com.histmersion.api.catalogue.Year.apply),
      authors = (List("Aaron Greener")).map(com.histmersion.api.library.AuthorName.apply),
      isOa = com.histmersion.api.library.WorkRef.IsOa(true),
      oaStatus = Some(com.histmersion.api.library.WorkRef.OaStatus("gold")),
      oaUrl = Some(com.histmersion.api.library.WorkRef.OaUrl("https://www.mdpi.com/2077-1444/10/4/258/pdf"))
    )
    assertEquals(
      Acquire.browserDownloadText(w, com.histmersion.api.library.Doi("10.3390/rel10040258"), com.histmersion.api.library.Citekey("greener2019_archaeology")),
      Some(
        "Aaron Greener (2019). Archaeology and Religion in Late Bronze Age Canaan. https://doi.org/10.3390/rel10040258 — " +
          "gold open access at https://www.mdpi.com/2077-1444/10/4/258/pdf, but no script may download it: download it in the " +
          "browser, then histmersion ingest <file> --citekey greener2019_archaeology"
      )
    )
    // the DOI link is not repeated as the location; no status says just "open access"; a closed work gets none
    val doiOnly = w.modify(_.oaStatus).setTo(None).modify(_.oaUrl).setTo(Some("https://doi.org/10.3390/rel10040258")).modify(_.authors).setTo(List("A", "B").map(com.histmersion.api.library.AuthorName.apply))
    assert(
      Acquire
        .browserDownloadText(doiOnly, com.histmersion.api.library.Doi("10.3390/REL10040258"), com.histmersion.api.library.Citekey("k"))
        .exists(t =>
          t.startsWith("A et al. (2019).") && t.contains("— open access, but") && !t.contains(" at https://doi.org")
        )
    )
    assertEquals(Acquire.browserDownloadText(w.modify(_.isOa).setTo(false), com.histmersion.api.library.Doi("10.3390/rel10040258"), com.histmersion.api.library.Citekey("k")), None)

  test("browserDownloadText: a fetcher's refusal says where to download it and under which licence"):
    val w = WorkRef(
      title = (Some("Gods of the Bronze Age")).map(com.histmersion.api.library.Title.apply),
      year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply),
      authors = (List("A")).map(com.histmersion.api.library.AuthorName.apply),
      isOa = com.histmersion.api.library.WorkRef.IsOa(true),
      oaStatus = Some(com.histmersion.api.library.WorkRef.OaStatus("gold"))
    )
    val refusal = "oapen: an openly licensed copy exists but could not be downloaded: 20.500.12657/23223 " +
      "(https://library.oapen.org/handle/20.500.12657/23223), licence CC BY 4.0"
    val text = Acquire.browserDownloadText(w, com.histmersion.api.library.Doi("10.1515/zz"), com.histmersion.api.library.Citekey("k"), List(refusal, refusal, com.histmersion.api.library.Acquire.Refusal(" "))).getOrElse(fail("an item"))
    assert(text.contains(s"download it in the browser ($refusal), then histmersion ingest <file> --citekey k"), text)
    assertEquals(text.sliding(refusal.length).count(_ == refusal), 1, "each refusal once")
