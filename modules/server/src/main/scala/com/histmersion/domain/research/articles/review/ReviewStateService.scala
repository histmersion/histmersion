package com.histmersion.domain.research.articles.review

import com.histmersion.api.ReviewGoal
import com.histmersion.domain.ArticleReviewStatus.Facts
import com.histmersion.util.database.Transactor

/** What the review manager, the review ledgers, the runs and the goals record about articles, read for their review
  * state. A mechanism over the engine's tables and the ledgers' JSON, counted in SQL, so it has no in-memory twin.
  */
trait ReviewStateService:

  /** The review facts of each of `slugs` and the time of its item's last change; a slug nothing records maps to none.
    */
  def facts(slugs: List[String]): Map[String, (Facts, Option[String])]

  /** When each of `runs` started, for the runs still recorded. */
  def runStarts(runs: List[String]): Map[String, String]

  /** The research goals that link the article. */
  def goals(slug: String): List[ReviewGoal]

object ReviewStateService:

  def apply(db: Transactor): ReviewStateService = Implementation(db)

  final private class Implementation(db: Transactor) extends ReviewStateService:
    def facts(slugs: List[String]): Map[String, (Facts, Option[String])] =
      if slugs.isEmpty then Map.empty else db.read(ArticleReviewRepo.facts(slugs))
    def runStarts(runs: List[String]): Map[String, String] =
      if runs.isEmpty then Map.empty else db.read(ArticleReviewRepo.runStarts(runs))
    def goals(slug: String): List[ReviewGoal] = db.read(ArticleReviewRepo.goals(slug))
