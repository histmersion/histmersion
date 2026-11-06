package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.catalogue.EventEntry

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{Api, BBox, TimeSpan, Write}
import com.histmersion.api.catalogue.{
  ArticleEventLead,
  Attestation,
  CatalogProviderInfo,
  EventCandidatesRequest,
  EventLead,
  EventLeads,
  EventLinkCandidate,
  EventLinkList,
  EventLinksRequest,
  EventLinksResult,
  EventPassage,
  EventTaxonomy,
  EventTier,
  LeadFacts,
  Period
}
import com.histmersion.api.library.Filter
import com.histmersion.api.jobs.JobInfo
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueJobs
import com.histmersion.domain.catalogue.events.{weights, EventSelection, EventService, EventSql}
import com.histmersion.util.data.maintenance.WriteGate
import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.evidence.passages.Passage

/** The links of events to the corpus as REST, MCP and the CLI reach them: the sources proposed as discussing an event
  * (computed on the first ask, stored after) and their confirmation — an attestation from a passage of the source's
  * text that names the event, read now — or rejection; the candidates of many events as a job; the leads catalogue
  * events raise; the context of an event's card; an article's catalogue leads. `info` gives a catalogue's terms,
  * `resolve` a list's filter its page size, `scope` the gazetteer's reading of its places.
  */
