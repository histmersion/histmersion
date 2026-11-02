package com.histmersion.plugins.media

import com.histmersion.plugin.*
import com.histmersion.upstreams.wikidata.{WikidataEntities, WikidataStatement}

/** `wikidata`: the pictures of places and events from their Wikidata items, licensed on Wikimedia Commons.
  *
  * For each place or event: its item's picture statements (a place: image P18, locator map P242, plan P3311, aerial
  * view P8592, route map P15; an event: P18 — `media.wikidata.*-properties`), the best-ranked `per-property` of each,
  * and a place's Commons category (P373). For each file named: its licence, author and credit from Commons' `imageinfo`
  * + `extmetadata`, read and never assumed ([[CommonsFiles]]). The host judges the licence against
  * `media.commons.licences`, stores Commons' own renditions and links each picture to its target in its role.
  */
final class WikidataPictures(media: MediaSettings, licences: LicenceReader) extends MediaCollection:
  val title = "Wikidata pictures"
  override val description =
    "Pictures of places and events from their Wikidata items (image, locator map, plan, aerial view, route map), " +
      "licensed on Wikimedia Commons; a job asks about every place on the map not asked yet (one per Wikidata item, " +
      "most-cited sites first), batch by batch, and resumes where it stopped"
  val role = MediaCollection.Image
  override def roles = (media.wikidata.placeRoles ++ media.wikidata.eventRoles).map(_._2).toSet + role
  override def matchRule = MediaMatchRule.Own
  override def asks = Set(MediaTarget.Places, MediaTarget.Events)
  override def requestIntervalMs = media.requestIntervalMs

  def search(place: MediaPlace, http: PluginHttp) =
    Left("the Wikidata pictures are found for a batch of targets at once")

  /** One target as the import asks it: what it is, its id and its Wikidata item. */
  final private case class Asked(target: MediaTargetId, qid: String, roles: List[(String, String)])

  override def searchTargets(places: List[MediaPlace], events: List[MediaEvent], http: PluginHttp): MediaFound =
    // the events first, then the places: the order the host lists them in
    val targets =
      events.flatMap(e =>
        e.wikidata.map(q => Asked(MediaTargetId(MediaTarget.Events, e.id), q.value, media.wikidata.eventRoles))
      ) ++ places.flatMap(p =>
        p.wikidata.map(q => Asked(MediaTargetId(MediaTarget.Places, p.id), q.value, media.wikidata.placeRoles))
      )
    val props = (media.wikidata.placeRoles ++ media.wikidata.eventRoles).map(_._1).toSet
    // the items whose batch Wikidata did not answer: their targets are NOT recorded as asked
    val answers = targets.map(_.qid).distinct.grouped(media.wikidata.batch.max(1)).toList.map { ids =>
      ids -> http
        .askJson(WikidataEntities.url(media.wikidata.apiUrl, ids, media.wikidata.maxlag))
        .flatMap(WikidataEntities.items(_, props))
    }
    val items = answers.flatMap(_._2.toOption).flatMap(_.toList).toMap
    val unanswered = answers.collect { case (ids, Left(_)) => ids }.flatten.toSet
    val found = targets.flatMap { t =>
      items.get(t.qid).toList.flatMap { item =>
        t.roles.flatMap { (prop, r) =>
          WikidataStatement.best(item.statements.filter(_.property == prop), media.perProperty).zipWithIndex.flatMap {
            (st, i) => Option(Commons.fileTitle(st.file)).filter(_.nonEmpty).map(f => (t, r, prop, f, i))
          }
        }
      }
    }
    val (files, judged) = CommonsFiles(http, media, licences).items(found.map(_._4).distinct)
    MediaFound(
      uses = found.map((t, r, prop, f, i) =>
        MediaUse(t.target, files(f).copy(role = Some(r)), i, Some(prop), WikidataId.option(t.qid))
      ),
      asked = targets.map(_.target),
      unanswered = targets.filter(t => unanswered(t.qid)).map(_.target).toSet,
      read = items.size,
      failed = answers.collect { case (ids, Left(e)) => s"Wikidata ${ids.head}…: $e" } ++ judged,
      categories = targets
        .filter(_.target.kind == MediaTarget.Places)
        .flatMap(t => items.get(t.qid).flatMap(_.commonsCategory).map(t.target.id -> _))
    )

  override def completeAll(items: List[MediaItem], http: PluginHttp): MediaCompleted =
    CommonsFiles(http, media, licences).thumbnails(items)
