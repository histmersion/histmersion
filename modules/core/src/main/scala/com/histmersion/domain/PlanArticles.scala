package com.histmersion
package domain

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.domain.library.Citekey

import scala.annotation.tailrec
import com.histmersion.api.Plural
import com.histmersion.api.research.Articles
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.research.Article.Plans.{ArticlePlan, ProposedArticle}
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Run

/** The `propose-articles` pipeline's typed answer and its pure checks (docs/research-process-managers.md §4 (b), phase
  * 2): a planner proposes the articles a research area (a goal) should have — each a new slug, a title, a kind and the
  * filter it is compiled from. CODE keeps only the ones that can be compiled and are new: the slug is valid and free,
  * the filter returns at least `minSources` sources, and it shares less than `maxOverlap` of them with every article of
  * the area (and with every proposal kept before it). A person ALWAYS approves the plan before anything is compiled.
  */
object PlanArticles:
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
      why: Problem = Problem("")
  ) derives KindlingsCodecAsObject
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

  final case class ArticlesAnswer(rationale: ArticlesAnswer.Rationale, articles: List[ArticleAnswer] = Nil)
      derives KindlingsCodecAsObject
  object ArticlesAnswer:
    type Rationale = Rationale.T
    object Rationale extends Newtype[String, Pure]

    given Schema[ArticlesAnswer] = KindlingsSchema
      .derived[ArticlesAnswer]
      .schema
      .modify(_.rationale)(
        _.description("How the articles cover the research area and what they leave open, in two or three sentences")
      )
      .modify(_.articles)(_.description("The new articles, the most needed first"))

  /** The checks (config `agents.seed.*`, a seeding manager's `minSources`/`maxArticles` over them). */
  final case class Limits(minSources: Limits.MinSources, maxOverlap: Limits.MaxOverlap, maxArticles: Limits.MaxArticles)

  object Limits:
    type MinSources = MinSources.T
    object MinSources extends Newtype[Int, Pure]
    type MaxOverlap = MaxOverlap.T
    object MaxOverlap extends Newtype[Double, Pure]
    type MaxArticles = MaxArticles.T
    object MaxArticles extends Newtype[Int, Pure]

  /** One proposal whose shape passed: what [[verify]] measures. */
  final case class Candidate(
      slug: Article.Slug,
      title: Article.Title,
      kind: Article.Kind.Wire,
      filter: Article.Filter,
      why: Problem
  )

  /** The share of `mine` that `theirs` holds already (0 when `mine` is empty). */
  def overlap(mine: Set[Citekey], theirs: Set[Citekey]): ProposedArticle.Overlap =
    ProposedArticle.Overlap(if mine.isEmpty then 0.0 else mine.count(theirs).toDouble / mine.size)

  /** The shape of each proposed article: a valid slug (not the goal's, not twice), a title, a kind, a filter that
    * selects something. Left = the problems a retry should fix (nothing usable, an empty rationale); the ones it cannot
    * fix are dropped with why.
    */
  def check(
      a: ArticlesAnswer,
      maxArticles: Limits.MaxArticles,
      last: LastRound
  ): Either[List[Problem], (List[Candidate], List[ArticlePlan.Dropped])] =
    val problems = List.newBuilder[Problem]
    val dropped = List.newBuilder[ArticlePlan.Dropped]
    val kept = a.articles
      .foldLeft(Vector.empty[Candidate]) { (acc, x) =>
        Articles.slugOf(Article.Slug(Option(x.slug).fold("")(_.value))) match
          case Left(m) => problems += Problem(s"article '${x.slug}': $m"); acc
          case Right(s) if Option(x.title).forall(_.value.trim.isEmpty) =>
            problems += Problem(s"article '$s' has no title"); acc
          case Right(s) if acc.exists(_.slug == s) => dropped += ArticlePlan.Dropped(s"'$s' proposed twice"); acc
          case Right(s)                            =>
            val kind = x.kind.map(_.value.trim).filter(_.nonEmpty).getOrElse("concept")
            Articles.kindOf(Article.Kind.Wire(kind)) match
              case Left(m)  => problems += Problem(s"article '$s': $m"); acc
              case Right(k) =>
                Article.Filter.normalise(
                  Article.Filter(
                    query = x.query.map(q => Article.Filter.Query(q.value)),
                    topics = x.topics.map(t => Article.Filter.Topics(t.value)),
                    tags = x.tags.map(t => Article.Filter.Tags(t.value))
                  )
                ) match
                  case Left(m)     => problems += Problem(s"article '$s': $m"); acc
                  case Right(None) =>
                    problems += Problem(s"article '$s' has no filter: give a query, topics or tags"); acc
                  case Right(Some(f)) =>
                    acc :+ Candidate(
                      s,
                      Article.Title(x.title.value.trim),
                      k,
                      f,
                      Problem(Option(x.why).map(_.value.trim).getOrElse(""))
                    )
      }
      .toList
    if kept.size > maxArticles.value then problems += Problem(s"at most $maxArticles articles (you gave ${kept.size})")
    if a.articles.isEmpty then problems += Problem("the plan proposes no article")
    if Option(a.rationale).forall(_.value.trim.isEmpty) then
      problems += Problem("the rationale is empty — say how the articles cover the area")
    val ps = problems.result()
    if ps.nonEmpty && !last.value then Left(ps)
    else
      Right((kept.take(maxArticles.value), dropped.result() ++ ps.map(p => ArticlePlan.Dropped(s"not proposed: $p"))))

  /** Verify the candidates against the corpus: `slugFree` (no article and no old slug of one), `hits` (a filter's
    * source count and its sources), `existing` (each article of the area with its sources). Every candidate that passes
    * is kept, and the next ones are measured against it too.
    */
  def verify(
      goal: Goal.Slug,
      rationale: ArticlesAnswer.Rationale,
      cs: List[Candidate],
      dropped: List[ArticlePlan.Dropped],
      slugFree: Article.Slug => Boolean,
      hits: Article.Filter => (ProposedArticle.Hits, Set[Citekey]),
      existing: List[(Article.Slug, Set[Citekey])],
      limits: Limits
  ): ArticlePlan =
    @tailrec def loop(
        rest: List[Candidate],
        acc: Vector[(ProposedArticle, Set[Citekey])],
        drop: Vector[ArticlePlan.Dropped]
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
          if !slugFree(c.slug) then
            loop(tail, acc, drop :+ ArticlePlan.Dropped(s"'${c.slug}': an article with this slug exists (or did)"))
          else
            val (n, set) = hits(c.filter)
            val against = existing ++ acc.map((p, s) => (p.slug, s))
            val worst = against.map((slug, theirs) => (slug, overlap(set, theirs))).maxByOption(_._2.value: Double)
            if n.value < limits.minSources.value then
              loop(
                tail,
                acc,
                drop :+ ArticlePlan.Dropped(
                  s"'${c.slug}': ${c.filter.line} returns ${Plural.count(Plural.CountN(n.value.toLong), Plural.CountSingular("source"))}; at least ${limits.minSources}"
                )
              )
            else
              worst match
                case Some((o, share)) if share.value >= limits.maxOverlap.value =>
                  loop(
                    tail,
                    acc,
                    drop :+ ArticlePlan.Dropped(
                      f"'${c.slug}': ${share.value * 100}%.0f%% of its sources are [[$o]]'s already (less than ${limits.maxOverlap.value * 100}%.0f%%)"
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
                        Problem.wire(c.why),
                        n,
                        w.fold(ProposedArticle.Overlap(0.0))(_._2),
                        w.filter(_._2.value > 0).map(x => ProposedArticle.OverlapWith(x._1.value))
                      ),
                      set
                    ),
                    drop
                  )
    loop(cs, Vector.empty, Vector.empty)

  /** The worklog line of a run. */
  def logLine(goal: Goal.Slug, plan: ArticlePlan, outcome: Outcome, runId: Run.Id): LogLine =
    LogLine(
      s"propose-articles $goal: ${Plural.count(Plural.CountN(plan.articles.size.toLong), Plural.CountSingular("article"))} proposed, ${plan.dropped.size} dropped — $outcome " +
        s"(agent run ${runId.value.take(8)})"
    )