final class EventLinksControllers(
    links: EventLinkService,
    leadFacts: LeadService,
    events: EventService,
    attestations: AttestationService,
    sources: Passage.Source,
    jobs: CatalogueJobs,
    info: EventLinksControllers.InfoArg1 => Option[CatalogProviderInfo],
    resolve: Filter => Filter,
    scope: Filter => PlaceScope,
    settings: () => AppConfig,
    maintenance: WriteGate
):
  private def ec = settings().events

  private def notFound(m: com.histmersion.domain.catalogue.evidence.EventLinksControllers.NotFoundM) = Api.Error(m, Some(Write.CodeNotFound))

  private def eventOrFail(id: com.histmersion.domain.catalogue.evidence.EventLinksControllers.EventOrFailId): Either[Api.Error, EventEntry] =
    events.get(events.renamedTo(id).getOrElse(id)).toRight(notFound(s"No event '$id'"))

  /** The names of an event worth searching for. */
  private def namesOf(d: EventEntry): List[com.histmersion.domain.catalogue.evidence.EventLinksControllers.NamesOfResult] =
    val stored = events.tierAndNames(d.event.id).map(_._2).getOrElse(Nil)
    EventMatcher.searchNames(d.event.label :: stored, ec.links.minNameChars)

  /** The passages of `citekey`'s body that name the event — a whole-word, case- and accent-insensitive match of each
    * name.
    */
  private def passagesIn(citekey: com.histmersion.domain.library.Citekey, names: List[com.histmersion.domain.catalogue.evidence.EventLinksControllers.PassagesInNames]): List[EventPassage] =
    val text = sources.reader(citekey)
    names
      .flatMap { n =>
        val words = n.split("\\s+").filter(_.nonEmpty).map(w => java.util.regex.Pattern.quote(w))
        val rx = words.mkString("\\b", "\\s+", "\\b")
        text
          .find(
            rx,
            regex = true,
            limit = 5,
            context = 160,
            maxMatches = 50,
            settings().limits.passages.regexTimeoutMs.toLong
          )
          .toOption
          .toList
          .flatten
          .flatMap { h =>
            h.matches
              .map(m => EventPassage(h.offset + m.start, h.offset + m.end, h.text.substring(m.start, m.end), n, h.text))
          }
      }
      .distinctBy(_.start)
      .sortBy(_.start)

  /** Compute and store an event's candidates. */
  def compute(d: EventEntry): EventLinkList =
    val w = ec.links.weights
    val names = namesOf(d)
    val near = leadFacts
      .nearFindings(d.event.id, w.km, w.years, 500)
      .map(f => EventMatcher.NearFinding(f.id, f.citekey, f.km, f.yearGap))
    val named = names
      .flatMap(n => sources.naming(n, ec.links.sourcesPerName))
      .distinct
      .map(ck => ck -> passagesIn(ck, names))
      .filter(_._2.nonEmpty)
      .toMap
    val cands = EventMatcher.candidates(d.event.id, near, named, w)
    links.saveCandidates(d.event.id, cands)
    EventLinkList(d.event.id, d.event.label, links.candidates(d.event.id))

  /** An event's candidates — computed on the first ask, stored after. */
  def links(id: com.histmersion.domain.catalogue.evidence.EventLinksControllers.LinksId): Either[Api.Error, EventLinkList] =
    eventOrFail(id).map { d =>
      if links.isChecked(d.event.id) then EventLinkList(d.event.id, d.event.label, links.candidates(d.event.id))
      else compute(d)
    }

  /** Recompute, confirm or reject. A source is confirmed only from a passage of its body that names the event, read NOW
    * (not the stored evidence): the attestation carries that passage's quote and offsets. A source with none is
    * refused, with why.
    */
  def applyLinks(caller: Caller, id: com.histmersion.domain.catalogue.evidence.EventLinksControllers.ApplyLinksId, req: EventLinksRequest): Either[Api.Error, EventLinksResult] =
    maintenance.guard("event_links") {
      eventOrFail(id).map { d =>
        val eid = d.event.id
        if req.recompute then compute(d)
        val names = namesOf(d)
        val existing = attestations.list(None, Some(AttestationTarget.Event), Some(eid), 1000, 0).attestations
        @tailrec def confirm(
            rest: List[com.histmersion.domain.catalogue.evidence.EventLinksControllers.ApplyLinksConfirmRest],
            ok: List[Attestation],
            no: List[com.histmersion.domain.catalogue.evidence.EventLinksControllers.ApplyLinksConfirmNo]
        ): (List[Attestation], List[com.histmersion.domain.catalogue.evidence.EventLinksControllers.ApplyLinksConfirmResultPart2]) =
          rest match
            case Nil         => (ok, no)
            case raw :: tail =>
              sources.canonical(raw) match
                case None     => confirm(tail, ok, no :+ s"$raw: no such source")
                case Some(ck) =>
                  if existing.exists(_.citekey == ck) then
                    val _ = links.setStatus(eid, ck, Event.LinkStatus.Confirmed)
                    confirm(tail, ok, no :+ s"$ck: already attests it")
                  else
                    EventMatcher.attestationPassage(passagesIn(ck, names)) match
                      case None =>
                        confirm(
                          tail,
                          ok,
                          no :+ s"$ck: no passage of its text names the event (${names.mkString(", ")}) — not attested"
                        )
                      case Some(p) =>
                        val a = attestations.create(EventMatcher.attestation(eid, ck, p))
                        if !links.setStatus(eid, ck, Event.LinkStatus.Confirmed) then
                          links.saveCandidates(
                            eid,
                            links.candidates(eid).filter(_.status == Event.LinkStatus.Proposed) :+
                              EventLinkCandidate(
                                com.histmersion.api.catalogue.Event.Id(eid),
                                com.histmersion.api.library.Citekey(ck),
                                0,
                                Event.LinkStatus.Proposed,
                                None,
                                Nil,
                                List(p),
                                List("confirmed by hand")
                              )
                          )
                          val _ = links.setStatus(eid, ck, Event.LinkStatus.Confirmed)
                        confirm(tail, ok :+ a, no)
        val (attested, refused) = confirm(req.confirm.map(_.trim).filter(_.nonEmpty).distinct, Nil, Nil)
        val rejected = req.reject
          .map(_.trim)
          .filter(_.nonEmpty)
          .distinct
          .filter(ck => links.setStatus(eid, sources.canonical(ck).getOrElse(ck), Event.LinkStatus.Rejected))
        EventLinksResult(
          EventLinkList(eid, d.event.label, links.candidates(eid)),
          attested,
          refused,
          rejected
        )
      }
    }

  /** The candidates of many events, as a job: those never computed (or all), `links.batch` at a time. */
  def candidatesJob(caller: Caller, req: EventCandidatesRequest): Either[Api.Error, JobInfo] =
    req.event.map(_.trim).filter(_.nonEmpty) match
      case Some(one) =>
        eventOrFail(one).flatMap(_ =>
          jobs.start(EventLinksControllers.CandidatesJobKind, s"candidates of $one", write = true) { (_, _) =>
            eventOrFail(one)
              .map(d => s"${compute(d).candidates.size} candidate(s) for ${d.event.id}")
              .left
              .map(_.message)
          }
        )
      case None =>
        jobs.start(
          EventLinksControllers.CandidatesJobKind,
          s"candidate links of ${if req.all then "every event" else "the events not checked yet"}",
          write = true
        ) { (_, progress) =>
          val ids = links.eventsToCheck(req.all, req.limit.filter(_ > 0).getOrElse(Int.MaxValue))
          // `var`s: the counts of the walk
          var n, found = 0
          ids.grouped(ec.links.batch.max(1)).takeWhile(_ => !jobs.held).foreach { batch =>
            batch.foreach { id =>
              events.get(id).foreach(d => found += compute(d).candidates.size)
              n += 1
            }
            progress(s"$n of ${ids.size} event(s) checked · $found candidate(s)")
          }
          Right(s"$n event(s) checked · $found candidate(s)")
        }

  private def leadOf(r: LeadRow): Option[EventLead] =
    EventLeads.of(
      LeadFacts(
        r.id,
        r.label,
        r.kind,
        TimeSpan(r.notBefore, r.beginBy, r.endFrom, r.notAfter),
        r.tier,
        r.vei,
        r.magnitude,
        r.findingsNear,
        ec.leads.km,
        ec.leads.years,
        r.proposed,
        r.attesting
      )
    )

  /** The leads of the events `q` selects — catalogue ones by default, with the catalogue's context. */
  def leads(q: Api.EventQuery): EventLead.List =
    val f = resolve(q.filter)
    val selection = EventSelection(
      f,
      scope(f),
      q.text,
      q.kind,
      q.historicity,
      q.placeId,
      EventSql.Narrow(
        q.tier.orElse(Some(EventTier.Catalogue)),
        context = q.context.getOrElse(true),
        q.area.flatMap(BBox.parse)
      )
    )
    val (total, rows) =
      leadFacts.leads(selection, ec.leads.km, ec.leads.years, settings().limits.page(Some(f.limit), 50), f.offset)
    EventLead.List(total.value, rows.flatMap(leadOf))

  // ── an event's card ─────────────────────────────────────────────────────────────────────────

  /** The context an event's card shows: its tier and names, the catalogues that list it, the sources attesting it, the
    * findings near it, the proposed sources (stored — a card never computes them) and its lead.
    */
  def context(d: EventEntry): EventEntry =
    val id = d.event.id
    val (tier, names, _) = events.tierAndNames(id).getOrElse((d.event.tier, Nil, None))
    val lead = leadFacts.leadOf(id, ec.leads.km, ec.leads.years).flatMap(leadOf)
    d.modify(_.tier)
      .setTo(tier)
      .modify(_.names)
      .setTo(names.filterNot(_ == d.event.label))
      .modify(_.catalogue)
      .setTo(
        events
          .catalogueRefs(id)
          .map(r =>
            // a plugin's catalogue: its terms come from the plugin, which the store does not know
            if r.licence.nonEmpty then r
            else
              info(r.provider).fold(r)(i =>
                r.modify(_.title).setTo(i.title).modify(_.licence).setTo(i.licence).modify(_.citation).setTo(i.citation)
              )
          )
      )
      .modify(_.sources)
      .setTo(leadFacts.attestingSources(id))
      .modify(_.findings)
      .setTo(leadFacts.nearFindings(id, ec.links.km, ec.links.years, ec.links.nearFindings))
      .modify(_.candidates)
      .setTo(links.candidates(id).filter(_.status == Event.LinkStatus.Proposed))
      .modify(_.leads)
      .setTo(lead)

  /** The catalogue events an article's period and region hold that its body never names. */
  def articleLeads(body: com.histmersion.domain.catalogue.evidence.EventLinksControllers.ArticleLeadsBody, citekeys: List[com.histmersion.domain.catalogue.evidence.EventLinksControllers.ArticleLeadsCitekeys], period: Option[Period]): List[ArticleEventLead] =
    leadFacts
      .articleCandidates(
        citekeys,
        period.map(p => (p.start, p.end)),
        ec.leads.articleKm,
        ec.leads.largeVei,
        ec.leads.largeMagnitude,
        ec.leads.articleLimit * 3
      )
      .filterNot((_, label, _, _, names, _, _, _, _) => ArticleEventLead.mentioned(body, label :: names))
      .take(ec.leads.articleLimit)
      .map((id, label, kind, ts, _, vei, mag, n, prov) =>
        ArticleEventLead(
          id,
          label,
          EventTaxonomy.canonical(kind),
          EventLeads.years(ts),
          EventLeads.size(vei, mag),
          n,
          info(prov).fold(prov)(_.title)
        )
      )

