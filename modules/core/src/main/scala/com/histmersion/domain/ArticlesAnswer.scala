package com.histmersion
package domain

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.research.Article.Plans.ArticlePlan
import com.histmersion.api.research.Article
import com.histmersion.domain.library.Citekey
import com.histmersion.api.research.Article.Plans.ProposedArticle
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Count
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.Where
import com.histmersion.util.problem.Problem.Share
import scala.annotation.tailrec
import com.histmersion.domain.automation.Goal
import com.histmersion.api.Plural
import com.histmersion.domain.automation.Run

final case class ArticlesAnswer(rationale: ArticlesAnswer.Rationale, articles: List[ArticlesAnswer.ArticleAnswer] = Nil)
    derives JsonCodec

object ArticlesAnswer:
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[ArticlesAnswer] = KindlingsSchema
    .derived[ArticlesAnswer]
    .schema
    .modify(_.rationale)(
      _.description("How the articles cover the research area and what they leave open, in two or three sentences")
    )
    .modify(_.articles)(_.description("The new articles, the most needed first"))

  type LastRound = LastRound.T

  object LastRound extends Newtype[Boolean, Pure]

  type LogLine = LogLine.T

  object LogLine extends Newtype[String, Pure]

  type Outcome = Outcome.T

  object Outcome extends Newtype[String, Pure]

  final case class ArticleAnswer(
      slug: ArticleAnswer.Slug,
      title: ArticleAnswer.Title,
      kind: Option[ArticleAnswer.Kind] = None,
      query: Option[ArticleAnswer.Query] = None,
      topics: List[ArticleAnswer.Topics] = Nil,
      tags: List[ArticleAnswer.Tags] = Nil,
      why: ArticlePlan.Rationale = ArticlePlan.Rationale("")
  ) derives JsonCodec

  object ArticleAnswer:
    type Slug = Slug.T
    object Slug extends Newtype[String, Pure]
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Query = Query.T
    object Query extends Newtype[String, Pure]
    type Topics = Topics.T
    object Topics extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[ArticleAnswer] = KindlingsSchema
      .derived[ArticleAnswer]
      .schema
      .modify(_.slug)(
        _.description("A new lower-case slug for the article (letters, digits, '-', '_'), not one that exists")
      )
      .modify(_.title)(_.description("The article's title"))
      .modify(_.kind)(_.description("concept | myth | culture | region | timeline (null: concept)"))
      .modify(_.query)(_.description("Free text the article's sources must match; null for none"))
      .modify(_.topics)(_.description("Topics every source must have, from the corpus vocabulary (facets)"))
      .modify(_.tags)(_.description("Tags every source must have, from the corpus vocabulary (facets)"))
      .modify(_.why)(_.description("What the article would establish that the area's articles do not, in one sentence"))

  final case class Limits(minSources: Limits.MinSources, maxOverlap: Limits.MaxOverlap, maxArticles: Limits.MaxArticles)

  object Limits:
    type MinSources = MinSources.T
    object MinSources extends Newtype[Int, Pure]
    type MaxOverlap = MaxOverlap.T
    object MaxOverlap extends Newtype[Double, Pure]
    type MaxArticles = MaxArticles.T
    object MaxArticles extends Newtype[Int, Pure]

  final case class Candidate(
      slug: Article.Slug,
      title: Article.Title,
      kind: Article.Kind.Wire,
      filter: Article.Filter,
      why: ArticlePlan.Rationale
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def overlap(mine: Set[Citekey], theirs: Set[Citekey]): ProposedArticle.Overlap =
    ProposedArticle.Overlap(if mine.isEmpty then 0.0 else mine.count(theirs).toDouble / mine.size)

  extension (a: ArticlesAnswer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def check(
        maxArticles: Limits.MaxArticles,
        last: LastRound
    ): Either[List[Problem], (List[Candidate], List[Problem])] =
      val problems = List.newBuilder[Problem]
      val dropped = List.newBuilder[Problem]
      val kept = a.articles
        .foldLeft(Vector.empty[Candidate]) { (acc, x) =>
          Article.slugOf(Article.Slug(Option(x.slug).fold("")(_.value))) match
            case Left(m) => problems += m.within(Field(s"article '${x.slug}'")); acc
            case Right(s) if Option(x.title).forall(_.value.trim.isEmpty) =>
              problems += Content.Proposal(Content.Requirement.ArticleTitle, Where(s"article '$s'")); acc
            case Right(s) if acc.exists(_.slug == s) =>
              dropped += Content.Answer(Content.Requirement.ProposedOnce(Raw.of(s))); acc
            case Right(s) =>
              val kind = x.kind.map(_.value.trim).filter(_.nonEmpty).getOrElse("concept")
              Article.kindOf(Article.Kind.Wire(kind)) match
                case Left(m)  => problems += m.within(Field(s"article '$s'")); acc
                case Right(k) =>
                  Article.Filter.normalise(
                    Article.Filter(
                      query = x.query.map(q => Article.Filter.Query(q.value)),
                      topics = x.topics.map(t => Article.Filter.Topics(t.value)),
                      tags = x.tags.map(t => Article.Filter.Tags(t.value))
                    )
                  ) match
                    case Left(m)     => problems += m.within(Field(s"article '$s'")); acc
                    case Right(None) =>
                      problems += Content.Proposal(Content.Requirement.ArticleFilter, Where(s"article '$s'")); acc
                    case Right(Some(f)) =>
                      acc :+ Candidate(
                        s,
                        Article.Title(x.title.value.trim),
                        k,
                        f,
                        ArticlePlan.Rationale(Option(x.why).fold("")(w => s"$w".trim))
                      )
        }
        .toList
      val cap = maxArticles.value
      if kept.size > cap then
        problems += Content.Answer(Content.Requirement.TooManyArticles(Count(cap.toLong), Count(kept.size.toLong)))
      if a.articles.isEmpty then problems += Content.Answer(Content.Requirement.ArticlesGiven)
      if Option(a.rationale).forall(_.value.trim.isEmpty) then
        problems += Content.Answer(Content.Requirement.PlanRationale)
      val ps = problems.result()
      if ps.nonEmpty && !last.value then Left(ps)
      else
        Right(
          (
            kept.take(cap),
            dropped.result() ++ ps.map(Content.NotProposed(_))
          )
        )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def verify(
      goal: Goal.Slug,
      rationale: ArticlesAnswer.Rationale,
      cs: List[Candidate],
      dropped: List[Problem],
      slugFree: Article.Slug => Boolean,
      hits: Article.Filter => (ProposedArticle.Hits, Set[Citekey]),
      existing: List[(Article.Slug, Set[Citekey])],
      limits: Limits
  ): ArticlePlan =
    @tailrec def loop(
        rest: List[Candidate],
        acc: Vector[(ProposedArticle, Set[Citekey])],
        drop: Vector[Problem]
    ): ArticlePlan =
      rest match
        case Nil =>
          ArticlePlan(
            ArticlePlan.Goal(goal.value),
            ArticlePlan.Rationale(rationale.value.trim),
            acc.map(_._1).toList,
            dropped ++ drop
          )
        case c :: tail =>
          def refused(rule: Content.Requirement) = Content.Proposal(rule, Where(s"'${c.slug}'"))
          if !slugFree(c.slug) then loop(tail, acc, drop :+ refused(Content.Requirement.SlugFree))
          else
            val (n, set) = hits(c.filter)
            val against = existing ++ acc.map((p, s) => (p.slug, s))
            val worst = against.map((slug, theirs) => (slug, overlap(set, theirs))).maxByOption(_._2.value: Double)
            if n.value < limits.minSources.value then
              loop(
                tail,
                acc,
                drop :+ refused(
                  Content.Requirement.ArticleSources(Raw(c.filter.line), Count.of(n), Count.of(limits.minSources))
                )
              )
            else
              worst match
                case Some((o, share)) if share.value >= limits.maxOverlap.value =>
                  loop(
                    tail,
                    acc,
                    drop :+ refused(
                      Content.Requirement.ArticleOverlap(Share.of(share), Raw.of(o), Share.of(limits.maxOverlap))
                    )
                  )
                case w =>
                  loop(
                    tail,
                    acc :+ (
                      ProposedArticle(
                        c.slug,
                        c.title,
                        c.kind,
                        c.filter,
                        c.why,
                        n,
                        w.fold(ProposedArticle.Overlap(0.0))(_._2),
                        w.filter(_._2.value > 0).map(x => ProposedArticle.OverlapWith(x._1.value))
                      ),
                      set
                    ),
                    drop
                  )
    loop(cs, Vector.empty, Vector.empty)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def logLine(goal: Goal.Slug, plan: ArticlePlan, outcome: Outcome, runId: Run.Id): LogLine =
    LogLine(
      s"propose-articles $goal: ${Plural.count(Plural.CountN(plan.articles.size.toLong), Plural.CountSingular("article"))} proposed, ${plan.dropped.size} dropped — $outcome " +
        s"(agent run ${runId.value.take(8)})"
    )
