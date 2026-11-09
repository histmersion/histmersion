package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{
  ArticleEventLead,
  CatalogRecord,
  EventEntry,
  EventFindingRef,
  EventSourceRef,
  EventTier
}
import com.histmersion.domain.DeepTime.SignedYear
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.events.CatalogMerge as CM
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.events.{EventSelection, EventSourceFilter}
import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.catalogue.events.EventService
import com.histmersion.api.catalogue.Year

/** What an event's card and the leads are computed from: the sources attesting an event, the dated findings near it,
  * the facts of each event's lead (its size, the findings around it, the sources proposed and confirmed), and the
  * catalogue events an article's region and period hold.
  */
trait LeadService:
  /** The sources attesting one event: its own, then those of the attestations naming it. */
  def attestingSources(id: Event.Id): List[EventSourceRef]

  /** The dated, placed findings within `km` and `years` of an event, nearest in time then space first. */
  def nearFindings(id: Event.Id, km: CM.Tolerance.Km, years: CM.Tolerance.Years, limit: PageSize): List[EventFindingRef]

  /** The facts of the leads of the events `selection` selects, the catalogue's and the largest first, one page; and how
    * many it selects.
    */
  def leads(
      selection: EventSelection,
      km: CM.Tolerance.Km,
      years: CM.Tolerance.Years,
      limit: PageSize,
      offset: Offset
  ): (RowCount, List[LeadRow])

  /** The facts of one event's lead. */
  def leadOf(id: Event.Id, km: CM.Tolerance.Km, years: CM.Tolerance.Years): List[LeadRow]

  /** Catalogue events overlapping `period` within `km` of a dated finding of `citekeys` of that period, large enough
    * (`minVei`, `minMagnitude`) or a war, with how many of those findings are near each.
    */
  def articleCandidates(
      citekeys: List[Citekey],
      period: Option[(SignedYear, SignedYear)],
      km: CM.Tolerance.Km,
      minVei: CatalogRecord.Vei,
      minMagnitude: CatalogRecord.Magnitude,
      limit: PageSize
  ): List[LeadService.ArticleCandidate]

