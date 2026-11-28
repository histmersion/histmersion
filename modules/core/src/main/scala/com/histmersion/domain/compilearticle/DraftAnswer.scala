package com.histmersion
package domain.compilearticle

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.domain.Title
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.Where
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.automation.Goal.Plans.ArticleDraft
import com.histmersion.api.research.DraftMode
import com.histmersion.domain.research.Slug
import com.histmersion.domain.CompileArticle.combined
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.compilearticle.Evidence
import com.histmersion.domain.compilearticle.Corpus
import com.histmersion.domain.CompileArticle.statusesSilent
import com.histmersion.domain.CompileArticle.cited
import com.histmersion.domain.CompileArticle.Last
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.reviewarticle.Fix

final case class DraftAnswer(
    title: Title,
    summary: Article.SummaryText,
    body: Article.Body,
    section: Option[Article.Section] = None,
    reasons: List[Coverage.Reason] = Nil
) derives JsonCodec

object DraftAnswer:
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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

  extension (a: DraftAnswer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def checkDraft(
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
      val newArticle = if currentTitle.isDefined || mode != DraftMode.Create then Nil
      else
        List(
          Option.when(Option(a.title).forall(_.value.trim.isEmpty))(Content.Answer(Content.Requirement.TitleGiven)),
          Option.when(Option(a.summary).forall(_.value.trim.isEmpty))(
            Content.Answer(Content.Requirement.ArticleSummary)
          )
        ).flatten
      val whole = combined(mode, current, text, section)
      val fatal: List[Problem] = List(
        Option.when(text.value.isEmpty)(Content.Answer(Content.Requirement.TextGiven)),
        whole.left.toOption
          .flatMap(_ => section)
          .map(s => Content.Answer(Content.Requirement.KnownHeading(Raw.of(s))))
      ).flatten ++ AgentVerify
        .linkProblems(AgentVerify.Text(text.value), Problem.Where("text"), corpus.isCitekey, corpus.isTarget) ++
        Option
          .when(Fix.headings(text).exists(h => h.trim.equalsIgnoreCase("sources cited")))(
            Content.Answer(Content.Requirement.NoSourcesCited)
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
          Some(Content.Answer(Content.Requirement.ReasonForTabled(Raw.of(r.citekey))))
        else if citesAllKeys(r.citekey.value) then
          Some(Content.Answer(Content.Requirement.ReasonForUncited(Raw.of(r.citekey))))
        else if s"${r.reason}".isEmpty then Some(Content.Answer(Content.Requirement.ReasonText(Raw.of(r.citekey))))
        else Coverage.Reason.merge(Map.empty, List(r)).left.toOption
      }
      val writerReasons = stated
        .filter(r => inFilterKeys(r.citekey.value) && !citesAllKeys(r.citekey.value) && s"${r.reason}".nonEmpty)
        .map(r =>
          r.citekey -> Coverage.Rationale(s"${s"${r.reason}".replaceAll("\\s+", " ").take(Coverage.Reason.MaxChars)}")
        )
        .toMap
      val reasons =
        (evidence.notUsed
          .filterNot(n => citesAll(n.citekey))
          .map(n => com.histmersion.api.library.Citekey(n.citekey.value) -> n.reason)
          .toMap ++ writerReasons)
      val unexplained =
        evidence.used.filterNot(ck => citesAll(ck) || reasons.contains(com.histmersion.api.library.Citekey(ck.value)))
      val handWritten = Option
        .when(Fix.headings(text).exists(_.trim.toLowerCase.startsWith("not used")))(
          Content.Answer(Content.Requirement.NotUsedGenerated)
        )
        .toList ++
        Option
          .when(Article.Coverage.hasMethodLine(text.value))(Content.Answer(Content.Requirement.MethodGenerated))
          .toList
      val soft: List[Problem] =
        mech
          .filter(_.problem.isUncitedParagraph)
          .take(3)
          .map(f => Content.Answer(Content.Requirement.ParagraphCited(Raw.of(f.quote)))) ++
          (if mode == DraftMode.Create then mech.filter(_.problem.isMissingSection).flatMap(_.problem.cause.shown)
           else Nil) ++
          mech.filter(_.kind.value == "language").flatMap(_.problem.cause.shown) ++
          citesHere.filterNot(allowed).map(ck => Content.Answer(Content.Requirement.ClaimTabled(Raw.of(ck)))) ++
          statusesSilent(body, corpus.statusOf) ++
          Option.when(citesHere.isEmpty)(Content.Answer(Content.Requirement.CitesNoSource)).toList ++
          newArticle.toList ++
          handWritten ++ reasonProblems ++
          unexplained.map(ck => Content.Answer(Content.Requirement.TabledAccounted(Raw.of(ck))))
      val problems = fatal ++ soft
      if fatal.nonEmpty || (soft.nonEmpty && !last.value) then Left(problems)
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
              soft.map(p => ArticleDraft.Problems(p.sentence)),
              filter,
              Coverage.Reason.list(reasons.map((k, v) => k -> v))
            ),
            soft.map(_.inside(Where("self-check")))
          )
        )
