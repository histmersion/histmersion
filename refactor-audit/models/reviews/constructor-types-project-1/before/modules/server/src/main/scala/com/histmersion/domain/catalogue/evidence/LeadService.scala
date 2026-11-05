package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{EventFindingRef, EventSourceRef, EventTier}
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.events.{EventSelection, EventSourceFilter}
import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.AtomicReference

/** What an event's card and the leads are computed from: the sources attesting an event, the dated findings near it,
  * the facts of each event's lead (its size, the findings around it, the sources proposed and confirmed), and the
  * catalogue events an article's region and period hold.
  */
trait LeadService:
  /** The sources attesting one event: its own, then those of the attestations naming it. */
  def attestingSources(id: com.histmersion.domain.catalogue.evidence.LeadService.AttestingSourcesId): List[EventSourceRef]

  /** The dated, placed findings within `km` and `years` of an event, nearest in time then space first. */
  def nearFindings(id: com.histmersion.domain.catalogue.evidence.LeadService.NearFindingsId, km: com.histmersion.domain.catalogue.evidence.LeadService.NearFindingsKm, years: com.histmersion.domain.catalogue.evidence.LeadService.NearFindingsYears, limit: com.histmersion.domain.catalogue.evidence.LeadService.NearFindingsLimit): List[EventFindingRef]

  /** The facts of the leads of the events `selection` selects, the catalogue's and the largest first, one page; and how
    * many it selects.
    */
  def leads(selection: EventSelection, km: com.histmersion.domain.catalogue.evidence.LeadService.LeadsKm, years: com.histmersion.domain.catalogue.evidence.LeadService.LeadsYears, limit: com.histmersion.domain.catalogue.evidence.LeadService.LeadsLimit, offset: com.histmersion.domain.catalogue.evidence.LeadService.LeadsOffset): (com.histmersion.RowCount, List[LeadRow])

  /** The facts of one event's lead. */
  def leadOf(id: com.histmersion.domain.catalogue.evidence.LeadService.LeadOfId, km: com.histmersion.domain.catalogue.evidence.LeadService.LeadOfKm, years: com.histmersion.domain.catalogue.evidence.LeadService.LeadOfYears): List[LeadRow]

  /** Catalogue events overlapping `period` within `km` of a dated finding of `citekeys` of that period, large enough
    * (`minVei`, `minMagnitude`) or a war, with how many of those findings are near each.
    */
  def articleCandidates(
      citekeys: List[com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesCitekeys],
      period: Option[(com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesPeriodPart1, com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesPeriodPart2)],
      km: com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesKm,
      minVei: com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesMinVei,
      minMagnitude: com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesMinMagnitude,
      limit: com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesLimit
  ): List[(com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesResultPart1, com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesResultPart2, com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesResultPart3, TimeSpan, List[com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesResultPart5], Option[com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesResultPart6], Option[com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesResultPart7], com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesResultPart8, com.histmersion.domain.catalogue.evidence.LeadService.ArticleCandidatesResultPart9)]

