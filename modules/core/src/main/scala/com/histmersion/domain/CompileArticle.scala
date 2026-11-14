package com.histmersion
package domain
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.research.Articles
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

/** The `compile-article` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.5, §14; the
  * five phases of `docs/writing-guide.md`): the claim table (one extraction per source, every claim with a quote code
  * finds in the record), the evidence model (statuses, the disagreement edges among the sources, due weight, the
  * Not-used list — all from the database), and the code self-check of the draft (links, citations per paragraph,
  * statuses stated inline, the section set, filler, no Sources cited heading). The server runs the steps; everything
  * here is pure and table-tested.
  */
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

  given citekeyKey: JsonKeyCodec[Citekey] =
    JsonCodec.key[Citekey](_.value, k => Citekey.option(k).toRight(s"not a citekey: '$k'"))

  // ── phase 1: extraction ─────────────────────────────────────────────────────────────────────────

  final case class ClaimAnswer(
      claim: ClaimAnswer.Claim,
      quote: AgentProposals.Proposal.Quote,
      date: Option[ClaimAnswer.Date] = None,
      place: Option[ClaimAnswer.Place] = None,
      confidence: com.histmersion.domain.library.Confidence.Wire
  ) derives JsonCodec
  object ClaimAnswer:
    type Claim = Claim.T
    object Claim extends Newtype[String, Pure]
    type Date = Date.T
    object Date extends Newtype[String, Pure]
    type Place = Place.T
    object Place extends Newtype[String, Pure]

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

  final case class ExtractAnswer(
      relevant: ExtractAnswer.Relevant,
      reason: Coverage.Rationale,
      claims: List[ClaimAnswer] = Nil
  ) derives JsonCodec
  object ExtractAnswer:
    type Relevant = Relevant.T
    object Relevant extends Newtype[Boolean, Pure]

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
      citekey: Citekey,
      claim: ClaimAnswer.Claim,
      quote: AgentProposals.Proposal.Quote,
      date: Option[ClaimAnswer.Date],
      place: Option[ClaimAnswer.Place],
      confidence: Confidence,
      ratio: AgentVerify.Ratio
  ) derives JsonCodec

  /** One source's verified extraction (checkpointed): its claims, or why it is not used. */
  final case class Extracted(citekey: Citekey, claims: List[Claim], notUsed: Option[Coverage.Rationale])
      derives JsonCodec

  /** Check one extraction: every quote must be in `record` (summary, findings, notes, body excerpt), every confidence
    * in the vocabulary; a source judged irrelevant must say why. Unverifiable claims send the answer back; on the last
    * attempt they are dropped and the rest kept.
    */
  def checkExtract(
      a: ExtractAnswer,
      citekey: Citekey,
      record: AgentVerify.SourceBody,
      minRatio: AgentVerify.MinRatio,
      maxClaims: MaxClaims,
      last: Last
  ): Either[List[Problem], (Extracted, List[Problem])] =
    val checked = a.claims.zipWithIndex.map { (c, i) =>
      val label = s"claim ${i + 1} (${Option(c.claim).map(_.value).getOrElse("").take(50)})"
      if Option(c.claim).forall(_.value.trim.isEmpty) then Left(Problem.Unclassified(Legacy.Text(s"$label: no claim")))
      else
        AgentVerify.confidence(Option(c.confidence).getOrElse(com.histmersion.domain.library.Confidence.Wire(""))) match
          case Left(m)     => Left(Problem.Unclassified(Legacy.Text(s"$label: ${m.sentence}")))
          case Right(conf) =>
            val q = Option(c.quote).map(_.value.trim).getOrElse("")
            val m = AgentVerify.findQuote(AgentProposals.Proposal.Quote(q), record, minRatio)
            if q.isEmpty then Left(Problem.Unclassified(Legacy.Text(s"$label: no quote")))
            else if !m.accepted then
              Left(
                Problem.Unclassified(
                  Legacy.Text(s"$label: the quote is not in the record of $citekey — copy it exactly")
                )
              )
            else
              Right(
                Claim(
                  citekey,
                  ClaimAnswer.Claim(c.claim.value.trim),
                  AgentProposals.Proposal.Quote(q),
                  c.date.map(d => ClaimAnswer.Date(d.value.trim)).filter(_.value.nonEmpty),
                  c.place.map(p => ClaimAnswer.Place(p.value.trim)).filter(_.value.nonEmpty),
                  conf,
                  m.ratio
                )
              )
    }
    val claims =
      checked.collect { case Right(c) => c }.distinctBy(c => AgentVerify.normalise(AgentVerify.Text(c.claim.value)))
    val tooMany =
      Option.when(claims.size > maxClaims.value)(
        Problem.Unclassified(Legacy.Text(s"at most $maxClaims claims per source (you gave ${claims.size})"))
      )
    val reason = Option(a.reason).map(r => s"$r".trim).filter(_.nonEmpty)
    val noReason =
      Option.when((!a.relevant.value || a.claims.isEmpty) && reason.isEmpty)(
        Problem.Unclassified(Legacy.Text("say why the record is not used"))
      )
    val problems = checked.collect { case Left(m) => m } ++ tooMany.toList ++ noReason.toList
    if problems.nonEmpty && !last.value then Left(problems)
    else
      val kept = if a.relevant.value then claims.take(maxClaims.value) else Nil
      val notUsed =
        Option.when(kept.isEmpty)(
          Coverage.Rationale(s"${reason.getOrElse("no claim could be verified in its record")}")
        )
      Right(
        (
          Extracted(citekey, kept, notUsed),
          problems.map(p => Problem.Unclassified(Legacy.Text(s"extract $citekey: dropped — ${p.sentence}")))
        )
      )

  // ── phase 2: the evidence model (code, from the database) ───────────────────────────────────────

  /** What the database says about one source the filter returned. */
  final case class SourceFact(citekey: Citekey, title: Title, year: Option[Year], status: Source.Status)
      derives JsonCodec

  /** A disagreement edge between two sources of the filter (only citekey targets count). */
  final case class EdgeFact(from: Citekey, kind: Article.Coverage.DisagreementKind, to: Citekey) derives JsonCodec

  /** The evidence model: the sources with their statuses and claim counts (due weight), the disagreement edges among
    * them, the disputed ones (a non-current status), and the Not-used list with reasons. `filter` is the filter line
    * for the `_Method_` note.
    */
  /** A source the article leaves out, and why: a rationale, not a failure. */
  final case class NotUsed(citekey: Citekey, reason: Coverage.Rationale) derives JsonCodec

  final case class Evidence(
      filter: ArticleDraft.Filter,
      total: RowCount,
      sources: List[SourceFact],
      claims: Map[Citekey, Evidence.ClaimCount],
      edges: List[EdgeFact],
      disputed: List[Citekey],
      notUsed: List[NotUsed]
  ) derives JsonCodec:
    def used: List[Citekey] =
      sources.map(_.citekey).filter(ck => claims.getOrElse(ck, Evidence.ClaimCount(0)).hasClaims)

  object Evidence:
    type ClaimCount = ClaimCount.T
    object ClaimCount extends Newtype[Int, Pure]:
      extension (n: ClaimCount) def hasClaims: Boolean = n.value > 0

  /** The statuses a first citation must state (writing guide rule 2). */
  val StatedStatuses: Set[Source.Status] = Set(
    Source.Status.Contested,
    Source.Status.Superseded,
    Source.Status.Debunked,
    Source.Status.Retracted,
    Source.Status.Unassessed
  )

  val DisagreementKinds: Set[Article.Coverage.DisagreementKind] = Article.Coverage.DisagreementKinds.toSet

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

  /** The claim table and evidence model as the writer reads them (data, not instructions). */
  def evidenceText(ev: Evidence, extracted: List[Extracted]): Description =
    val bySource = extracted.map(e => e.citekey -> e).toMap
    val sources = ev.sources.filter(s => ev.claims.getOrElse(s.citekey, Evidence.ClaimCount(0)).hasClaims).map { s =>
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
    val weight =
      ev.claims.toList.filter(_._2.hasClaims).sortBy(-_._2.value).map((ck, n) => s"[[$ck]] $n").mkString(", ")
    val notUsed =
      if ev.notUsed.isEmpty then "none"
      else ev.notUsed.map(n => s"[[${n.citekey}]]: ${n.reason}").mkString("\n- ")
    Description(
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
    )

  // ── phase 3: the draft ──────────────────────────────────────────────────────────────────────────

  final case class DraftAnswer(
      title: Title,
      summary: Article.SummaryText,
      body: Article.Body,
      section: Option[Article.Section] = None,
      reasons: List[Coverage.Reason] = Nil
  ) derives JsonCodec
  object DraftAnswer:
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

  private def blocks(body: Article.Body): List[String] =
    body.value.split("\\n\\s*\\n").toList.map(_.trim).filter(_.nonEmpty)

  /** Writing guide rule 2: the FIRST block citing a source whose status is not current or foundational must state that
    * status (the word itself, e.g. "contested").
    */
  def statusesSilent(body: Article.Body, statusOf: Citekey => Option[Source.Status]): List[Problem] =
    val bs = blocks(body)
    AgentVerify.wikilinks(AgentVerify.Text(body.value)).flatMap { ck =>
      Citekey.option(ck.value).flatMap(statusOf).filter(StatedStatuses.contains).flatMap { st =>
        bs.find(_.contains(s"[[$ck]]"))
          .filterNot(_.toLowerCase.contains(st.wire.value))
          .map(_ => Problem.Unclassified(Legacy.Text(s"the first citation of [[$ck]] does not say it is ${st.wire}")))
      }
    }

  /** The citekeys a text cites as EVIDENCE (links that are sources), leaving out its Not used section — the sources
    * listed there are named, not cited.
    */
  def cited(text: Article.Body, isCitekey: Citekey => Boolean): List[Citekey] =
    AgentVerify
      .wikilinks(AgentVerify.Text(withoutNotUsed(text).value))
      .flatMap(w => Citekey.option(w.value))
      .filter(isCitekey)

  /** `text` without its "Not used" section (heading to the next heading of the same or higher level). */
  def withoutNotUsed(text: Article.Body): Article.Body = Article.Coverage.withoutNotUsed(text)

  /** What the article would read after the draft is applied: the whole body (create), or the current body with the text
    * appended (under `section`, else at the end).
    */
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
            Articles.appendUnderSection(current, s, Article.Prose(text.value.trim))
          case None =>
            Right(Article.Body(if current.value.trim.isEmpty then text.value.trim
            else s"${current.value.stripTrailing}\n\n${text.value.trim}"))

  /** The inputs of the self-check that come from the corpus. */
  final case class Corpus(
      isCitekey: Citekey => Boolean,
      isTarget: Slug => Boolean,
      statusOf: Citekey => Option[Source.Status]
  )

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
      slug: Slug,
      mode: DraftMode,
      kind: Article.Kind.Wire,
      currentTitle: Option[Title],
      current: Article.Body,
      evidence: Evidence,
      filter: ArticleDraft.Filter,
      corpus: Corpus,
      last: Last
  ): Either[List[Problem], (ArticleDraft, List[Problem])] =
    val text = Article.Body(Option(a.body).map(_.value.trim).getOrElse(""))
    val section =
      a.section.map(s => Article.Section(s.value.trim)).filter(_.value.nonEmpty).filter(_ => mode == DraftMode.Extend)
    val title = currentTitle
      .orElse(Option(a.title).map(t => Title(t.value.trim)).filter(_.value.nonEmpty))
      .getOrElse(Title(slug.value))
    // a NEW article takes its title and summary from the draft: an empty one (a strict-schema
    // provider's "" or null) is sent back, not quietly replaced by the slug
    val newArticle = if currentTitle.isDefined || mode != DraftMode.Create then Nil
    else
      List(
        Option.when(Option(a.title).forall(_.value.trim.isEmpty))("the title is empty — give the new article's title"),
        Option.when(Option(a.summary).forall(_.value.trim.isEmpty))(
          "the summary is empty — say in one or two sentences what the article answers"
        )
      ).flatten
    val whole = combined(mode, current, text, section)
    val fatal = List(
      Option.when(text.value.isEmpty)("the text is empty"),
      whole.left.toOption.map(m => s"${m.sentence} — name an existing heading, or null to add at the end")
    ).flatten ++ AgentVerify
      .linkProblems(AgentVerify.Text(text.value), Problem.Where("text"), corpus.isCitekey, corpus.isTarget)
      .map(_.sentence) ++
      Option
        .when(ReviewArticle.headings(text).exists(h => h.trim.equalsIgnoreCase("sources cited")))(
          "a Sources cited section is generated by the page; do not write one"
        )
        .toList
    val body = whole.getOrElse(text)
    val mech = ReviewArticle.mechanical(
      slug,
      if mode == DraftMode.Create then kind else Article.Kind.Wire("index"),
      body,
      t => Slug.option(t.value).exists(corpus.isTarget),
      generated = ReviewArticle.MechanicalGenerated(true)
    )
    val citesHere = cited(text, corpus.isCitekey)
    val citesAll = cited(body, corpus.isCitekey).toSet
    val allowed = evidence.used.toSet ++ cited(current, corpus.isCitekey)
    val inFilterKeys: Set[String] = evidence.sources.map(_.citekey.value).toSet
    val citesAllKeys: Set[String] = citesAll.map(_.value)
    val stated = Option(a.reasons)
      .getOrElse(Nil)
      .map(r =>
        r.modify(_.citekey)
          .setTo(
            com.histmersion.api.library.Citekey(
              Option(r.citekey).map(_.value.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
            )
          )
          .modify(_.reason)
          .setTo(Coverage.Rationale(s"${Option(r.reason).map(w => s"$w".trim).getOrElse("")}"))
      )
    val reasonProblems = stated.flatMap { r =>
      if !inFilterKeys(r.citekey.value) then
        Some(s"a reason for [[${r.citekey}]], which is not a source of the claim table")
      else if citesAllKeys(r.citekey.value) then
        Some(s"a reason for [[${r.citekey}]], which the draft cites (a reason is only for a source it leaves out)")
      else if s"${r.reason}".isEmpty then Some(s"an empty reason for [[${r.citekey}]]")
      else Coverage.Reason.merge(Map.empty, List(r)).left.toOption.map(_.sentence)
    }
    val writerReasons = stated
      .filter(r => inFilterKeys(r.citekey.value) && !citesAllKeys(r.citekey.value) && s"${r.reason}".nonEmpty)
      .map(r =>
        r.citekey -> Coverage.Rationale(s"${s"${r.reason}".replaceAll("\\s+", " ").take(Coverage.Reason.MaxChars)}")
      )
      .toMap
    // the extraction's reason (the record does not bear on the question), unless the writer gave one
    val reasons =
      (evidence.notUsed
        .filterNot(n => citesAll(n.citekey))
        .map(n => com.histmersion.api.library.Citekey(n.citekey.value) -> n.reason)
        .toMap ++ writerReasons)
    val unexplained =
      evidence.used.filterNot(ck => citesAll(ck) || reasons.contains(com.histmersion.api.library.Citekey(ck.value)))
    val handWritten = Option
      .when(ReviewArticle.headings(text).exists(_.trim.toLowerCase.startsWith("not used")))(
        "a Not used section: code generates it from the filter — give each left-out source's reason in `reasons`"
      )
      .toList ++
      Option
        .when(Article.Coverage.hasMethodLine(text.value))("a _Method_ note: code generates it from the filter")
        .toList
    val soft =
      mech
        .filter(_.problem.isUncitedParagraph)
        .take(3)
        .map(f => s"a paragraph with no [[citekey]]: \"${f.quote.value.take(80)}\"") ++
        (if mode == DraftMode.Create then mech.filter(_.problem.isMissingSection).map(_.problem.value)
         else Nil) ++
        mech.filter(_.kind.value == "language").map(_.problem.value) ++
        citesHere.filterNot(allowed).map(ck => s"[[$ck]] is cited but no claim of it is in the claim table") ++
        statusesSilent(body, corpus.statusOf).map(_.sentence) ++
        Option.when(citesHere.isEmpty)("the text cites no source").toList ++
        newArticle.toList ++
        handWritten ++ reasonProblems ++
        unexplained.map(ck => s"[[$ck]] has claims in the table but is neither cited nor given a reason")
    val problems = fatal ++ soft
    if fatal.nonEmpty || (soft.nonEmpty && !last.value) then
      Left(problems.map((text => Problem.Unclassified(Legacy.Text(text)))))
    else
      val summary = Option(a.summary).map(s => Article.SummaryText(s.value.trim)).filter(_.value.nonEmpty)
      val notUsed = reasons.toList.sortBy(_._1.value).map((ck, r) => s"$ck: $r") ++
        unexplained.map(ck => s"$ck: claims extracted but neither cited nor given a reason")
      Right(
        (
          ArticleDraft(
            Article.Slug(slug.value),
            mode,
            Article.Title(title.value),
            kind,
            ArticleDraft.Text(text.value),
            summary,
            section.map(s => ArticleDraft.Section(s.value)),
            citesHere.map(ck => com.histmersion.api.library.Citekey(ck.value)),
            notUsed.map(com.histmersion.api.library.Citekey(_)),
            soft.map(ArticleDraft.Problems(_)),
            filter,
            Coverage.Reason.list(reasons.map((k, v) => k -> v))
          ),
          soft.map(p => Problem.Unclassified(Legacy.Text(s"self-check: $p")))
        )
      )

  /** The filter a compile run reads: the run's own (query, topics, tags) when it gives one; else the one the article
    * records (#958); else, for an existing article, its title as the query. It is what the article records afterwards.
    * `None` only when nothing at all selects a source.
    */
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
  def logLine(d: ArticleDraft, written: Written, why: Problem, review: Option[Run.Id], runId: Run.Id): LogLine =
    val what = d.mode match
      case DraftMode.Create => s"compiled a new article from ${d.cited.size} source(s)"
      case DraftMode.Extend =>
        s"appended a section${d.section.fold("")(s => s" under '$s'")} citing ${d.cited.size} source(s)"
    val outcome = if written.value then what else s"nothing written — ${why.sentence}"
    LogLine(
      s"compile-article [[${d.slug}]]: $outcome${review.fold("")(r => s"; review run ${r.value.take(8)} started")} (agent run ${runId.value.take(8)})"
    )
