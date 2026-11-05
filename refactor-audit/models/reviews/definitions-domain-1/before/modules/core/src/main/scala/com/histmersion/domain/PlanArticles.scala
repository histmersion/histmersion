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

  final case class ArticleAnswer(
      slug: String,
      title: String,
      kind: Option[String] = None,
      query: Option[String] = None,
      topics: List[String] = Nil,
      tags: List[String] = Nil,
      why: String = ""
  ) derives KindlingsCodecAsObject
  object ArticleAnswer:
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

  final case class ArticlesAnswer(rationale: String, articles: List[ArticleAnswer] = Nil) derives KindlingsCodecAsObject
  object ArticlesAnswer:
    given Schema[ArticlesAnswer] = KindlingsSchema
      .derived[ArticlesAnswer]
      .schema
      .modify(_.rationale)(
        _.description("How the articles cover the research area and what they leave open, in two or three sentences")
      )
      .modify(_.articles)(_.description("The new articles, the most needed first"))

  /** The checks (config `agents.seed.*`, a seeding manager's `minSources`/`maxArticles` over them). */
  final case class Limits(minSources: Int, maxOverlap: Double, maxArticles: Int)

  /** One proposal whose shape passed: what [[verify]] measures. */
  final case class Candidate(slug: String, title: String, kind: String, filter: Article.Filter, why: String)

  /** The share of `mine` that `theirs` holds already (0 when `mine` is empty). */
  def overlap(mine: Set[String], theirs: Set[String]): Double =
    if mine.isEmpty then 0.0 else mine.count(theirs).toDouble / mine.size

  /** The shape of each proposed article: a valid slug (not the goal's, not twice), a title, a kind, a filter that
    * selects something. Left = the problems a retry should fix (nothing usable, an empty rationale); the ones it cannot
    * fix are dropped with why.
    */
  def check(a: ArticlesAnswer, maxArticles: Int, last: Boolean): Either[List[String], (List[Candidate], List[String])] =
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
      goal: String,
      rationale: String,
      cs: List[Candidate],
      dropped: List[String],
      slugFree: String => Boolean,
      hits: Article.Filter => (Int, Set[String]),
      existing: List[(String, Set[String])],
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
  def logLine(goal: String, plan: ArticlePlan, outcome: String, runId: String): String =
    s"propose-articles $goal: ${Plural.count(plan.articles.size, "article")} proposed, ${plan.dropped.size} dropped — $outcome " +
      s"(agent run ${runId.take(8)})"
