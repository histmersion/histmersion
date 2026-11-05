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
  def facts(slugs: List[com.histmersion.domain.research.articles.review.ReviewStateService.FactsSlugs]): Map[com.histmersion.domain.research.articles.review.ReviewStateService.FactsResultPart1, (Facts, Option[com.histmersion.domain.research.articles.review.ReviewStateService.FactsResultPart2Part2])]

  /** When each of `runs` started, for the runs still recorded. */
  def runStarts(runs: List[com.histmersion.domain.research.articles.review.ReviewStateService.RunStartsRuns]): Map[com.histmersion.domain.research.articles.review.ReviewStateService.RunStartsResultPart1, com.histmersion.domain.research.articles.review.ReviewStateService.RunStartsResultPart2]

  /** The research goals that link the article. */
  def goals(slug: com.histmersion.domain.research.articles.review.ReviewStateService.GoalsSlug): List[ReviewGoal]

object ReviewStateService:

  type FactsResultPart1 = FactsResultPart1.T
  object FactsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FactsResultPart2Part2 = FactsResultPart2Part2.T
  object FactsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FactsSlugs = FactsSlugs.T
  object FactsSlugs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStartsResultPart1 = RunStartsResultPart1.T
  object RunStartsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStartsResultPart2 = RunStartsResultPart2.T
  object RunStartsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStartsRuns = RunStartsRuns.T
  object RunStartsRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsSlug = GoalsSlug.T
  object GoalsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): ReviewStateService = Implementation(db)

  final private class Implementation(db: Transactor) extends ReviewStateService:
    def facts(slugs: List[com.histmersion.domain.research.articles.review.ReviewStateService.Implementation.FactsSlugs]): Map[com.histmersion.domain.research.articles.review.ReviewStateService.Implementation.FactsResultPart1, (Facts, Option[com.histmersion.domain.research.articles.review.ReviewStateService.Implementation.FactsResultPart2Part2])] =
      if slugs.isEmpty then Map.empty else db.read(ArticleReviewRepo.facts(slugs))
    def runStarts(runs: List[com.histmersion.domain.research.articles.review.ReviewStateService.Implementation.RunStartsRuns]): Map[com.histmersion.domain.research.articles.review.ReviewStateService.Implementation.RunStartsResultPart1, com.histmersion.domain.research.articles.review.ReviewStateService.Implementation.RunStartsResultPart2] =
      if runs.isEmpty then Map.empty else db.read(ArticleReviewRepo.runStarts(runs))
    def goals(slug: com.histmersion.domain.research.articles.review.ReviewStateService.Implementation.GoalsSlug): List[ReviewGoal] = db.read(ArticleReviewRepo.goals(slug))

  object Implementation:
    type FactsResultPart1 = FactsResultPart1.T
    object FactsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FactsResultPart2Part2 = FactsResultPart2Part2.T
    object FactsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FactsSlugs = FactsSlugs.T
    object FactsSlugs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunStartsResultPart1 = RunStartsResultPart1.T
    object RunStartsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunStartsResultPart2 = RunStartsResultPart2.T
    object RunStartsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunStartsRuns = RunStartsRuns.T
    object RunStartsRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsSlug = GoalsSlug.T
    object GoalsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
