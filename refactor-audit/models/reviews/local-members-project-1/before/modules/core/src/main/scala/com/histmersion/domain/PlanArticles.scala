package com.histmersion.domain

import com.histmersion.domain.research.Slug

import scala.annotation.tailrec
import com.histmersion.api.Plural
import com.histmersion.api.research.Articles
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.research.Article.Plans.{ArticlePlan, ProposedArticle}
import com.histmersion.api.research.Article

/** The `propose-articles` pipeline's typed answer and its pure checks (docs/research-process-managers.md §4 (b), phase
  * 2): a planner proposes the articles a research area (a goal) should have — each a new slug, a title, a kind and the
  * filter it is compiled from. CODE keeps only the ones that can be compiled and are new: the slug is valid and free,
  * the filter returns at least `minSources` sources, and it shares less than `maxOverlap` of them with every article of
  * the area (and with every proposal kept before it). A person ALWAYS approves the plan before anything is compiled.
  */
object PlanArticles:

  type OverlapResult = OverlapResult.T
  object OverlapResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapMine = OverlapMine.T
  object OverlapMine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapTheirs = OverlapTheirs.T
  object OverlapTheirs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart2Part2 = CheckResultPart2Part2.T
  object CheckResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckMaxArticles = CheckMaxArticles.T
  object CheckMaxArticles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckLast = CheckLast.T
  object CheckLast extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyGoal = VerifyGoal.T
  object VerifyGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyRationale = VerifyRationale.T
  object VerifyRationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyDropped = VerifyDropped.T
  object VerifyDropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifySlugFreeArg1 = VerifySlugFreeArg1.T
  object VerifySlugFreeArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifySlugFreeResult = VerifySlugFreeResult.T
  object VerifySlugFreeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyHitsResultPart1 = VerifyHitsResultPart1.T
  object VerifyHitsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyHitsResultPart2 = VerifyHitsResultPart2.T
  object VerifyHitsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyExistingPart1 = VerifyExistingPart1.T
  object VerifyExistingPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyExistingPart2 = VerifyExistingPart2.T
  object VerifyExistingPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogLineResult = LogLineResult.T
  object LogLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogLineGoal = LogLineGoal.T
  object LogLineGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogLineOutcome = LogLineOutcome.T
  object LogLineOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class ArticleAnswer(
      slug: ArticleAnswer.Slug,
      title: ArticleAnswer.Title,
      kind: Option[ArticleAnswer.Kind] = None,
      query: Option[ArticleAnswer.Query] = None,
      topics: List[ArticleAnswer.Topics] = Nil,
      tags: List[ArticleAnswer.Tags] = Nil,
      why: ArticleAnswer.Why = ""
  ) derives KindlingsCodecAsObject
  object ArticleAnswer:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  final case class ArticlesAnswer(rationale: ArticlesAnswer.Rationale, articles: List[ArticleAnswer] = Nil) derives KindlingsCodecAsObject
  object ArticlesAnswer:
    type Rationale = Rationale.T
    object Rationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    object MinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxOverlap = MaxOverlap.T
    object MaxOverlap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxArticles = MaxArticles.T
    object MaxArticles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One proposal whose shape passed: what [[verify]] measures. */
  final case class Candidate(slug: Candidate.Slug, title: Candidate.Title, kind: Candidate.Kind, filter: Article.Filter, why: Candidate.Why)

  object Candidate:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The share of `mine` that `theirs` holds already (0 when `mine` is empty). */
  def overlap(mine: Set[com.histmersion.domain.PlanArticles.OverlapMine], theirs: Set[com.histmersion.domain.PlanArticles.OverlapTheirs]): com.histmersion.domain.PlanArticles.OverlapResult =
    if mine.isEmpty then 0.0 else mine.count(theirs).toDouble / mine.size

  /** The shape of each proposed article: a valid slug (not the goal's, not twice), a title, a kind, a filter that
    * selects something. Left = the problems a retry should fix (nothing usable, an empty rationale); the ones it cannot
    * fix are dropped with why.
    */
  def check(a: ArticlesAnswer, maxArticles: com.histmersion.domain.PlanArticles.CheckMaxArticles, last: com.histmersion.domain.PlanArticles.CheckLast): Either[List[com.histmersion.domain.PlanArticles.CheckResultPart1], (List[Candidate], List[com.histmersion.domain.PlanArticles.CheckResultPart2Part2])] =
    val problems = List.newBuilder[String]
    val dropped = List.newBuilder[String]
    val kept = a.articles
      .foldLeft(Vector.empty[Candidate]) { (acc, x) =>
        Slug.parse(Option(x.slug).getOrElse("")).map(_.value) match
          case Left(m)                                            => problems += s"article '${x.slug}': $m"; acc
          case Right(s) if Option(x.title).forall(_.trim.isEmpty) => problems += s"article '$s' has no title"; acc
          case Right(s) if acc.exists(_.slug == s)                => dropped += s"'$s' proposed twice"; acc
          case Right(s)                                           =>
            val kind = x.kind.map(_.trim).filter(_.nonEmpty).getOrElse("concept")
            Articles.kindOf(kind) match
              case Left(m)  => problems += s"article '$s': $m"; acc
              case Right(k) =>
                Article.Filter.normalise(Article.Filter(query = x.query, topics = x.topics, tags = x.tags)) match
                  case Left(m)        => problems += s"article '$s': $m"; acc
                  case Right(None)    => problems += s"article '$s' has no filter: give a query, topics or tags"; acc
                  case Right(Some(f)) =>
                    acc :+ Candidate(s, x.title.trim, k, f, Option(x.why).map(_.trim).getOrElse(""))
      }
      .toList
    if kept.size > maxArticles then problems += s"at most $maxArticles articles (you gave ${kept.size})"
    if a.articles.isEmpty then problems += "the plan proposes no article"
    if Option(a.rationale).forall(_.trim.isEmpty) then
      problems += "the rationale is empty — say how the articles cover the area"
    val ps = problems.result()
    if ps.nonEmpty && !last then Left(ps)
    else Right((kept.take(maxArticles), dropped.result() ++ ps.map(p => s"not proposed: $p")))

  /** Verify the candidates against the corpus: `slugFree` (no article and no old slug of one), `hits` (a filter's
    * source count and its sources), `existing` (each article of the area with its sources). Every candidate that passes
    * is kept, and the next ones are measured against it too.
    */
  def verify(
      goal: com.histmersion.domain.PlanArticles.VerifyGoal,
      rationale: com.histmersion.domain.PlanArticles.VerifyRationale,
      cs: List[Candidate],
      dropped: List[com.histmersion.domain.PlanArticles.VerifyDropped],
      slugFree: com.histmersion.domain.PlanArticles.VerifySlugFreeArg1 => com.histmersion.domain.PlanArticles.VerifySlugFreeResult,
      hits: Article.Filter => (com.histmersion.domain.PlanArticles.VerifyHitsResultPart1, Set[com.histmersion.domain.PlanArticles.VerifyHitsResultPart2]),
      existing: List[(com.histmersion.domain.PlanArticles.VerifyExistingPart1, Set[com.histmersion.domain.PlanArticles.VerifyExistingPart2])],
      limits: Limits
  ): ArticlePlan =
    @tailrec def loop(
        rest: List[Candidate],
        acc: Vector[(ProposedArticle, Set[String])],
        drop: Vector[String]
    ): ArticlePlan =
      rest match
        case Nil       => ArticlePlan(goal, rationale.trim, acc.map(_._1).toList, dropped ++ drop)
        case c :: tail =>
          if !slugFree(c.slug) then loop(tail, acc, drop :+ s"'${c.slug}': an article with this slug exists (or did)")
          else
            val (n, set) = hits(c.filter)
            val against = existing ++ acc.map((p, s) => (p.slug, s))
            val worst = against.map((slug, theirs) => (slug, overlap(set, theirs))).maxByOption(_._2)
            if n < limits.minSources then
              loop(
                tail,
                acc,
                drop :+ s"'${c.slug}': ${c.filter.line} returns ${Plural.count(n, "source")}; at least ${limits.minSources}"
              )
            else
              worst match
                case Some((o, share)) if share >= limits.maxOverlap =>
                  loop(
                    tail,
                    acc,
                    drop :+ f"'${c.slug}': ${share * 100}%.0f%% of its sources are [[$o]]'s already (less than ${limits.maxOverlap * 100}%.0f%%)"
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
                        w.fold(0.0)(_._2),
                        w.filter(_._2 > 0).map(_._1)
                      ),
                      set
                    ),
                    drop
                  )
    loop(cs, Vector.empty, Vector.empty)

  /** The worklog line of a run. */
  def logLine(goal: com.histmersion.domain.PlanArticles.LogLineGoal, plan: ArticlePlan, outcome: com.histmersion.domain.PlanArticles.LogLineOutcome, runId: com.histmersion.domain.automation.Run.Id): com.histmersion.domain.PlanArticles.LogLineResult =
    s"propose-articles $goal: ${Plural.count(plan.articles.size, "article")} proposed, ${plan.dropped.size} dropped — $outcome " +
      s"(agent run ${runId.take(8)})"
