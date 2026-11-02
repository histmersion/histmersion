package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.{EventFindingRef, EventSourceRef, EventTier, TimeSpan}
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
  def attestingSources(id: String): List[EventSourceRef]

  /** The dated, placed findings within `km` and `years` of an event, nearest in time then space first. */
  def nearFindings(id: String, km: Double, years: Long, limit: Int): List[EventFindingRef]

  /** The facts of the leads of the events `selection` selects, the catalogue's and the largest first, one page; and how
    * many it selects.
    */
  def leads(selection: EventSelection, km: Double, years: Long, limit: Int, offset: Int): (Int, List[LeadRow])

  /** The facts of one event's lead. */
  def leadOf(id: String, km: Double, years: Long): List[LeadRow]

  /** Catalogue events overlapping `period` within `km` of a dated finding of `citekeys` of that period, large enough
    * (`minVei`, `minMagnitude`) or a war, with how many of those findings are near each.
    */
  def articleCandidates(
      citekeys: List[String],
      period: Option[(Long, Long)],
      km: Double,
      minVei: Int,
      minMagnitude: Double,
      limit: Int
  ): List[(String, String, String, TimeSpan, List[String], Option[Int], Option[Double], Int, String)]

object LeadService:
  def apply(db: Transactor, related: EventSourceFilter): LeadService = Implementation(db, related)

  final private class Implementation(db: Transactor, related: EventSourceFilter) extends LeadService:
    def attestingSources(id: String): List[EventSourceRef] = db.read(LeadRepo.attestingSources(id))
    def nearFindings(id: String, km: Double, years: Long, limit: Int): List[EventFindingRef] =
      db.read(LeadRepo.nearFindings(id, km, years, limit))
    def leads(selection: EventSelection, km: Double, years: Long, limit: Int, offset: Int): (Int, List[LeadRow]) =
      db.read(LeadRepo.leadRows(selection.sql(related), km, years, limit, offset))
    def leadOf(id: String, km: Double, years: Long): List[LeadRow] =
      db.read(LeadRepo.leadRows(Sql.of("e.id = ?", Param.S(id)), km, years, 1, 0))._2
    def articleCandidates(
        citekeys: List[String],
        period: Option[(Long, Long)],
        km: Double,
        minVei: Int,
        minMagnitude: Double,
        limit: Int
    ): List[(String, String, String, TimeSpan, List[String], Option[Int], Option[Double], Int, String)] =
      db.read(LeadRepo.articleCandidates(citekeys, period, km, minVei, minMagnitude, limit))

  /** The same rules over the shared in-memory state, which holds the catalogue's rows alone — no finding is stored, so
    * none is near an event and no article region holds one; `source` gives a source's title and year by its citekey
    * (the library's), `None` for none. Leads are listed in the order the events are, the catalogue's first.
    */
  final class InMemory(
      state: AtomicReference[CatalogueState],
      events: com.histmersion.domain.catalogue.events.EventService,
      source: String => Option[(Option[String], Option[Int])]
  ) extends LeadService:

    def attestingSources(id: String): List[EventSourceRef] =
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

    def nearFindings(id: String, km: Double, years: Long, limit: Int): List[EventFindingRef] = Nil

    private def factsOf(s: CatalogueState, id: String): Option[LeadRow] =
      s.events.find(_.id == id).map { e =>
        val recs = s.catalogueRecords.filter(_._1 == id).map(_._2)
        val tier =
          if e.citekey.isDefined || s.attestations.exists(a => a.targetType == "event" && a.targetId == id) then
            EventTier.Attested
          else EventTier.Catalogue
        val cs = s.candidates.filter(_.eventId == id)
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
          cs.count(_.status == com.histmersion.values.EventLinkStatus.Proposed),
          cs.count(_.status == com.histmersion.values.EventLinkStatus.Confirmed)
        )
      }

    def leads(selection: EventSelection, km: Double, years: Long, limit: Int, offset: Int): (Int, List[LeadRow]) =
      val s = state.get
      val listed = events.list(selection, Int.MaxValue, 0)
      val rows = listed.events.flatMap(e => factsOf(s, e.id)).sortBy { r =>
        val size = r.vei.fold(0.0)(_.toDouble) + r.magnitude.getOrElse(0.0)
        (r.tier != EventTier.Catalogue, -size, r.notBefore.orElse(r.beginBy).fold((1, 0L))(y => (0, y)), r.id)
      }
      (listed.total, rows.slice(offset.max(0), offset.max(0) + limit.max(1)))

    def leadOf(id: String, km: Double, years: Long): List[LeadRow] = factsOf(state.get, id).toList

    def articleCandidates(
        citekeys: List[String],
        period: Option[(Long, Long)],
        km: Double,
        minVei: Int,
        minMagnitude: Double,
        limit: Int
    ): List[(String, String, String, TimeSpan, List[String], Option[Int], Option[Double], Int, String)] = Nil
