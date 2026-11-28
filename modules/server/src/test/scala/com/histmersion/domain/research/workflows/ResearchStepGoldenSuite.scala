package com.histmersion
package domain.research.workflows

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import com.histmersion.api.automation.Goal.Plans.ArticleDraft
import com.histmersion.api.library.{Confidence, Source}
import com.histmersion.api.research.{
  Article,
  CitedSource,
  CritiquePair,
  CritiqueReason,
  CritiqueRef,
  CritiqueSide,
  CritiqueState
}
import com.histmersion.api.research.Coverage
import com.histmersion.domain.{AgentVerify, CheckpointGolden, Proposal, ReviewArticle, Title}
import com.histmersion.domain.CheckpointGolden.Golden
import com.histmersion.domain.library.Citekey
import com.histmersion.util.json.RawJson.*
import com.histmersion.testsupport.CheckpointCodecs.given
import ResearchTransformers.given
import CompileStepTransformers.given

class ResearchStepGoldenSuite extends munit.FunSuite:

  private val ck = Citekey("smith1987_tin")
  private val apiCk = api.library.Citekey("smith1987_tin")
  private val critic = api.library.Citekey("doe2001_tin")

  private val claim = Claim(
    ck,
    ClaimAnswer.Claim("Tin reached the Aegean from Afghanistan."),
    Proposal.Quote("tin came from the east"),
    Some(ClaimAnswer.Date("c. 2500 BCE")),
    Some(ClaimAnswer.Place("Aegean")),
    Confidence.High,
    AgentVerify.Ratio(0.97)
  )

  private val sourceFact = SourceFact(ck, Title("Tin and trade"), Some(Year(1987)), Source.Status.Current)

  private val all: List[Golden[?]] = List(
    Golden(
      "review-amended.ndjson",
      List(
        Amended(Amended.Written(true), Some(Amended.PreviousVersion(4L)), Amended.Conflict(false)),
        Amended(Amended.Written(false), None, Amended.Conflict(true))
      )
    ),
    Golden(
      "review-snapshot.ndjson",
      List(
        Snapshot(
          Snapshot.Slug("tin_trade"),
          Snapshot.Title("Tin trade"),
          Snapshot.Kind("concept"),
          Snapshot.Body("## Origins\n\nTin came [[smith1987_tin]]."),
          Some(Snapshot.UpdatedAt("2026-10-25T10:00:00Z")),
          Snapshot.Truncated(true),
          None,
          List(
            CitedSource(
              apiCk,
              Some(api.library.Title("Tin and trade")),
              Some(api.catalogue.Year(1987)),
              Some(Source.Status.Current)
            )
          ),
          List(
            CritiquePair(
              CritiqueSide(critic),
              List(CritiquePair.Kinds("challenges")),
              CritiqueSide(apiCk),
              CritiqueState.Open
            )
          )
        ),
        Snapshot(
          Snapshot.Slug("empty"),
          Snapshot.Title(""),
          Snapshot.Kind("index"),
          Snapshot.Body(""),
          None,
          Snapshot.Truncated(false)
        )
      )
    ),
    Golden(
      "review-recheck.ndjson",
      List(
        Recheck(
          Reviewer.Rules,
          List(Check(Check.Id("R1"), Check.Fixed(false), Check.Quote("a quote"), Check.Note("still there"))),
          CheckpointGolden.reviewFindings.values.take(1),
          List(Recheck.Lessons("Quote the record."))
        ),
        Recheck(Reviewer.Skeptic, Nil, Nil)
      )
    ),
    Golden(
      "review-fix.ndjson",
      List(
        Fix(
          Fix.Body("## Origins\n\nTin came."),
          List(
            Article.Edits.EditResult(
              Article.Edits.EditResult.Index(0),
              Article.Edits.Op.Replace,
              Article.Edits.EditResult.Anchor("Tin came"),
              Article.Edits.EditResult.Text("Tin came from the east")
            )
          ),
          List(Fix.Applied("S1")),
          List(
            Rejection(
              Rejection.Id("R2"),
              ReviewArticle.Rationale("The record says otherwise."),
              ck,
              Rejection.Quote("the route is later"),
              Rejection.Ratio(0.91)
            )
          ),
          List(Fix.Unverified("C1")),
          List(Fix.UnresolvedMoved("Where was the tin mined?")),
          Fix.Notes("Two fixes."),
          Fix.WordsBefore(120),
          Fix.WordsAfter(124),
          List(Coverage.Reason(critic, Coverage.Rationale("Not about the route."))),
          List(WontFix(WontFix.Id("C2"), ReviewArticle.Rationale("A style preference."))),
          List(SetAside(SetAside.Id("S3"), CritiqueRef(critic, apiCk), Coverage.Rationale("Not material."))),
          List(Fix.Repaired("M1")),
          List(Fix.Repairs("closed a heading")),
          List(WontFix(WontFix.Id("S4"), ReviewArticle.Rationale("Out of scope."))),
          List(CritiqueReason(critic, apiCk, Coverage.Rationale("Not material.")))
        ),
        Fix(Fix.Body(""), Nil, Nil, Nil, Nil, Nil, Fix.Notes(""), Fix.WordsBefore(0), Fix.WordsAfter(0))
      )
    ),
    Golden(
      "compile-extracted.ndjson",
      List(
        Extracted(ck, List(claim, claim.copy(date = None, place = None, confidence = Confidence.Contested)), None),
        Extracted(Citekey("doe2001_tin"), Nil, Some(Coverage.Rationale("No finding on the question.")))
      )
    ),
    Golden(
      "compile-support.ndjson",
      List(
        Support(
          ArticleDraft.Filter("tin AND trade"),
          RowCount(2),
          List(sourceFact, SourceFact(Citekey("doe2001_tin"), Title("Against"), None, Source.Status.Contested)),
          Map(ck -> Support.ClaimCount(2), Citekey("doe2001_tin") -> Support.ClaimCount(0)),
          List(EdgeFact(Citekey("doe2001_tin"), Article.Coverage.DisagreementKind("challenges"), ck)),
          List(Citekey("doe2001_tin")),
          List(NotUsed(Citekey("roe1990_tin"), Coverage.Rationale("Wrong region.")))
        ),
        Support(ArticleDraft.Filter(""), RowCount(0), Nil, Map.empty, Nil, Nil, Nil)
      )
    ),
    Golden(
      "compile-found.ndjson",
      List(
        CompileArticlePipeline.Found(ArticleDraft.Filter("tin AND trade"), RowCount(1), List(sourceFact), List(ck)),
        CompileArticlePipeline.Found(ArticleDraft.Filter(""), RowCount(0), Nil, Nil)
      )
    )
  )

  private val update = sys.env.get("HISTMERSION_UPDATE_GOLDEN").contains("1")

  private def file(name: String) =
    Iterator
      .iterate(Paths.get(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .map(_.resolve("modules/server/src/test/resources"))
      .find(Files.isDirectory(_))
      .get
      .resolve(s"golden/checkpoints/$name")

  private def encoded[A](g: Golden[A]): List[String] = g.values.map(_.asJson(using g.encoder).noSpaces)

  private def check[A](g: Golden[A]): Unit =
    val path = file(g.file)
    if update then
      Files.createDirectories(path.getParent)
      Files.write(path, encoded(g).mkString("", "\n", "\n").getBytes(UTF_8))
    val stored = Files.readAllLines(path, UTF_8).toArray(Array.empty[String]).toList.filter(_.nonEmpty)
    assert(stored.nonEmpty)
    stored.foreach { line =>
      val again =
        decode(line)(using g.decoder).fold(e => fail(s"${g.file}: $e\n$line"), _.asJson(using g.encoder).noSpaces)
      assertEquals(again, line)
    }
    assertEquals(encoded(g), stored)

  all.foreach(g =>
    test(s"${g.file}: stored lines decode and re-encode byte for byte, the samples encode to them")(check(g))
  )
