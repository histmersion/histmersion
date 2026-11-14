package com.histmersion
package db

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.UUID

import scala.jdk.CollectionConverters.*

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.rng.Seed
import io.github.iltotore.iron.RefinedType
import hearth.kindlings.scalacheckderivation.extensions.*

import com.histmersion.api.automation.Agents.{RunParams, WouldWrite}
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Managers.{HintEffect, HintProposal, Hold, ItemState, ManagerConfig}
import com.histmersion.api.automation.ModelChains.LimitSpec
import com.histmersion.api.catalogue.{
  CatalogRecord,
  EventImportReport,
  EventPassage,
  EventRederiveReport,
  EventsJobKinds,
  Period,
  SkippedPeriod
}
import com.histmersion.api.jobs.{JobDetail, JobKinds}
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.maintenance.VacuumPlan
import com.histmersion.api.library.Acquire
import com.histmersion.api.exchange.{
  Bundle,
  ExchangeJobKinds,
  InitPlan,
  MergeApplyResult,
  MergeDecision,
  MergeResolutions
}
import com.histmersion.api.research.Article
import com.histmersion.domain.AgentProposals
import com.histmersion.domain.ReviewLedger.Ledger
import com.histmersion.domain.automation.approvals.ProposalEvidence
import com.histmersion.util.problem.{NonEmptyVector, Problem}
import com.histmersion.util.plugins.PluginKit
import com.histmersion.api.plugins.{PluginJar, PluginSet}
import StoredCodecs.{sameJson, BundleCodec, Codec, JsonAst, JsonSamples}
import com.histmersion.api.json.NewtypeCodecs.given

/** Every JSON shape the user's data holds — the `jsonb` columns and the `.hmx` bundle's lines — as the circe codecs
  * wrote it, kept as fixtures (`stored-shapes/<name>.jsonl`, one document per line) and read back by today's codecs:
  * each line must decode, and the decoded value must write the same document again (the same JSON value; a bundle line
  * the same bytes, since a record's `rev` is the sha256 of its line). A column written without its null fields
  * (`dropNulls`) has a second fixture of those lines, which must read as the full ones.
  *
  * `HISTMERSION_CAPTURE_STORED=1` writes the fixtures from random values (fixed seeds) through the current encoders;
  * they were captured once, with the circe codecs, before the codecs moved to jsoniter.
  */
class StoredShapesSuite extends munit.FunSuite:
  import StoredShapesSuite.*

  private val capture = sys.env.get("HISTMERSION_CAPTURE_STORED").contains("1")

  for shape <- shapes do
    test(s"${shape.name}: what the store holds reads back and writes the same again"):
      if capture then shape.capture()
      val lines = read(shape.name)
      assert(lines.nonEmpty, s"no fixture for ${shape.name}")
      lines.zipWithIndex.foreach((line, i) => shape.check(line).foreach(why => fail(s"${shape.name}:${i + 1}: $why")))
      shape.dropped.foreach { dropped =>
        val full = read(dropped.full)
        val without = read(shape.name)
        assertEquals(without.size, full.size)
        without.zip(full).zipWithIndex.foreach { case ((d, f), i) =>
          dropped.reads(d, f).foreach(why => fail(s"${shape.name}:${i + 1}: $why"))
        }
      }