object LeadService:
  type AttestingSourcesId = AttestingSourcesId.T
  object AttestingSourcesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NearFindingsId = NearFindingsId.T
  object NearFindingsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NearFindingsKm = NearFindingsKm.T
  object NearFindingsKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NearFindingsYears = NearFindingsYears.T
  object NearFindingsYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NearFindingsLimit = NearFindingsLimit.T
  object NearFindingsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadsKm = LeadsKm.T
  object LeadsKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadsYears = LeadsYears.T
  object LeadsYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadsLimit = LeadsLimit.T
  object LeadsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadsOffset = LeadsOffset.T
  object LeadsOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadOfId = LeadOfId.T
  object LeadOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadOfKm = LeadOfKm.T
  object LeadOfKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadOfYears = LeadOfYears.T
  object LeadOfYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesResultPart1 = ArticleCandidatesResultPart1.T
  object ArticleCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesResultPart2 = ArticleCandidatesResultPart2.T
  object ArticleCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesResultPart3 = ArticleCandidatesResultPart3.T
  object ArticleCandidatesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesResultPart5 = ArticleCandidatesResultPart5.T
  object ArticleCandidatesResultPart5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesResultPart6 = ArticleCandidatesResultPart6.T
  object ArticleCandidatesResultPart6 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesResultPart7 = ArticleCandidatesResultPart7.T
  object ArticleCandidatesResultPart7 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesResultPart8 = ArticleCandidatesResultPart8.T
  object ArticleCandidatesResultPart8 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesResultPart9 = ArticleCandidatesResultPart9.T
  object ArticleCandidatesResultPart9 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesCitekeys = ArticleCandidatesCitekeys.T
  object ArticleCandidatesCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesPeriodPart1 = ArticleCandidatesPeriodPart1.T
  object ArticleCandidatesPeriodPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesPeriodPart2 = ArticleCandidatesPeriodPart2.T
  object ArticleCandidatesPeriodPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesKm = ArticleCandidatesKm.T
  object ArticleCandidatesKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesMinVei = ArticleCandidatesMinVei.T
  object ArticleCandidatesMinVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesMinMagnitude = ArticleCandidatesMinMagnitude.T
  object ArticleCandidatesMinMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleCandidatesLimit = ArticleCandidatesLimit.T
  object ArticleCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor, related: EventSourceFilter): LeadService = Implementation(db, related)

  final private class Implementation(db: Transactor, related: EventSourceFilter) extends LeadService:
    def attestingSources(id: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.AttestingSourcesId): List[EventSourceRef] = db.read(LeadRepo.attestingSources(id))
    def nearFindings(id: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.NearFindingsId, km: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.NearFindingsKm, years: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.NearFindingsYears, limit: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.NearFindingsLimit): List[EventFindingRef] =
      db.read(LeadRepo.nearFindings(id, km, years, limit))
    def leads(selection: EventSelection, km: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.LeadsKm, years: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.LeadsYears, limit: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.LeadsLimit, offset: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.LeadsOffset): (com.histmersion.RowCount, List[LeadRow]) =
      db.read(LeadRepo.leadRows(selection.sql(related), km, years, limit, offset))
    def leadOf(id: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.LeadOfId, km: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.LeadOfKm, years: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.LeadOfYears): List[LeadRow] =
      db.read(LeadRepo.leadRows(Sql.of("e.id = ?", Param.S(id)), km, years, 1, 0))._2
    def articleCandidates(
        citekeys: List[com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesCitekeys],
        period: Option[(com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesPeriodPart1, com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesPeriodPart2)],
        km: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesKm,
        minVei: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesMinVei,
        minMagnitude: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesMinMagnitude,
        limit: com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesLimit
    ): List[(com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesResultPart1, com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesResultPart2, com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesResultPart3, TimeSpan, List[com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesResultPart5], Option[com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesResultPart6], Option[com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesResultPart7], com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesResultPart8, com.histmersion.domain.catalogue.evidence.LeadService.Implementation.ArticleCandidatesResultPart9)] =
      db.read(LeadRepo.articleCandidates(citekeys, period, km, minVei, minMagnitude, limit))

  object Implementation:
    type AttestingSourcesId = AttestingSourcesId.T
    object AttestingSourcesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NearFindingsId = NearFindingsId.T
    object NearFindingsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NearFindingsKm = NearFindingsKm.T
    object NearFindingsKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NearFindingsYears = NearFindingsYears.T
    object NearFindingsYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NearFindingsLimit = NearFindingsLimit.T
    object NearFindingsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadsKm = LeadsKm.T
    object LeadsKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadsYears = LeadsYears.T
    object LeadsYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadsLimit = LeadsLimit.T
    object LeadsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadsOffset = LeadsOffset.T
    object LeadsOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadOfId = LeadOfId.T
    object LeadOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadOfKm = LeadOfKm.T
    object LeadOfKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadOfYears = LeadOfYears.T
    object LeadOfYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart1 = ArticleCandidatesResultPart1.T
    object ArticleCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart2 = ArticleCandidatesResultPart2.T
    object ArticleCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart3 = ArticleCandidatesResultPart3.T
    object ArticleCandidatesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart5 = ArticleCandidatesResultPart5.T
    object ArticleCandidatesResultPart5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart6 = ArticleCandidatesResultPart6.T
    object ArticleCandidatesResultPart6 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart7 = ArticleCandidatesResultPart7.T
    object ArticleCandidatesResultPart7 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart8 = ArticleCandidatesResultPart8.T
    object ArticleCandidatesResultPart8 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart9 = ArticleCandidatesResultPart9.T
    object ArticleCandidatesResultPart9 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesCitekeys = ArticleCandidatesCitekeys.T
    object ArticleCandidatesCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesPeriodPart1 = ArticleCandidatesPeriodPart1.T
    object ArticleCandidatesPeriodPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesPeriodPart2 = ArticleCandidatesPeriodPart2.T
    object ArticleCandidatesPeriodPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesKm = ArticleCandidatesKm.T
    object ArticleCandidatesKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesMinVei = ArticleCandidatesMinVei.T
    object ArticleCandidatesMinVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesMinMagnitude = ArticleCandidatesMinMagnitude.T
    object ArticleCandidatesMinMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesLimit = ArticleCandidatesLimit.T
    object ArticleCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The same rules over the shared in-memory state, which holds the catalogue's rows alone — no finding is stored, so
    * none is near an event and no article region holds one; `source` gives a source's title and year by its citekey
    * (the library's), `None` for none. Leads are listed in the order the events are, the catalogue's first.
    */
  final class InMemory(
      state: AtomicReference[CatalogueState],
      events: com.histmersion.domain.catalogue.events.EventService,
      source: String => Option[(Option[String], Option[Int])]
  ) extends LeadService:

    def attestingSources(id: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.AttestingSourcesId): List[EventSourceRef] =
      val s = state.get
      val own = s.events
        .find(_.id == id)
        .flatMap(_.citekey)
        .flatMap(ck => source(ck).map((t, y) => EventSourceRef(ck, t, y, "citekey")))
      val att = s.attestations
        .filter(a => a.targetType == "event" && a.targetId == id)
        .sortBy(a => (a.citekey, a.id))
        .distinctBy(_.citekey)
        .flatMap(a => source(a.citekey).map((t, y) => EventSourceRef(a.citekey, t, y, "attestation", a.quote)))
        .toList
      own.toList ++ att.filterNot(a => own.exists(_.citekey == a.citekey))

    def nearFindings(id: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.NearFindingsId, km: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.NearFindingsKm, years: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.NearFindingsYears, limit: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.NearFindingsLimit): List[EventFindingRef] = Nil

    private def factsOf(s: CatalogueState, id: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.FactsOfId): Option[LeadRow] =
      s.events.find(_.id == id).map { e =>
        val recs = s.catalogueRecords.filter(_._1 == id).map(_._2)
        val tier =
          if e.citekey.isDefined || s.attestations.exists(a => a.targetType == "event" && a.targetId == id) then
            EventTier.Attested
          else EventTier.Catalogue
        val cs = s.candidates.filter(_.eventId == com.histmersion.api.catalogue.Event.Id(id))
        LeadRow(
          e.id,
          e.label,
          e.kind,
          e.timeSpan.notBefore,
          e.timeSpan.beginBy,
          e.timeSpan.endFrom,
          e.timeSpan.notAfter,
          tier,
          recs.flatMap(_.vei).maxOption,
          recs.flatMap(_.magnitude).maxOption,
          0,
          cs.count(_.status == com.histmersion.domain.catalogue.Event.LinkStatus.Proposed),
          cs.count(_.status == com.histmersion.domain.catalogue.Event.LinkStatus.Confirmed)
        )
      }

    def leads(selection: EventSelection, km: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.LeadsKm, years: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.LeadsYears, limit: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.LeadsLimit, offset: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.LeadsOffset): (com.histmersion.RowCount, List[LeadRow]) =
      val s = state.get
      val listed = events.list(selection, Int.MaxValue, 0)
      val rows = listed.events.flatMap(e => factsOf(s, e.id)).sortBy { r =>
        val size = r.vei.fold(0.0)(_.toDouble) + r.magnitude.getOrElse(0.0)
        (r.tier != EventTier.Catalogue, -size, r.notBefore.orElse(r.beginBy).fold((1, 0L))(y => (0, y)), r.id)
      }
      (com.histmersion.RowCount(listed.total.value), rows.slice(offset.max(0), offset.max(0) + limit.max(1)))

    def leadOf(id: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.LeadOfId, km: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.LeadOfKm, years: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.LeadOfYears): List[LeadRow] = factsOf(state.get, id).toList

    def articleCandidates(
        citekeys: List[com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesCitekeys],
        period: Option[(com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesPeriodPart1, com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesPeriodPart2)],
        km: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesKm,
        minVei: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesMinVei,
        minMagnitude: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesMinMagnitude,
        limit: com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesLimit
    ): List[(com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesResultPart1, com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesResultPart2, com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesResultPart3, TimeSpan, List[com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesResultPart5], Option[com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesResultPart6], Option[com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesResultPart7], com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesResultPart8, com.histmersion.domain.catalogue.evidence.LeadService.InMemory.ArticleCandidatesResultPart9)] = Nil

  object InMemory:
    type AttestingSourcesId = AttestingSourcesId.T
    object AttestingSourcesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NearFindingsId = NearFindingsId.T
    object NearFindingsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NearFindingsKm = NearFindingsKm.T
    object NearFindingsKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NearFindingsYears = NearFindingsYears.T
    object NearFindingsYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NearFindingsLimit = NearFindingsLimit.T
    object NearFindingsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FactsOfId = FactsOfId.T
    object FactsOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadsKm = LeadsKm.T
    object LeadsKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadsYears = LeadsYears.T
    object LeadsYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadsLimit = LeadsLimit.T
    object LeadsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadsOffset = LeadsOffset.T
    object LeadsOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadOfId = LeadOfId.T
    object LeadOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadOfKm = LeadOfKm.T
    object LeadOfKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeadOfYears = LeadOfYears.T
    object LeadOfYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart1 = ArticleCandidatesResultPart1.T
    object ArticleCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart2 = ArticleCandidatesResultPart2.T
    object ArticleCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart3 = ArticleCandidatesResultPart3.T
    object ArticleCandidatesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart5 = ArticleCandidatesResultPart5.T
    object ArticleCandidatesResultPart5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart6 = ArticleCandidatesResultPart6.T
    object ArticleCandidatesResultPart6 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart7 = ArticleCandidatesResultPart7.T
    object ArticleCandidatesResultPart7 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart8 = ArticleCandidatesResultPart8.T
    object ArticleCandidatesResultPart8 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesResultPart9 = ArticleCandidatesResultPart9.T
    object ArticleCandidatesResultPart9 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesCitekeys = ArticleCandidatesCitekeys.T
    object ArticleCandidatesCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesPeriodPart1 = ArticleCandidatesPeriodPart1.T
    object ArticleCandidatesPeriodPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesPeriodPart2 = ArticleCandidatesPeriodPart2.T
    object ArticleCandidatesPeriodPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesKm = ArticleCandidatesKm.T
    object ArticleCandidatesKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesMinVei = ArticleCandidatesMinVei.T
    object ArticleCandidatesMinVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesMinMagnitude = ArticleCandidatesMinMagnitude.T
    object ArticleCandidatesMinMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArticleCandidatesLimit = ArticleCandidatesLimit.T
    object ArticleCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
