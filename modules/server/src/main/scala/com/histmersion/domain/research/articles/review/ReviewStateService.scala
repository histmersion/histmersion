package com.histmersion
package domain.research.articles.review

import com.histmersion.api.research.ReviewGoal
import com.histmersion.domain.ArticleReviewStatus.Facts
import com.histmersion.util.database.Transactor
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.Run
import com.histmersion.domain.ArticleReviewStatus
import com.histmersion.api.research.ReviewRound

/** What the review manager, the review ledgers, the runs and the goals record about articles, read for their review
  * state. A mechanism over the engine's tables and the ledgers' JSON, counted in SQL, so it has no in-memory twin.
  */
trait ReviewStateService:

  /** The review facts of each of `slugs` and the time of its item's last change; a slug nothing records maps to none.
    */
  def facts(slugs: List[Slug]): Map[Slug, (Facts, Option[ArticleReviewRepo.FactsRow.UpdatedAt])]

  /** When each of `runs` started, for the runs still recorded. */
  def runStarts(runs: List[Run.Id]): Map[Run.Id, ArticleReviewStatus.RunStart]

  /** The research goals that link the article. */
  def goals(slug: Slug): List[ReviewGoal]

object ReviewStateService:
  def apply(db: Transactor): ReviewStateService = Implementation(db)

  final private class Implementation(db: Transactor) extends ReviewStateService:
    def facts(slugs: List[Slug]): Map[Slug, (Facts, Option[ArticleReviewRepo.FactsRow.UpdatedAt])] =
      if slugs.isEmpty then Map.empty else db.read(ArticleReviewRepo.facts(slugs))
    def runStarts(runs: List[Run.Id]): Map[Run.Id, ArticleReviewStatus.RunStart] =
      if runs.isEmpty then Map.empty
      else db.read(ArticleReviewRepo.runStarts(runs)).map((id, at) => id -> ReviewRound.StartedAt(at))
    def goals(slug: Slug): List[ReviewGoal] = db.read(ArticleReviewRepo.goals(slug))