object EventLinksControllers:
  type ApplyLinksConfirmResultPart2 = ApplyLinksConfirmResultPart2.T
  object ApplyLinksConfirmResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLinksConfirmRest = ApplyLinksConfirmRest.T
  object ApplyLinksConfirmRest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLinksConfirmNo = ApplyLinksConfirmNo.T
  object ApplyLinksConfirmNo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type InfoArg1 = InfoArg1.T
  object InfoArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type NotFoundM = NotFoundM.T
  object NotFoundM extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventOrFailId = EventOrFailId.T
  object EventOrFailId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesOfResult = NamesOfResult.T
  object NamesOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PassagesInNames = PassagesInNames.T
  object PassagesInNames extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksId = LinksId.T
  object LinksId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLinksId = ApplyLinksId.T
  object ApplyLinksId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLeadsBody = ArticleLeadsBody.T
  object ArticleLeadsBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLeadsCitekeys = ArticleLeadsCitekeys.T
  object ArticleLeadsCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesJobKind = CandidatesJobKindValue
  type CandidatesJobKindValue = CandidatesJobKindValue.T
  object CandidatesJobKindValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val CandidatesJobKind: com.histmersion.domain.catalogue.evidence.EventLinksControllers.CandidatesJobKind = com.histmersion.api.catalogue.EvidenceJobKinds.EventsCandidates.kind.value
