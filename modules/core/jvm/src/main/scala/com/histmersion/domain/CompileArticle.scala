package com.histmersion.domain

import com.histmersion.api.{ArticleCoverage, ArticleFilter, Articles, CoverageReason}
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.GoalPlans.ArticleDraft
import com.histmersion.values.{Confidence, DraftMode, SourceStatus}

/** The `compile-article` pipeline's typed answers and its pure logic (docs/research-embedded-agents.md §4.5, §14; the
  * five phases of `docs/writing-guide.md`): the claim table (one extraction per source, every claim with a quote code
  * finds in the record), the evidence model (statuses, the disagreement edges among the sources, due weight, the
  * Not-used list — all from the database), and the code self-check of the draft (links, citations per paragraph,
  * statuses stated inline, the section set, filler, no Sources cited heading). The server runs the steps; everything
  * here is pure and table-tested.
  */
object CompileArticle:

  // ── phase 1: extraction ─────────────────────────────────────────────────────────────────────────

  final case class ClaimAnswer(
      claim: String,
      quote: String,
      date: Option[String] = None,
      place: Option[String] = None,
      confidence: String
  ) derives KindlingsCodecAsObject
  object ClaimAnswer:
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

  final case class ExtractAnswer(relevant: Boolean, reason: String, claims: List[ClaimAnswer] = Nil)
      derives KindlingsCodecAsObject
  object ExtractAnswer:
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
      citekey: String,
      claim: String,
      quote: String,
      date: Option[String],
      place: Option[String],
      confidence: Confidence,
      ratio: Double
  ) derives KindlingsCodecAsObject

  /** One source's verified extraction (checkpointed): its claims, or why it is not used. */
  final case class Extracted(citekey: String, claims: List[Claim], notUsed: Option[String])
      derives KindlingsCodecAsObject

  /** Check one extraction: every quote must be in `record` (summary, findings, notes, body excerpt), every confidence
    * in the vocabulary; a source judged irrelevant must say why. Unverifiable claims send the answer back; on the last
    * attempt they are dropped and the rest kept.
    */
  def checkExtract(
      a: ExtractAnswer,
      citekey: String,
      record: String,
      minRatio: Double,
      maxClaims: Int,
      last: Boolean
  ): Either[List[String], (Extracted, List[String])] =
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
  final case class SourceFact(citekey: String, title: String, year: Option[Int], status: SourceStatus)
      derives KindlingsCodecAsObject

  /** A disagreement edge between two sources of the filter (only citekey targets count). */
  final case class EdgeFact(from: String, kind: String, to: String) derives KindlingsCodecAsObject

  /** The evidence model: the sources with their statuses and claim counts (due weight), the disagreement edges among
    * them, the disputed ones (a non-current status), and the Not-used list with reasons. `filter` is the filter line
    * for the `_Method_` note.
    */
  /** A source the article leaves out, and why. */
  final case class NotUsed(citekey: String, reason: String) derives KindlingsCodecAsObject

  final case class Evidence(
      filter: String,
      total: Int,
      sources: List[SourceFact],
      claims: Map[String, Int],
      edges: List[EdgeFact],
      disputed: List[String],
      notUsed: List[NotUsed]
  ) derives KindlingsCodecAsObject:
    def used: List[String] = sources.map(_.citekey).filter(ck => claims.getOrElse(ck, 0) > 0)

  /** The statuses a first citation must state (writing guide rule 2). */
  val StatedStatuses: Set[SourceStatus] = Set(
    SourceStatus.Contested,
    SourceStatus.Superseded,
    SourceStatus.Debunked,
    SourceStatus.Retracted,
    SourceStatus.Unassessed
  )

  val DisagreementKinds: Set[String] = ArticleCoverage.DisagreementKinds.toSet

  def evidence(
      filter: String,
      total: Int,
      facts: List[SourceFact],
      extracted: List[Extracted],
      edges: List[EdgeFact]
  ): Evidence =
    val in = facts.map(_.citekey).toSet
    val counts = extracted.map(e => e.citekey -> e.claims.size).toMap
    val kept = edges.filter(e => DisagreementKinds.contains(e.kind) && in(e.from) && in(e.to)).distinct
    val disputed = facts
      .filter(f => f.status != SourceStatus.Current && f.status != SourceStatus.Foundational)
      .map(_.citekey)
    val notUsed = extracted.collect {
      case e if e.claims.isEmpty => NotUsed(e.citekey, e.notUsed.getOrElse("no claim"))
    } ++
      facts.collect {
        case f if f.status == SourceStatus.Retracted && counts.getOrElse(f.citekey, 0) > 0 =>
          NotUsed(f.citekey, "retracted: may be cited only to note the retraction")
      }
    Evidence(filter, total, facts, counts, kept, disputed, notUsed.distinctBy(_.citekey))

  /** The claim table and evidence model as the writer reads them (data, not instructions). */
  def evidenceText(ev: Evidence, extracted: List[Extracted]): String =
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
      title: String,
      summary: String,
      body: String,
      section: Option[String] = None,
      reasons: List[CoverageReason] = Nil
  ) derives KindlingsCodecAsObject
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

  private def blocks(body: String): List[String] =
    body.split("\\n\\s*\\n").toList.map(_.trim).filter(_.nonEmpty)

  /** Writing guide rule 2: the FIRST block citing a source whose status is not current or foundational must state that
    * status (the word itself, e.g. "contested").
    */
  def statusesSilent(body: String, statusOf: String => Option[SourceStatus]): List[String] =
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
  def cited(text: String, isCitekey: String => Boolean): List[String] =
    AgentVerify.wikilinks(withoutNotUsed(text)).filter(isCitekey)

  /** `text` without its "Not used" section (heading to the next heading of the same or higher level). */
  def withoutNotUsed(text: String): String = ArticleCoverage.withoutNotUsed(text)

  /** What the article would read after the draft is applied: the whole body (create), or the current body with the text
    * appended (under `section`, else at the end).
    */
  def combined(mode: DraftMode, current: String, text: String, section: Option[String]): Either[String, String] =
    mode match
      case DraftMode.Create => Right(text)
      case DraftMode.Extend =>
        section.map(_.trim).filter(_.nonEmpty) match
          case Some(s) => Articles.appendUnderSection(current, s, text.trim)
          case None    => Right(if current.trim.isEmpty then text.trim else s"${current.stripTrailing}\n\n${text.trim}")

  /** The inputs of the self-check that come from the corpus. */
  final case class Corpus(
      isCitekey: String => Boolean,
      isTarget: String => Boolean,
      statusOf: String => Option[SourceStatus]
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
      slug: String,
      mode: DraftMode,
      kind: String,
      currentTitle: Option[String],
      current: String,
      evidence: Evidence,
      filter: String,
      corpus: Corpus,
      last: Boolean
  ): Either[List[String], (ArticleDraft, List[String])] =
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
      if mode == DraftMode.Create then kind else "index",
      body,
      corpus.isTarget,
      generated = true
    )
    val citesHere = cited(text, corpus.isCitekey)
    val citesAll = cited(body, corpus.isCitekey).toSet
    val allowed = evidence.used.toSet ++ cited(current, corpus.isCitekey)
    val inFilter = evidence.sources.map(_.citekey).toSet
    val stated = Option(a.reasons)
      .getOrElse(Nil)
      .map(r =>
        r.copy(
          citekey = Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse(""),
          reason = Option(r.reason).map(_.trim).getOrElse("")
        )
      )
    val reasonProblems = stated.flatMap { r =>
      if !inFilter(r.citekey) then Some(s"a reason for [[${r.citekey}]], which is not a source of the claim table")
      else if citesAll(r.citekey) then
        Some(s"a reason for [[${r.citekey}]], which the draft cites (a reason is only for a source it leaves out)")
      else if r.reason.isEmpty then Some(s"an empty reason for [[${r.citekey}]]")
      else CoverageReason.merge(Map.empty, List(r)).left.toOption
    }
    val writerReasons = stated
      .filter(r => inFilter(r.citekey) && !citesAll(r.citekey) && r.reason.nonEmpty)
      .map(r => r.citekey -> r.reason.replaceAll("\\s+", " ").take(CoverageReason.MaxChars))
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
      Option.when(ArticleCoverage.hasMethodLine(text))("a _Method_ note: code generates it from the filter").toList
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
            CoverageReason.list(reasons)
          ),
          soft.map(p => s"self-check: $p")
        )
      )

  /** The filter a compile run reads: the run's own (query, topics, tags) when it gives one; else the one the article
    * records (#958); else, for an existing article, its title as the query. It is what the article records afterwards.
    * `None` only when nothing at all selects a source.
    */
  def runFilter(
      query: Option[String],
      topics: List[String],
      tags: List[String],
      stored: Option[ArticleFilter],
      existingTitle: Option[String]
  ): Option[ArticleFilter] =
    val stated = ArticleFilter(query = query, topics = topics, tags = tags)
    ArticleFilter
      .normalise(stated)
      .toOption
      .flatten
      .orElse(stored)
      .orElse(existingTitle.flatMap(t => ArticleFilter.normalise(ArticleFilter(query = Some(t))).toOption.flatten))

  /** The log line of a compile run. */
  def logLine(d: ArticleDraft, written: Boolean, why: String, review: Option[String], runId: String): String =
    val what = d.mode match
      case DraftMode.Create => s"compiled a new article from ${d.cited.size} source(s)"
      case DraftMode.Extend =>
        s"appended a section${d.section.fold("")(s => s" under '$s'")} citing ${d.cited.size} source(s)"
    val outcome = if written then what else s"nothing written — $why"
    s"compile-article [[${d.slug}]]: $outcome${review.fold("")(r => s"; review run ${r.take(8)} started")} (agent run ${runId.take(8)})"
