package com.histmersion.domain.media.covers

import com.histmersion.util.database.Transactor

/** The sources a run of covers and illustrations looks at: those with figures and no illustration yet, those with an
  * ISBN or OCLC number and no cover yet (not asked of Open Library yet).
  */
trait CoverService:
  def illustrationCandidates(only: Option[com.histmersion.domain.library.Citekey], refresh: com.histmersion.domain.media.covers.CoverService.CandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.CandidatesLimit): List[com.histmersion.domain.library.Citekey]
  def coverCandidates(only: Option[com.histmersion.domain.library.Citekey], refresh: com.histmersion.domain.media.covers.CoverService.CandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.CandidatesLimit): List[CoverKeys]

object CoverService:
  type CandidatesLimit = CandidatesLimit.T
  object CandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesRefresh = CandidatesRefresh.T
  object CandidatesRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): CoverService = Implementation(db)

  final private class Implementation(db: Transactor) extends CoverService:
    def illustrationCandidates(only: Option[com.histmersion.domain.library.Citekey], refresh: com.histmersion.domain.media.covers.CoverService.CandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.CandidatesLimit): List[com.histmersion.domain.library.Citekey] =
      db.read(CoverRepo.illustrationCandidates(only.map(c => CoverRepo.Only(c.value)), CoverRepo.Refresh(refresh.value), CoverRepo.Limit(limit.value))).map(com.histmersion.domain.library.Citekey.assume(_))
    def coverCandidates(only: Option[com.histmersion.domain.library.Citekey], refresh: com.histmersion.domain.media.covers.CoverService.CandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.CandidatesLimit): List[CoverKeys] =
      db.read(CoverRepo.coverCandidates(only.map(c => CoverRepo.Only(c.value)), CoverRepo.Refresh(refresh.value), CoverRepo.Limit(limit.value)))


  /** Media's rows alone, in memory: no source is held, so no source has figures or an ISBN to look at. */
  final class InMemory extends CoverService:
    def illustrationCandidates(only: Option[com.histmersion.domain.library.Citekey], refresh: com.histmersion.domain.media.covers.CoverService.CandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.CandidatesLimit): List[com.histmersion.domain.library.Citekey] = Nil
    def coverCandidates(only: Option[com.histmersion.domain.library.Citekey], refresh: com.histmersion.domain.media.covers.CoverService.CandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.CandidatesLimit): List[CoverKeys] = Nil

