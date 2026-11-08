package com.histmersion
package domain.research.workflows

import com.histmersion.domain.ReviewLedger.Ledger
import com.histmersion.domain.research.ResearchState
import com.histmersion.domain.research.Slug
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*
import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The articles' review ledgers: every finding the review panel raised on an article, with what became of it, read at
  * the start of a `review-article` run and written after each of its passes; and which articles passed their review.
  */
trait ReviewLedgerService:

  /** The article's ledger; None when it has none yet. A stored one that no longer decodes is an error. */
  def get(slug: Slug): Either[String, Option[Ledger]]

  /** Write the ledger (insert or replace). The article must exist. */
  def put(ledger: Ledger): Unit

  /** The articles whose ledger holds no open MAJOR and that review manager `reviewId` has no item open on. */
  def passedReview(reviewId: ReviewLedgerService.ReviewId): Set[Slug]

object ReviewLedgerService:
  type ReviewId = ReviewId.T
  object ReviewId extends RefinedType[String, Pure]

  def apply(db: Transactor): ReviewLedgerService = Implementation(db)

  final private class Implementation(db: Transactor) extends ReviewLedgerService:
    def get(slug: Slug): Either[String, Option[Ledger]] = db.read(ReviewLedgerRepo.get(slug))
    def put(ledger: Ledger): Unit = db.transact(ReviewLedgerRepo.put(ledger))
    def passedReview(reviewId: ReviewId): Set[Slug] = db.read(ReviewLedgerRepo.passedReview(ReviewLedgerRepo.ReviewId(reviewId.value)))


  /** Over the ledgers of `state`, as a database holding only research's rows answers: no manager item is stored, so an
    * article passed once its ledger holds no open MAJOR; a ledger of an article that is not stored is refused, as the
    * database's reference to the article refuses it.
    */
  final class InMemory(state: AtomicReference[ResearchState]) extends ReviewLedgerService:
    def get(slug: Slug): Either[String, Option[Ledger]] = Right(state.get.ledgers.get(ResearchState.LedgersKey(slug.value)))

    def put(ledger: Ledger): Unit =
      val before = state.getAndUpdate(st =>
        if st.articles.contains(ResearchState.ArticlesKey(ledger.slug.value)) then
          st.modify(_.ledgers).using(_.updated(ResearchState.LedgersKey(ledger.slug.value), ledger))
        else st
      )
      if !before.articles.contains(ResearchState.ArticlesKey(ledger.slug.value)) then
        throw IllegalStateException(s"no article '${ledger.slug}' for its review ledger")

    def passedReview(reviewId: ReviewId): Set[Slug] =
      state.get.ledgers.collect { case (slug, l) if l.openMajors.isEmpty => Slug.assume(slug.value) }.toSet

