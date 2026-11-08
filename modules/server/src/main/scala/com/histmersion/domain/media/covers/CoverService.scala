package com.histmersion
package domain.media.covers

import com.histmersion.util.database.Transactor
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The sources a run of covers and illustrations looks at: those with figures and no illustration yet, those with an
  * ISBN or OCLC number and no cover yet (not asked of Open Library yet).
  */
trait CoverService:
  def illustrationCandidates(only: Option[Citekey], refresh: CoverService.CandidatesRefresh, limit: CoverService.CandidatesLimit): List[Citekey]
  def coverCandidates(only: Option[Citekey], refresh: CoverService.CandidatesRefresh, limit: CoverService.CandidatesLimit): List[CoverKeys]

object CoverService:
  type CandidatesLimit = CandidatesLimit.T
  object CandidatesLimit extends RefinedType[Int, Pure]
  type CandidatesRefresh = CandidatesRefresh.T
  object CandidatesRefresh extends RefinedType[Boolean, Pure]

  def apply(db: Transactor): CoverService = Implementation(db)

  final private class Implementation(db: Transactor) extends CoverService:
    def illustrationCandidates(only: Option[Citekey], refresh: CandidatesRefresh, limit: CandidatesLimit): List[Citekey] =
      db.read(CoverRepo.illustrationCandidates(only.map(c => CoverRepo.Only(c.value)), CoverRepo.Refresh(refresh.value), CoverRepo.Limit(limit.value))).map(Citekey.assume(_))
    def coverCandidates(only: Option[Citekey], refresh: CandidatesRefresh, limit: CandidatesLimit): List[CoverKeys] =
      db.read(CoverRepo.coverCandidates(only.map(c => CoverRepo.Only(c.value)), CoverRepo.Refresh(refresh.value), CoverRepo.Limit(limit.value)))


  /** Media's rows alone, in memory: no source is held, so no source has figures or an ISBN to look at. */
  final class InMemory extends CoverService:
    def illustrationCandidates(only: Option[Citekey], refresh: CandidatesRefresh, limit: CandidatesLimit): List[Citekey] = Nil
    def coverCandidates(only: Option[Citekey], refresh: CandidatesRefresh, limit: CandidatesLimit): List[CoverKeys] = Nil

