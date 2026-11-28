package com.histmersion
package domain

import com.histmersion.domain.compilearticle.SourceFact
import com.histmersion.domain.compilearticle.Extracted
import com.histmersion.domain.compilearticle.EdgeFact
import com.histmersion.domain.compilearticle.Evidence
import com.histmersion.domain.compilearticle.NotUsed
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Content, Count, Raw, Where}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.Citekey
import com.histmersion.api.given

import com.histmersion.util.json.JsonCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonKeyCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Goal.Plans.ArticleDraft
import com.histmersion.api.library.Confidence
import com.histmersion.api.research.DraftMode
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The `compile-article` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.5, §14; the
  * five phases of `docs/writing-guide.md`): the claim table (one extraction per source, every claim with a quote code
  * finds in the record), the evidence model (statuses, the disagreement edges among the sources, due weight, the
  * Not-used list — all from the database), and the code self-check of the draft (links, citations per paragraph,
  * statuses stated inline, the section set, filler, no Sources cited heading). The server runs the steps; everything
  * here is pure and table-tested.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.ResearchWorkflows)
object CompileArticle:
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Last = Last.T
  object Last extends Newtype[Boolean, Pure]
  type LogLine = LogLine.T
  object LogLine extends Newtype[String, Pure]
  type MaxClaims = MaxClaims.T
  object MaxClaims extends Newtype[Int, Pure]
  type Written = Written.T
  object Written extends Newtype[Boolean, Pure]

  /** Why a compile run's write did what it did (`created`, `the draft was rejected`, the dry-run note): an outcome, not
    * a failure. Its JSON is the same string the checkpointed `write` step holds.
    */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  given citekeyKey: JsonKeyCodec[Citekey] =
    JsonCodec.key[Citekey](_.value, k => Citekey.option(k).toRight(s"not a citekey: '$k'"))

  /** The statuses a first citation must state (writing guide rule 2). */
  val StatedStatuses: Set[Source.Status] = Set(
    Source.Status.Contested,
    Source.Status.Superseded,
    Source.Status.Debunked,
    Source.Status.Retracted,
    Source.Status.Unassessed
  )

  val DisagreementKinds: Set[Article.Coverage.DisagreementKind] = Article.Coverage.DisagreementKinds.toSet

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def evidence(
      filter: ArticleDraft.Filter,
      total: RowCount,
      facts: List[SourceFact],
      extracted: List[Extracted],
      edges: List[EdgeFact]
  ): Evidence =
    val in = facts.map(_.citekey).toSet
    val counts = extracted.map(e => e.citekey -> Evidence.ClaimCount(e.claims.size)).toMap
    val kept = edges.filter(e => DisagreementKinds.contains(e.kind) && in(e.from) && in(e.to)).distinct
    val disputed = facts
      .filter(f => f.status != Source.Status.Current && f.status != Source.Status.Foundational)
      .map(_.citekey)
    val notUsed = extracted.collect {
      case e if e.claims.isEmpty =>
        NotUsed(e.citekey, e.notUsed.getOrElse(Coverage.Rationale("no claim")))
    } ++
      facts.collect {
        case f
            if f.status == Source.Status.Retracted && counts.getOrElse(f.citekey, Evidence.ClaimCount(0)).hasClaims =>
          NotUsed(f.citekey, Coverage.Rationale("retracted: may be cited only to note the retraction"))
      }
    Evidence(filter, total, facts, counts, kept, disputed, notUsed.distinctBy(_.citekey))

  // ── phase 4: the self-check (code) ──────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def blocks(body: Article.Body): List[String] =
    body.value.split("\\n\\s*\\n").toList.map(_.trim).filter(_.nonEmpty)

  /** Writing guide rule 2: the FIRST block citing a source whose status is not current or foundational must state that
    * status (the word itself, e.g. "contested").
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statusesSilent(body: Article.Body, statusOf: Citekey => Option[Source.Status]): List[Problem] =
    val bs = blocks(body)
    AgentVerify.wikilinks(AgentVerify.Text(body.value)).flatMap { ck =>
      Citekey.option(ck.value).flatMap(statusOf).filter(StatedStatuses.contains).flatMap { st =>
        bs.find(_.contains(s"[[$ck]]"))
          .filterNot(_.toLowerCase.contains(st.wire.value))
          .map(_ => Content.Answer(Content.Requirement.StatusUnstated(Raw.of(ck), Raw.of(st.wire))))
      }
    }

  /** The citekeys a text cites as EVIDENCE (links that are sources), leaving out its Not used section — the sources
    * listed there are named, not cited.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cited(text: Article.Body, isCitekey: Citekey => Boolean): List[Citekey] =
    AgentVerify
      .wikilinks(AgentVerify.Text(withoutNotUsed(text).value))
      .flatMap(w => Citekey.option(w.value))
      .filter(isCitekey)

  /** `text` without its "Not used" section (heading to the next heading of the same or higher level). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def withoutNotUsed(text: Article.Body): Article.Body = Article.Coverage.withoutNotUsed(text)

  /** What the article would read after the draft is applied: the whole body (create), or the current body with the text
    * appended (under `section`, else at the end).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def combined(
      mode: DraftMode,
      current: Article.Body,
      text: Article.Body,
      section: Option[Article.Section]
  ): Either[Problem, Article.Body] =
    mode match
      case DraftMode.Create => Right(text)
      case DraftMode.Extend =>
        section.map(s => Article.Section(s.value.trim)).filter(_.value.nonEmpty) match
          case Some(s) =>
            Article.appendUnderSection(current, s, Article.Prose(text.value.trim))
          case None =>
            Right(Article.Body(if current.value.trim.isEmpty then text.value.trim
            else s"${current.value.stripTrailing}\n\n${text.value.trim}"))

  /** The filter a compile run reads: the run's own (query, topics, tags) when it gives one; else the one the article
    * records (#958); else, for an existing article, its title as the query. It is what the article records afterwards.
    * `None` only when nothing at all selects a source.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def runFilter(
      query: Option[Article.Filter.Query],
      topics: List[Article.Filter.Topics],
      tags: List[Article.Filter.Tags],
      stored: Option[Article.Filter],
      existingTitle: Option[Title]
  ): Option[Article.Filter] =
    val stated = Article.Filter(query = query, topics = topics, tags = tags)
    Article.Filter
      .normalise(stated)
      .toOption
      .flatten
      .orElse(stored)
      .orElse(
        existingTitle.flatMap(t =>
          Article.Filter.normalise(Article.Filter(query = Some(Article.Filter.Query(t.value)))).toOption.flatten
        )
      )

  /** The log line of a compile run. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def logLine(d: ArticleDraft, written: Written, why: Rationale, review: Option[Run.Id], runId: Run.Id): LogLine =
    val what = d.mode match
      case DraftMode.Create => s"compiled a new article from ${d.cited.size} source(s)"
      case DraftMode.Extend =>
        s"appended a section${d.section.fold("")(s => s" under '$s'")} citing ${d.cited.size} source(s)"
    val outcome = if written.value then what else s"nothing written — $why"
    LogLine(
      s"compile-article [[${d.slug}]]: $outcome${review.fold("")(r => s"; review run ${r.value.take(8)} started")} (agent run ${runId.value.take(8)})"
    )
