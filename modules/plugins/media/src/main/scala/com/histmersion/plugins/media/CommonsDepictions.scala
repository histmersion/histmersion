package com.histmersion.plugins.media

import com.histmersion.plugin.*
import com.histmersion.upstreams.{WikidataEntities, WikidataStatement}

/** `depicts`: pictures that DEPICT an event, from Wikimedia Commons: for each event with a Wikidata item,
  *   1. the works its item lists as `P1299 depicted by` (a painting, a relief) — each work's own image (P18) — when
  *      `media.depicts.depicted-by` is on; then
  *   2. the Commons files whose structured data says `P180 depicts` the item ([[CommonsDepicts]]);
  * at most `media.depicts.per-event`, in role `depiction`. Licence, author and credit come from Commons as for every
  * Commons picture ([[CommonsFiles]]); the host judges them against `media.depicts.licences`.
  */
final class CommonsDepictions(media: MediaSettings, licences: LicenceReader) extends MediaCollection:
  val title = "Commons depictions"
  override val description =
    "Pictures that depict events: Wikimedia Commons files whose structured data says P180 'depicts' the event's " +
      "Wikidata item, and the images of the works its item lists as P1299 'depicted by'; licence read from Commons"
  val role = MediaCollection.Depiction
  override def matchRule = MediaMatchRule.Own
  override def asks = Set(MediaTarget.Events)
  override def requestIntervalMs = media.requestIntervalMs

  def search(place: MediaPlace, http: PluginHttp) = Left("the depictions are about events")

  override def searchTargets(places: List[MediaPlace], events: List[MediaEvent], http: PluginHttp): MediaFound =
    val asked = events.flatMap(e => e.wikidata.map(q => (e, q.value)))
    val wikidata = media.wikidata
    val qids = asked.map(_._2).distinct

    // 1. P1299 on the events' items, then P18 of the works named
    val byItem =
      if !media.depicts.depictedBy || asked.isEmpty then Nil
      else
        qids.grouped(wikidata.batch.max(1)).toList.map { ids =>
          ids -> http.askJson(WikidataEntities.url(wikidata.apiUrl, ids, wikidata.maxlag)).flatMap { j =>
            j.hcursor.downField("error").focus.fold(Right(j))(_ => Left("Wikidata error"))
          }
        }
    val depictedBy = byItem.flatMap(_._2.toOption).map(CommonsDepicts.depictedBy).flatMap(_.toList).toMap
    val works = depictedBy.values.flatten.toList.distinct
    val workAnswers = works.grouped(wikidata.batch.max(1)).toList.map { ids =>
      ids -> http
        .askJson(WikidataEntities.url(wikidata.apiUrl, ids, wikidata.maxlag))
        .flatMap(WikidataEntities.items(_, Set("P18")))
    }
    val workImage: Map[String, String] = workAnswers
      .flatMap(_._2.toOption)
      .flatMap(_.toList)
      .flatMap((q, item) =>
        WikidataStatement.best(item.statements, 1).headOption.map(st => q -> Commons.fileTitle(st.file))
      )
      .toMap

    // 2. Commons' P180 depicts, one search per event
    val searches = asked.map { (e, q) =>
      (
        e,
        q,
        http
          .askJson(CommonsDepicts.searchUrl(media.commons.apiUrl, q, media.depicts.searchLimit, media.commons.maxlag))
          .flatMap(CommonsDepicts.titles)
      )
    }
    val depicts = searches.collect { case (e, _, Right(ts)) => e.id -> ts }.toMap

    val found = asked.flatMap { (e, q) =>
      val fromWorks = depictedBy
        .getOrElse(q, Nil)
        .flatMap(workImage.get)
        .filter(_.nonEmpty)
        .map(f => (CommonsDepicts.DepictedBy, f))
      val fromFiles = depicts.getOrElse(e.id, Nil).map(f => (CommonsDepicts.Depicts, Commons.fileTitle(f)))
      (fromWorks ++ fromFiles).distinctBy(_._2).take(media.depicts.perEvent.max(0)).zipWithIndex.map {
        case ((p, f), i) => (e, q, p, f, i)
      }
    }
    val (files, judged) = CommonsFiles(http, media, licences).items(found.map(_._4).distinct)
    val unanswered = byItem.collect { case (ids, Left(_)) => ids }.flatten.toSet ++
      searches.collect { case (_, q, Left(_)) => q }
    MediaFound(
      uses = found.map((e, q, p, f, i) =>
        MediaUse(
          MediaTargetId(MediaTarget.Events, e.id),
          files(f),
          i,
          Some(p),
          WikidataId.option(q),
          note = Some(s"$p ${if p == CommonsDepicts.Depicts then "depicts" else "depicted by"} $q")
        )
      ),
      asked = asked.map((e, _) => MediaTargetId(MediaTarget.Events, e.id)),
      unanswered = asked.filter((_, q) => unanswered(q)).map((e, _) => MediaTargetId(MediaTarget.Events, e.id)).toSet,
      read = depictedBy.size + workImage.size,
      failed = byItem.collect { case (ids, Left(err)) => s"Wikidata ${ids.head}…: $err" } ++
        workAnswers.collect { case (ids, Left(err)) => s"Wikidata works ${ids.head}…: $err" } ++
        searches.collect { case (e, q, Left(err)) => s"Commons search for ${e.id} ($q): $err" } ++ judged
    )

  override def completeAll(items: List[MediaItem], http: PluginHttp): MediaCompleted =
    CommonsFiles(http, media, licences).thumbnails(items)