object StoredShapesSuite:
  val Dir: Path = Path.of("modules/db/src/test/resources/stored-shapes")
  val Samples = 12

  def read(name: String): List[String] =
    val f = Dir.resolve(s"$name.jsonl")
    if Files.isRegularFile(f) then Files.readAllLines(f, UTF_8).asScala.toList.filter(_.nonEmpty) else Nil

  def write(name: String, lines: List[String]): Unit =
    Files.createDirectories(Dir)
    val _ = Files.write(Dir.resolve(s"$name.jsonl"), lines.map(_ + "\n").mkString.getBytes(UTF_8))

  final case class Dropped(full: String, reads: (String, String) => Option[String])

  final case class Shape(
      name: String,
      capture: () => Unit,
      check: String => Option[String],
      dropped: Option[Dropped] = None
  )

  def samples[A](gen: Gen[A], salt: Int): List[A] =
    (1 to Samples).toList.flatMap(i =>
      gen(Gen.Parameters.default.withSize(1 + i % 5), Seed(1000L * salt + i)).orElse(
        gen(Gen.Parameters.default.withSize(1), Seed(7L * salt + i))
      )
    )

  /** A `jsonb` column of `A`, written by `Jsonb.write`. */
  def column[A](name: String, salt: Int, gen: => Gen[A])(using c: Codec[A]): Shape =
    Shape(
      name,
      () => write(name, samples(gen, salt).map(c.write(_, false))),
      line =>
        c.decode(line) match
          case Left(e)  => Some(s"does not decode: $e")
          case Right(a) =>
            val again = c.write(a, false)
            Option.unless(sameJson(again, line))(s"writes $again")
    )

  /** The same column written without its null fields: each line reads as the value of the full line beside it. */
  def droppedColumn[A](name: String, full: String)(using c: Codec[A]): Shape =
    Shape(
      s"$name-dropnulls",
      () => write(s"$name-dropnulls", read(full).map(l => c.write(c.decode(l).toOption.get, true))),
      line => c.decode(line).left.toOption.map(e => s"does not decode: $e"),
      Some(
        Dropped(
          full,
          (d, f) =>
            c.decode(d).map(c.write(_, false)) match
              case Left(e)      => Some(s"does not decode: $e")
              case Right(again) => Option.unless(sameJson(again, f))(s"reads as $again, not as $f")
        )
      )
    )

  /** A bundle file's line: a stamped record's rev must check, and the record must write the same bytes again. */
  def bundle[A](name: String, salt: Int, gen: => Gen[A], stamped: Boolean = true)(using c: BundleCodec[A]): Shape =
    def line(a: A) = if stamped then c.stamp(a) else c.line(a)
    Shape(
      s"bundle-$name",
      () => write(s"bundle-$name", samples(gen, salt).map(line)),
      l =>
        c.decode(l, stamped) match
          case Left(e)  => Some(s"does not decode: $e")
          case Right(a) =>
            val again = line(a)
            Option.unless(again == l)(s"writes $again")
    )

  /** A file the server keeps on disk, written and read by its owner's own functions. */
  def file[A](
      name: String,
      salt: Int,
      gen: => Gen[A],
      render: A => String,
      parse: String => Either[Problem, A]
  ): Shape =
    Shape(
      s"file-$name",
      () => write(s"file-$name", samples(gen, salt).map(a => render(a).replace("\n", "\\n"))),
      l =>
        val text = l.replace("\\n", "\n")
        parse(text) match
          case Left(e)  => Some(s"does not decode: $e")
          case Right(a) =>
            val again = render(a)
            Option.unless(sameJson(again, text))(s"writes $again")
    )

  /** The same file as an earlier version wrote it (`file-<name>-<earlier>`, kept as captured): each line must read as
    * the value of the line beside it in today's fixture, which is what it writes again.
    */
  def earlierFile[A](name: String, earlier: String, render: A => String, parse: String => Either[Problem, A]): Shape =
    def text(line: String) = line.replace("\\n", "\n")
    Shape(
      s"file-$name-$earlier",
      () => (),
      l => parse(text(l)).left.toOption.map(e => s"does not decode: $e"),
      Some(
        Dropped(
          s"file-$name",
          (d, f) =>
            parse(text(d)).map(render) match
              case Left(e)      => Some(s"does not decode: $e")
              case Right(again) => Option.unless(sameJson(again, text(f)))(s"reads as $again, not as $f")
        )
      )
    )

  /** A `jsonb` column holding any JSON (a place's properties, a correction's values, the agents' state): what the store
    * prints must parse and print the same document again.
    */
  val anyJson: Shape =
    val fixed = List(
      "{\"n\":12345678901234567890.123456789,\"e\":1E+2,\"z\":-0,\"f\":0.1,\"i\":-9223372036854775808}",
      "[1.0,1,1e-7,2.5E10,null,true,false,\"\\u0001\\u001f\\\"\\\\/\",\"\u00e9\u2014\ud83d\ude00\"]",
      "{\"a\":{\"b\":[],\"c\":{}},\"a2\":null}",
      "{}",
      "[]"
    )
    Shape(
      "json-any",
      () => write("json-any", fixed ++ samples(JsonSamples.gen, 60).map(JsonAst.print)),
      l =>
        JsonAst.parse(l) match
          case None    => Some("does not parse")
          case Some(j) => Option.unless(sameJson(JsonAst.print(j), l))(s"prints ${JsonAst.print(j)}")
    )

  val shapes: List[Shape] =
    import Arbitraries.given
    List(
      column[JobDetail]("jobs-detail", 19, jobDetail),
      column[RunParams]("jobs-params", 1, Arbitrary.derived[RunParams].arbitrary),
      droppedColumn[RunParams]("jobs-params", "jobs-params"),
      column[MergeApplyResult]("jobs-result-merge-apply", 2, Arbitrary.derived[MergeApplyResult].arbitrary),
      column[List[WouldWrite]]("agent-steps-would-write", 3, Arbitrary.derived[List[WouldWrite]].arbitrary),
      column[Goal.Plans.Plan]("approvals-proposal-plan", 4, Arbitrary.derived[Goal.Plans.Plan].arbitrary),
      column[Goal.Plans.ArticleDraft](
        "approvals-proposal-draft",
        5,
        Arbitrary.derived[Goal.Plans.ArticleDraft].arbitrary
      ),
      column[HintProposal]("approvals-proposal-hint", 6, Arbitrary.derived[HintProposal].arbitrary),
      column[ProposalEvidence]("approvals-proposal-evidence", 7, Arbitrary.derived[ProposalEvidence].arbitrary),
      column[AgentProposals.Proposal](
        "approvals-proposal-source",
        8,
        Arbitrary.derived[AgentProposals.Proposal].arbitrary
      ),
      column[ManagerConfig]("agent-managers-config", 9, Arbitrary.derived[ManagerConfig].arbitrary),
      droppedColumn[ManagerConfig]("agent-managers-config", "agent-managers-config"),
      column[ItemState]("manager-items-state", 10, Arbitrary.derived[ItemState].arbitrary),
      droppedColumn[ItemState]("manager-items-state", "manager-items-state"),
      column[Hold]("manager-items-hold", 11, Arbitrary.derived[Hold].arbitrary),
      droppedColumn[Hold]("manager-items-hold", "manager-items-hold"),
      column[HintEffect]("manager-hints-effect", 12, Arbitrary.derived[HintEffect].arbitrary),
      droppedColumn[HintEffect]("manager-hints-effect", "manager-hints-effect"),
      column[Article.Filter]("articles-coverage-filter", 13, Arbitrary.derived[Article.Filter].arbitrary),
      column[CatalogRecord]("event-catalogue-record", 14, Arbitrary.derived[CatalogRecord].arbitrary),
      column[List[EventPassage]]("event-link-candidates-passages", 15, Arbitrary.derived[List[EventPassage]].arbitrary),
      column[MergeResolutions]("merges-resolutions", 16, Arbitrary.derived[MergeResolutions].arbitrary),
      column[Ledger]("review-ledgers-ledger", 17, Arbitrary.derived[Ledger].arbitrary),
      column[List[LimitSpec]]("agent-allowances-limits", 18, Arbitrary.derived[List[LimitSpec]].arbitrary),
      bundle[Bundle.Source]("sources", 30, Arbitrary.derived[Bundle.Source].arbitrary),
      bundle[Bundle.Finding]("findings", 31, Arbitrary.derived[Bundle.Finding].arbitrary),
      bundle[Bundle.Edge]("edges", 32, Arbitrary.derived[Bundle.Edge].arbitrary, stamped = false),
      bundle[Bundle.Assessment]("assessments", 33, Arbitrary.derived[Bundle.Assessment].arbitrary),
      bundle[Bundle.Alias]("aliases", 34, Arbitrary.derived[Bundle.Alias].arbitrary, stamped = false),
      bundle[Bundle.Article]("articles", 35, Arbitrary.derived[Bundle.Article].arbitrary),
      bundle[Bundle.ArticleVersion]("versions", 36, Arbitrary.derived[Bundle.ArticleVersion].arbitrary),
      bundle[Bundle.Asset]("assets", 37, Arbitrary.derived[Bundle.Asset].arbitrary),
      bundle[Bundle.AssetLink]("asset-links", 38, Arbitrary.derived[Bundle.AssetLink].arbitrary),
      bundle[Bundle.Attestation]("attestations", 39, Arbitrary.derived[Bundle.Attestation].arbitrary),
      bundle[Bundle.Event]("events", 40, Arbitrary.derived[Bundle.Event].arbitrary),
      bundle[Bundle.Goal]("goals", 41, Arbitrary.derived[Bundle.Goal].arbitrary),
      bundle[Bundle.QueueItem]("queue", 42, Arbitrary.derived[Bundle.QueueItem].arbitrary),
      bundle[Bundle.Tombstone]("tombstones", 43, Arbitrary.derived[Bundle.Tombstone].arbitrary, stamped = false),
      bundle[Bundle.Place]("places", 44, Arbitrary.derived[Bundle.Place].arbitrary),
      bundle[Bundle.Period]("periods", 45, Arbitrary.derived[Bundle.Period].arbitrary),
      bundle[Bundle.EventCatalogue]("event-catalogue", 46, Arbitrary.derived[Bundle.EventCatalogue].arbitrary),
      bundle[Bundle.EventCandidate]("event-candidates", 47, Arbitrary.derived[Bundle.EventCandidate].arbitrary),
      bundle[Bundle.EventCheck]("event-checks", 48, Arbitrary.derived[Bundle.EventCheck].arbitrary),
      bundle[Bundle.Correction]("corrections", 49, Arbitrary.derived[Bundle.Correction].arbitrary),
      bundle[Bundle.SiteType]("site-types", 50, Arbitrary.derived[Bundle.SiteType].arbitrary),
      bundle[Bundle.ArticleAlias]("article-aliases", 51, Arbitrary.derived[Bundle.ArticleAlias].arbitrary, false),
      bundle[Bundle.EventAlias]("event-aliases", 52, Arbitrary.derived[Bundle.EventAlias].arbitrary, false),
      bundle[Bundle.GoalAlias]("goal-aliases", 53, Arbitrary.derived[Bundle.GoalAlias].arbitrary, false),
      bundle[Bundle.PlaceAlias]("place-aliases", 54, Arbitrary.derived[Bundle.PlaceAlias].arbitrary, false),
      bundle[Bundle.Manifest]("manifest", 55, Arbitrary.derived[Bundle.Manifest].arbitrary, stamped = false),
      file[PluginSet](
        "plugin-set",
        70,
        Arbitrary.derived[PluginSet].arbitrary,
        PluginKit.renderSet,
        PluginKit.parseSet
      ),
      file[List[PluginJar]](
        "plugin-jars",
        71,
        Arbitrary.derived[List[PluginJar]].arbitrary,
        PluginKit.renderJars,
        PluginKit.parseJars
      ),
      file[PluginKit.Index](
        "plugin-index",
        72,
        Arbitrary.derived[PluginKit.Index].arbitrary,
        PluginKit.renderIndex,
        PluginKit.parseIndex
      ),
      // a plugin's problems were written bare when there was one; they are a list now, and the bare form still reads
      earlierFile[PluginSet]("plugin-set", "bare", PluginKit.renderSet, PluginKit.parseSet),
      earlierFile[List[PluginJar]]("plugin-jars", "bare", PluginKit.renderJars, PluginKit.parseJars),
      earlierFile[PluginKit.Index]("plugin-index", "bare", PluginKit.renderIndex, PluginKit.parseIndex),
      file[PluginKit.KitManifest](
        "kit-manifest",
        73,
        Arbitrary
          .derived[PluginKit.KitManifest]
          .arbitrary
          .map(k =>
            PluginKit
              .KitManifest(PluginKit.KitFormat, k.version, k.commit, k.graalvm, k.spiVersion, k.mainClass, k.files)
          ),
        PluginKit.renderKit,
        PluginKit.parseKit
      ),
      anyJson
    )

  /** A job's detail: each field its kinds' owners declare, set or left at its default. */
  private def jobDetail: Gen[JobDetail] =
    import Arbitraries.given
    def field[A](f: JobDetail.Field[A], gen: Gen[A])(d: JobDetail): Gen[JobDetail] =
      Gen.oneOf(Gen.const(d), gen.map(d.set(f, _)))
    for
      a <- field(JobDetail.Purpose, Arbitrary.derived[Option[JobKinds.Subject]].arbitrary)(JobDetail.empty)
      b <- field(ImportJobKinds.Media, Arbitrary.derived[Option[JobKinds.Subject]].arbitrary)(a)
      c <- field(EventsJobKinds.Catalogue, Arbitrary.derived[Option[JobKinds.Subject]].arbitrary)(b)
      d <- field(ExchangeJobKinds.PlanHash, Arbitrary.derived[Option[ExchangeJobKinds.PlanHash]].arbitrary)(c)
      e <- field(EventImportReport.Detail, Arbitrary.derived[Option[EventImportReport]].arbitrary)(d)
      f <- field(EventRederiveReport.Detail, Arbitrary.derived[Option[EventRederiveReport]].arbitrary)(e)
      g <- field(SkippedPeriod.Skipped, Arbitrary.derived[List[SkippedPeriod]].arbitrary)(f)
      h <- field(SkippedPeriod.SkippedCount, summon[Arbitrary[Period.ImportResult.SkippedCount]].arbitrary)(g)
      i <- field(InitPlan.Detail, Arbitrary.derived[Option[InitPlan]].arbitrary)(h)
      j <- field(VacuumPlan.Detail, Arbitrary.derived[Option[VacuumPlan]].arbitrary)(i)
      k <- field(Acquire.Checks.RecheckJob.Detail, Arbitrary.derived[Option[Acquire.Checks.RecheckJob]].arbitrary)(j)
    yield k

  /** The values a stored document can hold: printable text with the characters JSON must escape, finite numbers,
    * instants as the store writes them.
    */
  object Arbitraries:
    private val chars: Gen[Char] =
      Gen.frequency(
        20 -> Gen.alphaNumChar,
        3 -> Gen.oneOf(" -_.,:;/'"),
        1 -> Gen.oneOf("\"\\\n\t\r\u0001\u001fé—€ę𓂀".toList)
      )
    private val identifier: Gen[String] =
      Gen
        .zip(Gen.alphaLowerChar, Gen.nonEmptyListOf(Gen.oneOf(Gen.alphaLowerChar, Gen.numChar, Gen.oneOf('-', '_'))))
        .map((h, t) => (h :: t.take(12)).mkString)
    private val hex64: Gen[String] = Gen.listOfN(64, Gen.hexChar).map(_.mkString.toLowerCase)
    given Arbitrary[String] =
      Arbitrary(Gen.oneOf(identifier, Gen.listOf(chars).map(_.mkString), Gen.uuid.map(_.toString), hex64))
    given Arbitrary[MergeDecision] = Arbitrary(
      Gen
        .oneOf(MergeDecision.Kind.values.toList)
        .flatMap(k =>
          if k.takesArgument then identifier.map(a => MergeDecision.withArg(k, MergeDecision.Argument(s"$a")))
          else Gen.const(MergeDecision.of(k))
        )
    )
    given Arbitrary[Double] = Arbitrary(
      Gen.oneOf(Gen.choose(-1e6, 1e6), Gen.choose(-90.0, 90.0), Gen.oneOf(0.0, -0.0, 1.0, 0.1, 1e-7, 1e21, 12345.678))
    )
    given Arbitrary[Float] = Arbitrary(Gen.choose(-1000f, 1000f))
    given Arbitrary[BigDecimal] = Arbitrary(Gen.choose(-1000000L, 1000000L).map(n => BigDecimal(n, 3)))
    given Arbitrary[Instant] =
      Arbitrary(
        Gen
          .choose(0L, 4102444800000L)
          .flatMap(ms => Gen.oneOf(0, 123000000, 1).map(n => Instant.ofEpochMilli(ms).plusNanos(n.toLong)))
      )
    given Arbitrary[UUID] = Arbitrary(Gen.uuid)
    // the base type's values the constraint accepts, lifted as `NewtypeCodecs` lifts the base type's codec
    given [T] => (m: RefinedType.Mirror[T]) => (base: Arbitrary[m.BaseType]) => Arbitrary[T] =
      Arbitrary(base.arbitrary.map(m.ops.either).retryUntil(_.isRight, 1000).map(_.toOption.get.asInstanceOf[T]))
    given Arbitrary[Problem] = Arbitrary(Gen.const(Problem.Run.Interrupted))
    given Arbitrary[NonEmptyVector[Problem]] =
      Arbitrary(
        Gen.const(NonEmptyVector.of[Problem](Problem.Run.Interrupted, Problem.Run.Cancelled(Problem.Run.Actor.Person)))
      )
    given Arbitrary[JsonAst] = Arbitrary(JsonSamples.gen)