object LeadService:
  /** A catalogue event an article's region and period hold: its id, label, kind, span, names, size, the findings near
    * it and the catalogue it comes from.
    */
  type ArticleCandidate = (
      Event.Id,
      CatalogRecord.Label,
      CatalogRecord.Kind,
      TimeSpan,
      List[EventEntry.Names],
      Option[CatalogRecord.Vei],
      Option[CatalogRecord.Magnitude],
      ArticleEventLead.FindingsNear,
      CatalogRecord.Provider
  )

  def apply(db: Transactor, related: EventSourceFilter): LeadService = Implementation(db, related)

  final private class Implementation(db: Transactor, related: EventSourceFilter) extends LeadService:
    def attestingSources(id: Event.Id): List[EventSourceRef] = db.read(LeadRepo.attestingSources(id))
    def nearFindings(
        id: Event.Id,
        km: CM.Tolerance.Km,
        years: CM.Tolerance.Years,
        limit: PageSize
    ): List[EventFindingRef] =
      db.read(LeadRepo.nearFindings(id, km, years, limit))
    def leads(
        selection: EventSelection,
        km: CM.Tolerance.Km,
        years: CM.Tolerance.Years,
        limit: PageSize,
        offset: Offset
    ): (RowCount, List[LeadRow]) =
      db.read(LeadRepo.leadRows(selection.sql(related), km, years, limit, offset))
    def leadOf(id: Event.Id, km: CM.Tolerance.Km, years: CM.Tolerance.Years): List[LeadRow] =
      db.read(
        LeadRepo
          .leadRows(Sql.of(Sql.Text("e.id = ?"), Param.S(Param.S.V(id.value))), km, years, PageSize(1), Offset.Start)
      )._2
    def articleCandidates(
        citekeys: List[Citekey],
        period: Option[(SignedYear, SignedYear)],
        km: CM.Tolerance.Km,
        minVei: CatalogRecord.Vei,
        minMagnitude: CatalogRecord.Magnitude,
        limit: PageSize
    ): List[ArticleCandidate] =
      db.read(LeadRepo.articleCandidates(citekeys, period, km, minVei, minMagnitude, limit))

  /** The same rules over the shared in-memory state, which holds the catalogue's rows alone — no finding is stored, so
    * none is near an event and no article region holds one; `source` gives a source's title and year by its citekey
    * (the library's), `None` for none. Leads are listed in the order the events are, the catalogue's first.
    */
  final class InMemory(
      state: AtomicReference[CatalogueState],
      events: EventService,
      source: Citekey => Option[(Option[EventSourceRef.Title], Option[Year])]
  ) extends LeadService:

    def attestingSources(id: Event.Id): List[EventSourceRef] =
      val s = state.get
      val own = s.events
        .find(_.id == id)
        .flatMap(_.citekey)
        .flatMap(ck =>
          source(ck).map((t, y) =>
            EventSourceRef(com.histmersion.api.library.Citekey(ck.value), t, y, EventSourceRef.Via("citekey"))
          )
        )
      val att = s.attestations
        .filter(a => a.targetType.value == "event" && a.targetId.value == id.value)
        .sortBy(a => (a.citekey.value: String, a.id.value: Long))
        .distinctBy(_.citekey)
        .flatMap(a =>
          Citekey
            .option(a.citekey.value)
            .flatMap(source)
            .map((t, y) =>
              EventSourceRef(
                a.citekey,
                t,
                y,
                EventSourceRef.Via("attestation"),
                a.quote.map(q => EventSourceRef.Quote(q.value))
              )
            )
        )
        .toList
      own.toList ++ att.filterNot(a => own.exists(_.citekey == a.citekey))

    def nearFindings(
        id: Event.Id,
        km: CM.Tolerance.Km,
        years: CM.Tolerance.Years,
        limit: PageSize
    ): List[EventFindingRef] = Nil

    private def factsOf(s: CatalogueState, id: Event.Id): Option[LeadRow] =
      s.events.find(_.id == id).map { e =>
        val recs = s.catalogueRecords.filter(_._1 == id).map(_._2)
        val tier =
          if e.citekey.isDefined || s.attestations
              .exists(a => a.targetType.value == "event" && a.targetId.value == id.value)
          then EventTier.Attested
          else EventTier.Catalogue
        val cs = s.candidates.filter(_.eventId.value == id.value)
        LeadRow(
          LeadRow.Id(e.id.value),
          LeadRow.Label(e.label.value),
          LeadRow.Kind(e.kind.value),
          e.timeSpan.notBefore.map(y => LeadRow.NotBefore(y.value)),
          e.timeSpan.beginBy.map(y => LeadRow.BeginBy(y.value)),
          e.timeSpan.endFrom.map(y => LeadRow.EndFrom(y.value)),
          e.timeSpan.notAfter.map(y => LeadRow.NotAfter(y.value)),
          LeadRow.Tier(tier),
          recs.flatMap(_.vei.map(_.value)).maxOption.map(LeadRow.Vei(_)),
          recs.flatMap(_.magnitude.map(_.value)).maxOption.map(LeadRow.Magnitude(_)),
          LeadRow.FindingsNear(0),
          LeadRow.Proposed(cs.count(_.status.value == Event.LinkStatus.Proposed)),
          LeadRow.Attesting(cs.count(_.status.value == Event.LinkStatus.Confirmed))
        )
      }

    def leads(
        selection: EventSelection,
        km: CM.Tolerance.Km,
        years: CM.Tolerance.Years,
        limit: PageSize,
        offset: Offset
    ): (RowCount, List[LeadRow]) =
      val s = state.get
      val listed = events.list(selection, PageSize(Int.MaxValue), Offset.Start)
      val rows = listed.events.flatMap(e => Event.Id.option(e.id.value).flatMap(factsOf(s, _))).sortBy { r =>
        val size = r.vei.fold(0.0)(_.value.toDouble) + r.magnitude.fold(0.0)(_.value)
        (
          r.tier.value != EventTier.Catalogue,
          -size,
          r.notBefore.map(_.value: Long).orElse(r.beginBy.map(_.value: Long)).fold((1, 0L))(y => (0, y)),
          r.id.value: String
        )
      }
      (
        RowCount(listed.total.value),
        rows.slice(offset.value.max(0), offset.value.max(0) + limit.value.max(1))
      )

    def leadOf(id: Event.Id, km: CM.Tolerance.Km, years: CM.Tolerance.Years): List[LeadRow] =
      factsOf(state.get, id).toList

    def articleCandidates(
        citekeys: List[Citekey],
        period: Option[(SignedYear, SignedYear)],
        km: CM.Tolerance.Km,
        minVei: CatalogRecord.Vei,
        minMagnitude: CatalogRecord.Magnitude,
        limit: PageSize
    ): List[ArticleCandidate] = Nil
