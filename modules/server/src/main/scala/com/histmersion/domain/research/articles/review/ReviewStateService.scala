package com.histmersion.domain.research.articles.review

import com.histmersion.api.research.ReviewGoal
import com.histmersion.domain.ArticleReviewStatus.Facts
import com.histmersion.util.database.Transactor

/** What the review manager, the review ledgers, the runs and the goals record about articles, read for their review
  * state. A mechanism over the engine's tables and the ledgers' JSON, counted in SQL, so it has no in-memory twin.
  */
trait ReviewStateService:

  /** The review facts of each of `slugs` and the time of its item's last change; a slug nothing records maps to none.
    */
  def facts(slugs: List[com.histmersion.domain.research.Slug]): Map[com.histmersion.domain.research.Slug, (Facts, Option[ArticleReviewRepo.FactsRow.UpdatedAt])]

  /** When each of `runs` started, for the runs still recorded. */
  def runStarts(runs: List[com.histmersion.domain.automation.Run.Id]): Map[com.histmersion.domain.automation.Run.Id, com.histmersion.domain.ArticleReviewStatus.RunStart]

  /** The research goals that link the article. */
  def goals(slug: com.histmersion.domain.research.Slug): List[ReviewGoal]

object ReviewStateService:
  def apply(db: Transactor): ReviewStateService = Implementation(db)

  final private class Implementation(db: Transactor) extends ReviewStateService:
    def facts(slugs: List[com.histmersion.domain.research.Slug]): Map[com.histmersion.domain.research.Slug, (Facts, Option[ArticleReviewRepo.FactsRow.UpdatedAt])] =
      if slugs.isEmpty then Map.empty else db.read(ArticleReviewRepo.facts(slugs))
    def runStarts(runs: List[com.histmersion.domain.automation.Run.Id]): Map[com.histmersion.domain.automation.Run.Id, com.histmersion.domain.ArticleReviewStatus.RunStart] =
      if runs.isEmpty then Map.empty
      else
        db.read(ArticleReviewRepo.runStarts(runs)).map((id, at) => id -> com.histmersion.api.research.ReviewRound.StartedAt(at))
    def goals(slug: com.histmersion.domain.research.Slug): List[ReviewGoal] = db.read(ArticleReviewRepo.goals(slug))

