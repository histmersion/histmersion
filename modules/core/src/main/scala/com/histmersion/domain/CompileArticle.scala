package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.research.Articles
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Goal.Plans.ArticleDraft
import com.histmersion.api.library.Confidence
import com.histmersion.api.research.DraftMode
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.api.json.NewtypeCodecs.given

/** The `compile-article` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.5, §14; the
  * five phases of `docs/writing-guide.md`): the claim table (one extraction per source, every claim with a quote code
  * finds in the record), the evidence model (statuses, the disagreement edges among the sources, due weight, the
  * Not-used list — all from the database), and the code self-check of the draft (links, citations per paragraph,
  * statuses stated inline, the section set, filler, no Sources cited heading). The server runs the steps; everything
  * here is pure and table-tested.
  */
object CompileArticle:
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsTarget = IsTarget.T
  object IsTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Known = Known.T
  object Known extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Last = Last.T
  object Last extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogLine = LogLine.T
  object LogLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxClaims = MaxClaims.T
  object MaxClaims extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinRatio = MinRatio.T
  object MinRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Record = Record.T
  object Record extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReviewText = ReviewText.T
  object ReviewText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Written = Written.T
  object Written extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── phase 1: extraction ─────────────────────────────────────────────────────────────────────────

  type DisagreementKinds = DisagreementKindsValue
  type EvidenceFilter = _root_.com.histmersion.domain.CompileArticle.Evidence.Filter
  val EvidenceFilter: _root_.com.histmersion.domain.CompileArticle.Evidence.Filter.type = _root_.com.histmersion.domain.CompileArticle.Evidence.Filter
  type StatusesSilentBody = _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body
  val StatusesSilentBody: _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body.type = _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body
  type CitedText = _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body
  val CitedText: _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body.type = _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body
  type CheckDraftCurrent = _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body
  val CheckDraftCurrent: _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body.type = _root_.com.histmersion.domain.CompileArticle.DraftAnswer.Body

  final case class ClaimAnswer(
      claim: ClaimAnswer.Claim,
      quote: ClaimAnswer.Quote,
      date: Option[ClaimAnswer.Date] = None,
      place: Option[ClaimAnswer.Place] = None,
      confidence: ClaimAnswer.Confidence
  ) derives KindlingsCodecAsObject
  object ClaimAnswer:
    type Claim = Claim.T
    object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Date = Date.T
    object Date extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Place = Place.T
    object Place extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Confidence = Confidence.T
    object Confidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ClaimAnswer] = KindlingsSchema
      .derived[ClaimAnswer]
      .schema
      .modify(_.claim)(
        _.description("One atomic claim (one fact), in your words, that bears on the article's question")
      )
      .modify(_.quote)(_.description("The sentence of the record that states it, copied EXACTLY"))
      .modify(_.date)(_.description("The date or period the claim is about, as the record gives it; null if none"))
      .modify(_.place)(_.description("The place the claim is about, as the record names it; null if none"))
      .modify(_.confidence)(_.description("high | medium | low | contested — how firmly the record states it"))

  final case class ExtractAnswer(relevant: ExtractAnswer.Relevant, reason: ExtractAnswer.Reason, claims: List[ClaimAnswer] = Nil)
      derives KindlingsCodecAsObject
  object ExtractAnswer:
    type Relevant = Relevant.T
    object Relevant extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ExtractAnswer] = KindlingsSchema
      .derived[ExtractAnswer]
      .schema
      .modify(_.relevant)(_.description("Does the record bear on the article's question at all?"))
      .modify(_.reason)(
        _.description("If not relevant: why (wrong region, period or sub-problem). Otherwise what it contributes")
      )
      .modify(_.claims)(_.description("Every claim of the record that bears on the question; empty if not relevant"))

  /** A claim whose quote code found in the record. `ratio` is the OCR-tolerant match share. */
  final case class Claim(
      citekey: com.histmersion.domain.library.Citekey,
      claim: Claim.Value,
      quote: Claim.Quote,
      date: Option[Claim.Date],
      place: Option[Claim.Place],
      confidence: Confidence,
      ratio: Claim.Ratio
  ) derives KindlingsCodecAsObject

  object Claim:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Date = Date.T
    object Date extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Place = Place.T
    object Place extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ratio = Ratio.T
    object Ratio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One source's verified extraction (checkpointed): its claims, or why it is not used. */
  final case class Extracted(citekey: com.histmersion.domain.library.Citekey, claims: List[Claim], notUsed: Option[Extracted.NotUsed])
      derives KindlingsCodecAsObject

  object Extracted:
    type NotUsed = NotUsed.T
    object NotUsed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Check one extraction: every quote must be in `record` (summary, findings, notes, body excerpt), every confidence
    * in the vocabulary; a source judged irrelevant must say why. Unverifiable claims send the answer back; on the last
    * attempt they are dropped and the rest kept.
    */
  def checkExtract(
      a: ExtractAnswer,
      citekey: com.histmersion.domain.library.Citekey,
      record: com.histmersion.domain.CompileArticle.Record,
      minRatio: com.histmersion.domain.CompileArticle.MinRatio,
      maxClaims: com.histmersion.domain.CompileArticle.MaxClaims,
      last: com.histmersion.domain.CompileArticle.Last
  ): Either[List[com.histmersion.domain.CompileArticle.Problem], (Extracted, List[com.histmersion.domain.CompileArticle.Description])] =
    val checked = a.claims.zipWithIndex.map { (c, i) =>
      val label = s"claim ${i + 1} (${Option(c.claim).getOrElse("").take(50)})"
      if Option(c.claim).forall(_.trim.isEmpty) then Left(s"$label: no claim")
      else
        AgentVerify.confidence(Option(c.confidence).getOrElse("")) match
          case Left(m)     => Left(s"$label: $m")
          case Right(conf) =>
            val q = Option(c.quote).map(_.trim).getOrElse("")
            val m = AgentVerify.findQuote(q, record, minRatio)
            if q.isEmpty then Left(s"$label: no quote")
            else if !m.accepted then Left(s"$label: the quote is not in the record of $citekey — copy it exactly")
            else
              Right(
                Claim(
                  citekey,
                  c.claim.trim,
                  q,
                  c.date.map(_.trim).filter(_.nonEmpty),
                  c.place.map(_.trim).filter(_.nonEmpty),
                  conf,
                  m.ratio
                )
              )
    }
    val claims = checked.collect { case Right(c) => c }.distinctBy(c => AgentVerify.normalise(c.claim))
    val tooMany =
      Option.when(claims.size > maxClaims)(s"at most $maxClaims claims per source (you gave ${claims.size})")
    val reason = Option(a.reason).map(_.trim).filter(_.nonEmpty)
    val noReason = Option.when((!a.relevant || a.claims.isEmpty) && reason.isEmpty)("say why the record is not used")
    val problems = checked.collect { case Left(m) => m } ++ tooMany.toList ++ noReason.toList
    if problems.nonEmpty && !last then Left(problems)
    else
      val kept = if a.relevant then claims.take(maxClaims) else Nil
      val notUsed = Option.when(kept.isEmpty)(reason.getOrElse("no claim could be verified in its record"))
      Right((Extracted(citekey, kept, notUsed), problems.map(p => s"extract $citekey: dropped — $p")))

  // ── phase 2: the evidence model (code, from the database) ───────────────────────────────────────

  /** What the database says about one source the filter returned. */
  final case class SourceFact(citekey: com.histmersion.domain.library.Citekey, title: com.histmersion.domain.Title, year: Option[com.histmersion.Year], status: Source.Status)
      derives KindlingsCodecAsObject

  /** A disagreement edge between two sources of the filter (only citekey targets count). */
  final case class EdgeFact(from: com.histmersion.domain.library.Citekey, kind: EdgeFact.Kind, to: com.histmersion.domain.library.Citekey) derives KindlingsCodecAsObject

  object EdgeFact:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The evidence model: the sources with their statuses and claim counts (due weight), the disagreement edges among
    * them, the disputed ones (a non-current status), and the Not-used list with reasons. `filter` is the filter line
    * for the `_Method_` note.
    */
  /** A source the article leaves out, and why. */
  final case class NotUsed(citekey: com.histmersion.domain.library.Citekey, reason: NotUsed.Reason) derives KindlingsCodecAsObject

  object NotUsed:
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Evidence(
      filter: Evidence.Filter,
      total: com.histmersion.RowCount,
      sources: List[SourceFact],
      claims: Map[Evidence.ClaimsKey, Evidence.ClaimsValue],
      edges: List[EdgeFact],
      disputed: List[Evidence.Disputed],
      notUsed: List[NotUsed]
  ) derives KindlingsCodecAsObject:
    def used: List[com.histmersion.domain.library.Citekey] = sources.map(_.citekey).filter(ck => claims.getOrElse(ck, 0) > 0)

  object Evidence:

    type Filter = Filter.T
    object Filter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClaimsKey = ClaimsKey.T
    object ClaimsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClaimsValue = ClaimsValue.T
    object ClaimsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Disputed = Disputed.T
    object Disputed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The statuses a first citation must state (writing guide rule 2). */
  val StatedStatuses: Set[Source.Status] = Set(
    Source.Status.Contested,
    Source.Status.Superseded,
    Source.Status.Debunked,
    Source.Status.Retracted,
    Source.Status.Unassessed
  )

  val DisagreementKinds: Set[com.histmersion.domain.CompileArticle.DisagreementKinds] = Article.Coverage.DisagreementKinds.toSet

  def evidence(
      filter: com.histmersion.domain.CompileArticle.EvidenceFilter,
      total: com.histmersion.RowCount,
      facts: List[SourceFact],
      extracted: List[Extracted],
      edges: List[EdgeFact]
  ): Evidence =
    val in = facts.map(_.citekey).toSet
    val counts = extracted.map(e => e.citekey -> e.claims.size).toMap
    val kept = edges.filter(e => DisagreementKinds.contains(e.kind) && in(e.from) && in(e.to)).distinct
    val disputed = facts
      .filter(f => f.status != Source.Status.Current && f.status != Source.Status.Foundational)
      .map(_.citekey)
    val notUsed = extracted.collect {
      case e if e.claims.isEmpty => NotUsed(e.citekey, e.notUsed.getOrElse("no claim"))
    } ++
      facts.collect {
        case f if f.status == Source.Status.Retracted && counts.getOrElse(f.citekey, 0) > 0 =>
          NotUsed(f.citekey, com.histmersion.domain.CompileArticle.NotUsed.Reason("retracted: may be cited only to note the retraction"))
      }
    Evidence(filter, total, facts, counts, kept, disputed, notUsed.distinctBy(_.citekey))

  /** The claim table and evidence model as the writer reads them (data, not instructions). */
  def evidenceText(ev: Evidence, extracted: List[Extracted]): com.histmersion.domain.CompileArticle.Description =
    val bySource = extracted.map(e => e.citekey -> e).toMap
    val sources = ev.sources.filter(s => ev.claims.getOrElse(s.citekey, 0) > 0).map { s =>
      val claims = bySource.get(s.citekey).toList.flatMap(_.claims).map { c =>
        val when = c.date.fold("")(d => s" · date: $d")
        val where = c.place.fold("")(p => s" · place: $p")
        s"  - ${c.claim} (confidence ${c.confidence.toString.toLowerCase}$when$where)\n    quote: \"${c.quote}\""
      }
      s"- [[${s.citekey}]] — ${s.title} (${s.year.fold("n.d.")(_.toString)}), status **${s.status.wire}**\n" +
        claims.mkString("\n")
    }
    val edges = if ev.edges.isEmpty then "none recorded among these sources (not evidence of agreement)"
    else ev.edges.map(e => s"[[${e.from}]] ${e.kind} [[${e.to}]]").mkString("; ")
    val weight = ev.claims.toList.filter(_._2 > 0).sortBy(-_._2).map((ck, n) => s"[[$ck]] $n").mkString(", ")
    val notUsed =
      if ev.notUsed.isEmpty then "none" else ev.notUsed.map(n => s"[[${n.citekey}]]: ${n.reason}").mkString("\n- ")
    s"Filter: ${ev.filter} → ${ev.total}\n\n## Claim table\n\n${sources.mkString("\n")}\n\n" +
      s"## Evidence model\n\n- disagreement edges among the sources: $edges\n" +
      (if ev.edges.isEmpty then ""
       else
         "- the critique checklist: each edge is answered when its two sources are cited in ONE paragraph — the one that " +
           "states the criticised claim (the target of challenges/refutes/updates; the older work of superseded_by), with the " +
           "critique in a sentence from the critic's claims. A paragraph that names an author cites that author's work there.\n") +
      s"- sources with a non-current status: ${
          if ev.disputed.isEmpty then "none" else ev.disputed.map(c => s"[[$c]]").mkString(", ")
        }\n" +
      s"- claims per source (due weight): $weight\n\n## Not used\n\n- $notUsed"

  // ── phase 3: the draft ──────────────────────────────────────────────────────────────────────────

  final case class DraftAnswer(
      title: DraftAnswer.Title,
      summary: DraftAnswer.Summary,
      body: DraftAnswer.Body,
      section: Option[DraftAnswer.Section] = None,
      reasons: List[Coverage.Reason] = Nil
  ) derives KindlingsCodecAsObject
  object DraftAnswer:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Body = Body.T
    object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Section = Section.T
    object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[DraftAnswer] = KindlingsSchema
      .derived[DraftAnswer]
      .schema
      .modify(_.title)(_.description("The article's title (a new article); repeat the current title when extending"))
      .modify(_.summary)(_.description("One or two plain sentences: what the article answers"))
      .modify(_.body)(_.description("New article: the WHOLE body in Markdown. Extending: ONLY the new text to add"))
      .modify(_.section)(
        _.description(
          "Extending: the existing heading to add the text under (e.g. '## Disagreements'); null = at the end"
        )
      )
      .modify(_.reasons)(
        _.description(
          "For each source of the claim table you do NOT cite: one plain sentence saying why it is left out " +
            "(code lists it under the generated Not used section)"
        )
      )

  // ── phase 4: the self-check (code) ──────────────────────────────────────────────────────────────

  private def blocks(body: com.histmersion.domain.CompileArticle.DraftAnswer.Body): List[String] =
    body.split("\\n\\s*\\n").toList.map(_.trim).filter(_.nonEmpty)

  /** Writing guide rule 2: the FIRST block citing a source whose status is not current or foundational must state that
    * status (the word itself, e.g. "contested").
    */
  def statusesSilent(body: com.histmersion.domain.CompileArticle.StatusesSilentBody, statusOf: com.histmersion.domain.library.Citekey => Option[Source.Status]): List[com.histmersion.domain.CompileArticle.Description] =
    val bs = blocks(body)
    AgentVerify.wikilinks(body).flatMap { ck =>
      statusOf(ck).filter(StatedStatuses.contains).flatMap { st =>
        bs.find(_.contains(s"[[$ck]]"))
          .filterNot(_.toLowerCase.contains(st.wire))
          .map(_ => s"the first citation of [[$ck]] does not say it is ${st.wire}")
      }
    }

  /** The citekeys a text cites as EVIDENCE (links that are sources), leaving out its Not used section — the sources
    * listed there are named, not cited.
    */
  def cited(text: com.histmersion.domain.CompileArticle.CitedText, isCitekey: com.histmersion.domain.library.Citekey => Boolean): List[com.histmersion.domain.library.Citekey] =
    AgentVerify.wikilinks(withoutNotUsed(text)).filter(isCitekey)

  /** `text` without its "Not used" section (heading to the next heading of the same or higher level). */
  def withoutNotUsed(text: com.histmersion.domain.CompileArticle.DraftAnswer.Body): com.histmersion.domain.CompileArticle.DraftAnswer.Body = Article.Coverage.withoutNotUsed(text)

  /** What the article would read after the draft is applied: the whole body (create), or the current body with the text
    * appended (under `section`, else at the end).
    */
  def combined(mode: DraftMode, current: com.histmersion.domain.CompileArticle.DraftAnswer.Body, text: com.histmersion.domain.CompileArticle.DraftAnswer.Body, section: Option[com.histmersion.domain.CompileArticle.DraftAnswer.Section]): Either[com.histmersion.domain.CompileArticle.Problem, com.histmersion.domain.CompileArticle.DraftAnswer.Body] =
    mode match
      case DraftMode.Create => Right(text)
      case DraftMode.Extend =>
        section.map(_.trim).filter(_.nonEmpty) match
          case Some(s) => Articles.appendUnderSection(current, s, text.trim)
          case None    => Right(if current.trim.isEmpty then text.trim else s"${current.stripTrailing}\n\n${text.trim}")

  /** The inputs of the self-check that come from the corpus. */
  final case class Corpus(
      isCitekey: Corpus.IsCitekeyArg1 => Corpus.IsCitekeyResult,
      isTarget: Corpus.IsTargetArg1 => Corpus.IsTargetResult,
      statusOf: Corpus.StatusOfArg1 => Option[Source.Status]
  )

  object Corpus:
    type IsCitekeyArg1 = com.histmersion.domain.library.Citekey
    val IsCitekeyArg1: _root_.com.histmersion.domain.library.Citekey.type = _root_.com.histmersion.domain.library.Citekey
    type IsCitekeyResult = com.histmersion.domain.CompileArticle.Known
    val IsCitekeyResult: _root_.com.histmersion.domain.CompileArticle.Known.type = _root_.com.histmersion.domain.CompileArticle.Known
    type IsTargetArg1 = com.histmersion.domain.research.Slug
    val IsTargetArg1: _root_.com.histmersion.domain.research.Slug.type = _root_.com.histmersion.domain.research.Slug
    type IsTargetResult = com.histmersion.domain.CompileArticle.IsTarget
    val IsTargetResult: _root_.com.histmersion.domain.CompileArticle.IsTarget.type = _root_.com.histmersion.domain.CompileArticle.IsTarget
    type StatusOfArg1 = com.histmersion.domain.library.Citekey
    val StatusOfArg1: _root_.com.histmersion.domain.library.Citekey.type = _root_.com.histmersion.domain.library.Citekey

  /** Check a draft. FATAL problems (an empty text, a link to nothing, escaped brackets, a Sources cited heading, an
    * extend under a heading the article does not have) always send it back; the others (an uncited paragraph, a
    * citation outside the evidence, a status not stated, a missing section, filler, a hand-written Not used section or
    * `_Method_` note — code generates both from the recorded filter, #958 — a reason for a source outside the filter or
    * one the draft cites, a source of the claim table neither cited nor given a reason) send it back too, but on the
    * last attempt the draft is kept and they are listed in the approval for the person to weigh. The draft carries the
    * reasons to store: the extraction's for the sources judged irrelevant, the writer's for the others it leaves out —
    * only for sources the article does not cite.
    */
  def checkDraft(
      a: DraftAnswer,
      slug: com.histmersion.domain.research.Slug,
      mode: DraftMode,
      kind: com.histmersion.domain.CompileArticle.Kind,
      currentTitle: Option[com.histmersion.domain.Title],
      current: com.histmersion.domain.CompileArticle.CheckDraftCurrent,
      evidence: Evidence,
      filter: com.histmersion.domain.CompileArticle.Evidence.Filter,
      corpus: Corpus,
      last: com.histmersion.domain.CompileArticle.Last
  ): Either[List[com.histmersion.domain.CompileArticle.Problem], (ArticleDraft, List[com.histmersion.domain.CompileArticle.Description])] =
    val text = Option(a.body).map(_.trim).getOrElse("")
    val section = a.section.map(_.trim).filter(_.nonEmpty).filter(_ => mode == DraftMode.Extend)
    val title = currentTitle.orElse(Option(a.title).map(_.trim).filter(_.nonEmpty)).getOrElse(slug)
    // a NEW article takes its title and summary from the draft: an empty one (a strict-schema
    // provider's "" or null) is sent back, not quietly replaced by the slug
    val newArticle = if currentTitle.isDefined || mode != DraftMode.Create then Nil
    else
      List(
        Option.when(Option(a.title).forall(_.trim.isEmpty))("the title is empty — give the new article's title"),
        Option.when(Option(a.summary).forall(_.trim.isEmpty))(
          "the summary is empty — say in one or two sentences what the article answers"
        )
      ).flatten
    val whole = combined(mode, current, text, section)
    val fatal = List(
      Option.when(text.isEmpty)("the text is empty"),
      whole.left.toOption.map(m => s"$m — name an existing heading, or null to add at the end")
    ).flatten ++ AgentVerify.linkProblems(text, corpus.isCitekey, corpus.isTarget) ++
      Option
        .when(ReviewArticle.headings(text).exists(h => h.trim.equalsIgnoreCase("sources cited")))(
          "a Sources cited section is generated by the page; do not write one"
        )
        .toList
    val body = whole.getOrElse(text)
    val mech = ReviewArticle.mechanical(
      slug,
      if mode == DraftMode.Create then kind else com.histmersion.domain.ReviewArticle.Finding.Kind("index"),
      body,
      corpus.isTarget,
      generated = com.histmersion.domain.ReviewArticle.MechanicalGenerated(true)
    )
    val citesHere = cited(text, corpus.isCitekey)
    val citesAll = cited(body, corpus.isCitekey).toSet
    val allowed = evidence.used.toSet ++ cited(current, corpus.isCitekey)
    val inFilter = evidence.sources.map(_.citekey).toSet
    val stated = Option(a.reasons)
      .getOrElse(Nil)
      .map(r =>
        r.modify(
          _.citekey).setTo(Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")).modify(_.reason).setTo(Option(r.reason).map(_.trim).getOrElse("")
        )
      )
    val reasonProblems = stated.flatMap { r =>
      if !inFilter(r.citekey) then Some(s"a reason for [[${r.citekey}]], which is not a source of the claim table")
      else if citesAll(r.citekey) then
        Some(s"a reason for [[${r.citekey}]], which the draft cites (a reason is only for a source it leaves out)")
      else if r.reason.isEmpty then Some(s"an empty reason for [[${r.citekey}]]")
      else Coverage.Reason.merge(Map.empty, List(r)).left.toOption
    }
    val writerReasons = stated
      .filter(r => inFilter(r.citekey) && !citesAll(r.citekey) && r.reason.nonEmpty)
      .map(r => r.citekey -> r.reason.replaceAll("\\s+", " ").take(Coverage.Reason.MaxChars))
      .toMap
    // the extraction's reason (the record does not bear on the question), unless the writer gave one
    val reasons =
      (evidence.notUsed.filterNot(n => citesAll(n.citekey)).map(n => n.citekey -> n.reason).toMap ++ writerReasons)
    val unexplained = evidence.used.filterNot(ck => citesAll(ck) || reasons.contains(ck))
    val handWritten = Option
      .when(ReviewArticle.headings(text).exists(_.trim.toLowerCase.startsWith("not used")))(
        "a Not used section: code generates it from the filter — give each left-out source's reason in `reasons`"
      )
      .toList ++
      Option.when(Article.Coverage.hasMethodLine(text))("a _Method_ note: code generates it from the filter").toList
    val soft =
      mech
        .filter(f => f.kind == "citation" && f.problem.contains("no [[citekey]]"))
        .take(3)
        .map(f => s"a paragraph with no [[citekey]]: \"${f.quote.take(80)}\"") ++
        (if mode == DraftMode.Create then
           mech.filter(f => f.kind == "section" && f.problem.startsWith("no ")).map(_.problem)
         else Nil) ++
        mech.filter(_.kind == "language").map(_.problem) ++
        citesHere.filterNot(allowed).map(ck => s"[[$ck]] is cited but no claim of it is in the claim table") ++
        statusesSilent(body, corpus.statusOf) ++
        Option.when(citesHere.isEmpty)("the text cites no source").toList ++
        newArticle.toList ++
        handWritten ++ reasonProblems ++
        unexplained.map(ck => s"[[$ck]] has claims in the table but is neither cited nor given a reason")
    val problems = fatal ++ soft
    if fatal.nonEmpty || (soft.nonEmpty && !last) then Left(problems)
    else
      val summary = Option(a.summary).map(_.trim).filter(_.nonEmpty)
      val notUsed = reasons.toList.sortBy(_._1).map((ck, r) => s"$ck: $r") ++
        unexplained.map(ck => s"$ck: claims extracted but neither cited nor given a reason")
      Right(
        (
          ArticleDraft(
            slug,
            mode,
            title,
            kind,
            text,
            summary,
            section,
            citesHere,
            notUsed,
            soft,
            filter,
            Coverage.Reason.list(reasons)
          ),
          soft.map(p => s"self-check: $p")
        )
      )

  /** The filter a compile run reads: the run's own (query, topics, tags) when it gives one; else the one the article
    * records (#958); else, for an existing article, its title as the query. It is what the article records afterwards.
    * `None` only when nothing at all selects a source.
    */
  def runFilter(
      query: Option[com.histmersion.domain.CompileArticle.Evidence.Filter],
      topics: List[com.histmersion.domain.CompileArticle.Topics],
      tags: List[com.histmersion.domain.CompileArticle.Tags],
      stored: Option[Article.Filter],
      existingTitle: Option[com.histmersion.domain.Title]
  ): Option[Article.Filter] =
    val stated = Article.Filter(query = query, topics = topics, tags = tags)
    Article.Filter
      .normalise(stated)
      .toOption
      .flatten
      .orElse(stored)
      .orElse(existingTitle.flatMap(t => Article.Filter.normalise(Article.Filter(query = Some(t))).toOption.flatten))

  /** The log line of a compile run. */
  def logLine(d: ArticleDraft, written: com.histmersion.domain.CompileArticle.Written, why: com.histmersion.domain.CompileArticle.Reason, review: Option[com.histmersion.domain.CompileArticle.ReviewText], runId: com.histmersion.domain.automation.Run.Id): com.histmersion.domain.CompileArticle.LogLine =
    val what = d.mode match
      case DraftMode.Create => s"compiled a new article from ${d.cited.size} source(s)"
      case DraftMode.Extend =>
        s"appended a section${d.section.fold("")(s => s" under '$s'")} citing ${d.cited.size} source(s)"
    val outcome = if written then what else s"nothing written — $why"
    s"compile-article [[${d.slug}]]: $outcome${review.fold("")(r => s"; review run ${r.take(8)} started")} (agent run ${runId.take(8)})"
