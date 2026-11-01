package com.histmersion.domain.media.covers

import com.histmersion.util.database.Transactor

/** The sources a run of covers and illustrations looks at: those with figures and no illustration yet, those with an
  * ISBN or OCLC number and no cover yet (not asked of Open Library yet).
  */
trait CoverService:
  def illustrationCandidates(only: Option[String], refresh: Boolean, limit: Int): List[String]
  def coverCandidates(only: Option[String], refresh: Boolean, limit: Int): List[CoverKeys]

object CoverService:
  def apply(db: Transactor): CoverService = Implementation(db)

  private final class Implementation(db: Transactor) extends CoverService:
    def illustrationCandidates(only: Option[String], refresh: Boolean, limit: Int): List[String] =
      db.read(CoverRepo.illustrationCandidates(only, refresh, limit))
    def coverCandidates(only: Option[String], refresh: Boolean, limit: Int): List[CoverKeys] =
      db.read(CoverRepo.coverCandidates(only, refresh, limit))

  /** Media's rows alone, in memory: no source is held, so no source has figures or an ISBN to look at. */
  final class InMemory extends CoverService:
    def illustrationCandidates(only: Option[String], refresh: Boolean, limit: Int): List[String] = Nil
    def coverCandidates(only: Option[String], refresh: Boolean, limit: Int): List[CoverKeys] = Nil
