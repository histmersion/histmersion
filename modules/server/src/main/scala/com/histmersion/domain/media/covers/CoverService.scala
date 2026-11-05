package com.histmersion.domain.media.covers

import com.histmersion.util.database.Transactor

/** The sources a run of covers and illustrations looks at: those with figures and no illustration yet, those with an
  * ISBN or OCLC number and no cover yet (not asked of Open Library yet).
  */
trait CoverService:
  def illustrationCandidates(only: Option[com.histmersion.domain.media.covers.CoverService.IllustrationCandidatesOnly], refresh: com.histmersion.domain.media.covers.CoverService.IllustrationCandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.IllustrationCandidatesLimit): List[com.histmersion.domain.media.covers.CoverService.IllustrationCandidatesResult]
  def coverCandidates(only: Option[com.histmersion.domain.media.covers.CoverService.CoverCandidatesOnly], refresh: com.histmersion.domain.media.covers.CoverService.CoverCandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.CoverCandidatesLimit): List[CoverKeys]

object CoverService:
  type IllustrationCandidatesResult = IllustrationCandidatesResult.T
  object IllustrationCandidatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IllustrationCandidatesOnly = IllustrationCandidatesOnly.T
  object IllustrationCandidatesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IllustrationCandidatesRefresh = IllustrationCandidatesRefresh.T
  object IllustrationCandidatesRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IllustrationCandidatesLimit = IllustrationCandidatesLimit.T
  object IllustrationCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverCandidatesOnly = CoverCandidatesOnly.T
  object CoverCandidatesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverCandidatesRefresh = CoverCandidatesRefresh.T
  object CoverCandidatesRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverCandidatesLimit = CoverCandidatesLimit.T
  object CoverCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): CoverService = Implementation(db)

  final private class Implementation(db: Transactor) extends CoverService:
    def illustrationCandidates(only: Option[com.histmersion.domain.media.covers.CoverService.Implementation.IllustrationCandidatesOnly], refresh: com.histmersion.domain.media.covers.CoverService.Implementation.IllustrationCandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.Implementation.IllustrationCandidatesLimit): List[com.histmersion.domain.media.covers.CoverService.Implementation.IllustrationCandidatesResult] =
      db.read(CoverRepo.illustrationCandidates(only, refresh, limit))
    def coverCandidates(only: Option[com.histmersion.domain.media.covers.CoverService.Implementation.CoverCandidatesOnly], refresh: com.histmersion.domain.media.covers.CoverService.Implementation.CoverCandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.Implementation.CoverCandidatesLimit): List[CoverKeys] =
      db.read(CoverRepo.coverCandidates(only, refresh, limit))

  object Implementation:
    type IllustrationCandidatesResult = IllustrationCandidatesResult.T
    object IllustrationCandidatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IllustrationCandidatesOnly = IllustrationCandidatesOnly.T
    object IllustrationCandidatesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IllustrationCandidatesRefresh = IllustrationCandidatesRefresh.T
    object IllustrationCandidatesRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IllustrationCandidatesLimit = IllustrationCandidatesLimit.T
    object IllustrationCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CoverCandidatesOnly = CoverCandidatesOnly.T
    object CoverCandidatesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CoverCandidatesRefresh = CoverCandidatesRefresh.T
    object CoverCandidatesRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CoverCandidatesLimit = CoverCandidatesLimit.T
    object CoverCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Media's rows alone, in memory: no source is held, so no source has figures or an ISBN to look at. */
  final class InMemory extends CoverService:
    def illustrationCandidates(only: Option[com.histmersion.domain.media.covers.CoverService.InMemory.IllustrationCandidatesOnly], refresh: com.histmersion.domain.media.covers.CoverService.InMemory.IllustrationCandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.InMemory.IllustrationCandidatesLimit): List[com.histmersion.domain.media.covers.CoverService.InMemory.IllustrationCandidatesResult] = Nil
    def coverCandidates(only: Option[com.histmersion.domain.media.covers.CoverService.InMemory.CoverCandidatesOnly], refresh: com.histmersion.domain.media.covers.CoverService.InMemory.CoverCandidatesRefresh, limit: com.histmersion.domain.media.covers.CoverService.InMemory.CoverCandidatesLimit): List[CoverKeys] = Nil

  object InMemory:
    type IllustrationCandidatesResult = IllustrationCandidatesResult.T
    object IllustrationCandidatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IllustrationCandidatesOnly = IllustrationCandidatesOnly.T
    object IllustrationCandidatesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IllustrationCandidatesRefresh = IllustrationCandidatesRefresh.T
    object IllustrationCandidatesRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IllustrationCandidatesLimit = IllustrationCandidatesLimit.T
    object IllustrationCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CoverCandidatesOnly = CoverCandidatesOnly.T
    object CoverCandidatesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CoverCandidatesRefresh = CoverCandidatesRefresh.T
    object CoverCandidatesRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CoverCandidatesLimit = CoverCandidatesLimit.T
    object CoverCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
