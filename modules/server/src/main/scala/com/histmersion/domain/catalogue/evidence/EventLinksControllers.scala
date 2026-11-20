package com.histmersion
package domain.catalogue.evidence

import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.domain.catalogue.Place

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{Api, BBox, TimeSpan, Write}
import com.histmersion.api.catalogue.{
  ArticleEventLead,
  Attestation,
  CatalogProviderInfo,
  CatalogRecord,
  EventCandidatesRequest,
  EventCatalogueRef,
  EventGeo,
  EventLead,
  EventLeads,
  EventLinkCandidate,
  EventLinkList,
  EventLinksRequest,
  EventLinksResult,
  EventPassage,
  EventSummary,
  EventTaxonomy,
  EventTier,
  LeadFacts,
  Period,
  Year
}
import com.histmersion.api.library.Filter
import com.histmersion.api.jobs.JobInfo
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueJobs
import com.histmersion.domain.catalogue.events.{weights, CatalogMerge, EventSelection, EventService, EventSql}
import com.histmersion.domain.DeepTime.SignedYear
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title
import com.histmersion.{Offset, PageSize}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.database.WritePermit
import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.evidence.passages.Passage
import com.histmersion.domain.catalogue.evidence.passages.PassageReader
import com.histmersion.api.jobs.JobKinds
import com.histmersion.util.jobs.BackgroundJob
import com.histmersion.util.jobs.Job
import com.histmersion.api.catalogue.EvidenceJobKinds
import com.histmersion.util.database.Unused

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
    info: CatalogRecord.Provider => Option[CatalogProviderInfo],
    resolve: Filter => Filter,
    scope: Filter => PlaceScope,
    settings: () => AppConfig,
    maintenance: WriteGate
):
  private def ec = settings().events

  private def notFound(m: Problem) = Api.Error(m, Some(Api.Error.Code(Write.CodeNotFound)))

  /** The id of an event read from the store. */
  private def idOf(d: EventEntry): Event.Id = Event.Id.assume(d.event.id.value)

  private def eventOrFail(id: Event.Id): Either[Api.Error, EventEntry] =
    events
      .get(events.renamedTo(id).getOrElse(id))
      .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$id"))))

  /** The names of an event worth searching for. */
  private def namesOf(d: EventEntry): List[String] =
    val stored = events.tierAndNames(idOf(d)).map(_._2).getOrElse(Nil)
    EventMatcher.searchNames(
      d.event.label.value :: stored.map(_.value),
      EventMatcher.Weights.MinNameChars(ec.links.minNameChars.value)
    )

  /** The passages of `citekey`'s body that name the event — a whole-word, case- and accent-insensitive match of each
    * name.
    */
  private def passagesIn(citekey: Citekey, names: List[String]): List[EventPassage] =
    val text = sources.reader(citekey)
    names
      .flatMap { n =>
        val words = n.split("\\s+").filter(_.nonEmpty).map(w => java.util.regex.Pattern.quote(w))
        val rx = words.mkString("\\b", "\\s+", "\\b")
        text
          .find(
            PassageReader.Query(rx),
            regex = PassageReader.Regex(true),
            limit = PageSize(5),
            context = PassageReader.Context(160),
            maxMatches = PassageReader.MaxMatches(50),
            PassageReader.TimeoutMs(settings().limits.passages.regexTimeoutMs.value.toLong)
          )
          .toOption
          .toList
          .flatten
          .flatMap { h =>
            h.matches
              .map(m =>
                EventPassage(
                  EventPassage.Start(h.offset.value + m.start.value),
                  EventPassage.End(h.offset.value + m.end.value),
                  EventPassage.Quote(h.text.value.substring(m.start.value, m.end.value)),
                  EventPassage.Name(n),
                  EventPassage.Context(h.text.value)
                )
              )
          }
      }
      .distinctBy(_.start.value)
      .sortBy(_.start.value: Int)

  private def listOf(d: EventEntry): EventLinkList =
    EventLinkList(d.event.id, EventLinkList.Label(d.event.label.value), links.candidates(idOf(d)))

  /** Compute and store an event's candidates. */
  def compute(d: EventEntry)(using WritePermit): EventLinkList =
    val w = ec.links.weights
    val names = namesOf(d)
    val id = idOf(d)
    val near = leadFacts
      .nearFindings(
        id,
        CatalogMerge.Tolerance.Km(w.km.value),
        CatalogMerge.Tolerance.Years(w.years.value),
        PageSize(500)
      )
      .map(f =>
        EventMatcher.NearFinding(
          EventMatcher.NearFinding.Id(f.id.value),
          Citekey.assume(f.citekey.value),
          EventMatcher.NearFinding.Km(f.km.value),
          EventMatcher.NearFinding.YearGap(f.yearGap.value)
        )
      )
    val named = PageSize
      .option(ec.links.sourcesPerName.value)
      .toList
      .flatMap(limit => names.flatMap(n => sources.naming(Title(n), limit)))
      .distinct
      .map(ck => ck -> passagesIn(ck, names))
      .filter(_._2.nonEmpty)
      .toMap
    val cands = EventMatcher.candidates(id, near, named, w)
    links.saveCandidates(id, cands)
    listOf(d)

  /** An event's candidates — computed on the first ask, stored after (that first ask writes, under the maintenance
    * gate).
    */
  def links(id: Event.Id): Either[Api.Error, EventLinkList] =
    eventOrFail(id).flatMap { d =>
      if links.isChecked(idOf(d)) then Right(listOf(d))
      else maintenance.guard(WriteGate.OperationName("event_links"))(Right(compute(d)))
    }

  /** Recompute, confirm or reject. A source is confirmed only from a passage of its body that names the event, read NOW
    * (not the stored evidence): the attestation carries that passage's quote and offsets. A source with none is
    * refused, with why.
    */
  def applyLinks(caller: Caller, id: Event.Id, req: EventLinksRequest): Either[Api.Error, EventLinksResult] =
    maintenance.guard(WriteGate.OperationName("event_links")) {
      eventOrFail(id).map { d =>
        val eid = idOf(d)
        if req.recompute.value then compute(d)
        val names = namesOf(d)
        val existing = attestations
          .list(
            None,
            Some(Attestation.TargetType(AttestationTarget.Event)),
            Some(Attestation.TargetId(eid.value)),
            PageSize(1000),
            Offset.Start
          )
          .attestations
        def status(s: String) = Event.LinkStatus.Wire(s)
        @tailrec def confirm(
            rest: List[String],
            ok: List[Attestation],
            no: List[String]
        ): (List[Attestation], List[String]) =
          rest match
            case Nil         => (ok, no)
            case raw :: tail =>
              Citekey.option(raw).flatMap(sources.canonical) match
                case None     => confirm(tail, ok, no :+ s"$raw: no such source")
                case Some(ck) =>
                  if existing.exists(_.citekey.value == ck.value) then
                    Unused(links.setStatus(eid, ck, status(Event.LinkStatus.Confirmed)), Unused.Why.Idempotent)
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
                        if !links.setStatus(eid, ck, status(Event.LinkStatus.Confirmed)) then
                          links.saveCandidates(
                            eid,
                            links.candidates(eid).filter(_.status.value == Event.LinkStatus.Proposed) :+
                              EventLinkCandidate(
                                com.histmersion.api.catalogue.Event.Id(eid.value),
                                com.histmersion.api.library.Citekey(ck.value),
                                EventLinkCandidate.Score(0),
                                EventLinkCandidate.StatusValue(Event.LinkStatus.Proposed),
                                None,
                                Nil,
                                List(p),
                                List(EventLinkCandidate.Reasons("confirmed by hand"))
                              )
                          )
                          Unused(links.setStatus(eid, ck, status(Event.LinkStatus.Confirmed)), Unused.Why.Idempotent)
                        confirm(tail, ok :+ a, no)
        val (attested, refused) = confirm(req.confirm.map(_.value.trim).filter(_.nonEmpty).distinct, Nil, Nil)
        val rejected = req.reject
          .map(_.value.trim)
          .filter(_.nonEmpty)
          .distinct
          .filter(ck =>
            Citekey
              .option(ck)
              .exists(k => links.setStatus(eid, sources.canonical(k).getOrElse(k), status(Event.LinkStatus.Rejected)))
          )
        EventLinksResult(
          EventLinkList(
            d.event.id,
            EventLinkList.Label(d.event.label.value),
            links.candidates(eid)
          ),
          attested,
          refused.map(EventLinksResult.Refused(_)),
          rejected.map(EventLinksResult.Rejected(_))
        )
      }
    }

  /** The candidates of many events, as a job: those never computed (or all), `links.batch` at a time. */
  def candidatesJob(caller: Caller, req: EventCandidatesRequest): Either[Api.Error, JobInfo] =
    req.event.map(_.value.trim).filter(_.nonEmpty) match
      case Some(one) =>
        Event.Id
          .option(one)
          .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$one"))))
          .flatMap(eventOrFail)
          .flatMap(_ =>
            jobs.start(EventLinksControllers.CandidatesJobKind, JobKinds.Subject(s"candidates of $one")) { (_, _) =>
              Event.Id
                .option(one)
                .toRight(notFound(Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$one"))))
                .flatMap(eventOrFail)
                .map(d =>
                  BackgroundJob.Outcome.Summary(s"${compute(d).candidates.size} candidate(s) for ${d.event.id}")
                )
                .left
                .map(e => e.problems)
            }
          )
      case None =>
        jobs.start(
          EventLinksControllers.CandidatesJobKind,
          JobKinds.Subject(
            s"candidate links of ${if req.all.value then "every event" else "the events not checked yet"}"
          )
        ) { (_, progress) =>
          val ids = links.eventsToCheck(
            req.all.value,
            req.limit.flatMap(l => PageSize.option(l.value)).getOrElse(PageSize(Int.MaxValue))
          )
          // `var`s: the counts of the walk
          var n, found = 0
          ids.grouped(ec.links.batch.value.max(1)).takeWhile(_ => !jobs.held).foreach { batch =>
            batch.foreach { id =>
              events.get(id).foreach(d => found += compute(d).candidates.size)
              n += 1
            }
            progress(JobInfo.Progress(s"$n of ${ids.size} event(s) checked · $found candidate(s)"))
          }
          Right(BackgroundJob.Outcome.Summary(s"$n event(s) checked · $found candidate(s)"))
        }

  private def leadOf(r: LeadRow): Option[EventLead] =
    EventLeads.of(
      LeadFacts(
        com.histmersion.api.catalogue.Event.Id(r.id.value),
        LeadFacts.Label(r.label.value),
        LeadFacts.Kind(r.kind.value),
        TimeSpan(
          r.notBefore.map(y => Year(y.value.toLong)),
          r.beginBy.map(y => Year(y.value.toLong)),
          r.endFrom.map(y => Year(y.value.toLong)),
          r.notAfter.map(y => Year(y.value.toLong))
        ),
        LeadFacts.Tier(r.tier.value),
        r.vei.map(v => LeadFacts.Vei(v.value)),
        r.magnitude.map(m => LeadFacts.Magnitude(m.value)),
        LeadFacts.FindingsNear(r.findingsNear.value),
        LeadFacts.Km(ec.leads.km.value),
        LeadFacts.Years(ec.leads.years.value),
        LeadFacts.Proposed(r.proposed.value),
        LeadFacts.Attesting(r.attesting.value)
      )
    )

  /** The leads of the events `q` selects — catalogue ones by default, with the catalogue's context. */
  def leads(q: Api.EventQuery): EventLead.List =
    val f = resolve(q.filter)
    def selection(place: Option[Place.Id]) = EventSelection(
      f,
      scope(f),
      q.text.map(t => EventSelection.Text(t.value)),
      q.kind.map(k => EventSelection.Kind(k.value)),
      q.historicity.map(h => EventSelection.Historicity(h.value)),
      place,
      EventSql.Narrow(
        Some(EventSql.Narrow.Tier(q.tier.fold(EventTier.Catalogue)(_.value))),
        context = EventSql.Narrow.Context(q.context.fold(true)(_.value)),
        q.area.flatMap(a => BBox.parse(BBox.Text(a.value)))
      )
    )
    // an id that is no place id names a place no event is at
    val selected = q.placeId.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Some(selection(None))
      case Some(p) => Place.Id.option(p).map(id => selection(Some(id)))
    selected.fold(EventLead.List(EventLead.List.Total(0), Nil)) { sel =>
      val (total, rows) =
        leadFacts.leads(
          sel,
          CatalogMerge.Tolerance.Km(ec.leads.km.value),
          CatalogMerge.Tolerance.Years(ec.leads.years.value),
          settings().limits.page(PageSize.option(f.limit.value), PageSize(50)),
          Offset.option(f.offset.value).getOrElse(Offset.Start)
        )
      EventLead.List(EventLead.List.Total(total.value), rows.flatMap(leadOf))
    }

  // ── an event's card ─────────────────────────────────────────────────────────────────────────

  /** The context an event's card shows: its tier and names, the catalogues that list it, the sources attesting it, the
    * findings near it, the proposed sources (stored — a card never computes them) and its lead.
    */
  def context(d: EventEntry): EventEntry =
    val id = idOf(d)
    val (tier, names, _) = events.tierAndNames(id).getOrElse((d.event.tier, Nil, None))
    val lead = leadFacts
      .leadOf(id, CatalogMerge.Tolerance.Km(ec.leads.km.value), CatalogMerge.Tolerance.Years(ec.leads.years.value))
      .flatMap(leadOf)
    d.modify(_.tier)
      .setTo(EventGeo.Tier(tier.value))
      .modify(_.names)
      .setTo(names.filterNot(_.value == d.event.label.value))
      .modify(_.catalogue)
      .setTo(
        events
          .catalogueRefs(id)
          .map(r =>
            // a plugin's catalogue: its terms come from the plugin, which the store does not know
            if r.licence.value.nonEmpty then r
            else
              info(CatalogRecord.Provider(r.provider.value)).fold(r)(i =>
                r.modify(_.title)
                  .setTo(EventCatalogueRef.Title(i.title.value))
                  .modify(_.licence)
                  .setTo(EventCatalogueRef.Licence(i.licence.value))
                  .modify(_.citation)
                  .setTo(EventCatalogueRef.Citation(i.citation.value))
              )
          )
      )
      .modify(_.sources)
      .setTo(leadFacts.attestingSources(id))
      .modify(_.findings)
      .setTo(
        PageSize
          .option(ec.links.nearFindings.value)
          .fold(Nil)(limit =>
            leadFacts.nearFindings(
              id,
              CatalogMerge.Tolerance.Km(ec.links.km.value),
              CatalogMerge.Tolerance.Years(ec.links.years.value),
              limit
            )
          )
      )
      .modify(_.candidates)
      .setTo(links.candidates(id).filter(_.status.value == Event.LinkStatus.Proposed))
      .modify(_.leads)
      .setTo(lead)

  /** The catalogue events an article's period and region hold that its body never names. */
  def articleLeads(
      body: ArticleEventLead.Body,
      citekeys: List[Citekey],
      period: Option[Period]
  ): List[ArticleEventLead] =
    PageSize
      .option(ec.leads.articleLimit.value * 3)
      .fold(Nil)(candidateLimit =>
        leadFacts.articleCandidates(
          citekeys,
          period.map(p => (SignedYear(p.start.value), SignedYear(p.end.value))),
          CatalogMerge.Tolerance.Km(ec.leads.articleKm.value),
          CatalogRecord.Vei(ec.leads.largeVei.value),
          CatalogRecord.Magnitude(ec.leads.largeMagnitude.value),
          candidateLimit
        )
      )
      .filterNot((_, label, _, _, names, _, _, _, _) =>
        ArticleEventLead.mentioned(body, EventEntry.Names(label.value) :: names)
      )
      .take(ec.leads.articleLimit.value)
      .map((id, label, kind, ts, _, vei, mag, n, prov) =>
        ArticleEventLead(
          com.histmersion.api.catalogue.Event.Id(id.value),
          ArticleEventLead.Label(label.value),
          ArticleEventLead.Kind(EventTaxonomy.canonical(EventSummary.Kind(kind.value)).value),
          ArticleEventLead.Years(EventLeads.years(ts).value),
          EventLeads
            .size(vei.map(v => LeadFacts.Vei(v.value)), mag.map(m => LeadFacts.Magnitude(m.value)))
            .map(ArticleEventLead.Size(_)),
          n,
          ArticleEventLead.Provider(info(prov).fold(prov.value)(_.title.value))
        )
      )

object EventLinksControllers:
  val CandidatesJobKind: Job.Kind =
    Job.Kind.applyUnsafe(EvidenceJobKinds.EventsCandidates.kind.value)
