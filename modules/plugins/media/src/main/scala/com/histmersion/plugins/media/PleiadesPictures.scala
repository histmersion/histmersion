package com.histmersion
package plugins.media

import com.histmersion.plugin.Problem
import com.histmersion.plugin.*

/** `pleiades`: pictures a Pleiades place record links. For each place on the map that carries a Pleiades id, its place
  * record is read once; the Wikimedia Commons files its references name ([[PleiadesLinks]]) — at most `per-place`, in
  * the record's order — are stated with their licence ON COMMONS ([[CommonsFiles]]: Pleiades' own CC BY covers its
  * data, not what it links), in role `image`, the link's basis naming the record. No name is matched: the place carries
  * the id the record is under. A link to Flickr, to a Commons category or to a picture on another site is counted with
  * that reason. The host judges the licences against `media.pleiades.licences`.
  */
final class PleiadesPictures(media: MediaSettings, licences: LicenceReader) extends MediaCollection:
  private val conf = media.pleiades
  val title = MediaCollection.Title("Pleiades-linked pictures")
  override val description = MediaCollection.Description(
    "Wikimedia Commons files that Pleiades place records link, for the places on the map that carry a Pleiades id " +
      "(matched by that id, no name matching); licence read from Commons; links to Flickr (needs a key), Commons " +
      "categories and other sites are counted, not taken"
  )
  val role = MediaCollection.Image
  override def matchRule = MediaMatchRule.Own
  override def placesCarrying = Some(MediaCollection.PlacesCarrying(PleiadesPictures.Scheme))
  override def perPlace = MediaCollection.PerPlace(conf.perPlace.value)
  override def requestIntervalMs = MediaCollection.RequestIntervalMs(conf.requestIntervalMs.value)

  override def check =
    Either.cond(
      conf.enabled.value,
      (),
      Problem(
        "Pleiades (linked Commons files) is switched off (media.pleiades.enabled = false)"
      )
    )

  def search(place: MediaPlace, http: Plugin.Http) = Left(
    Plugin.Http.Failure.Transport(Problem("the Pleiades pictures are found for a batch of places"))
  )

  private def placeTarget(p: MediaPlace): MediaTarget.Id =
    MediaTarget.Id(MediaTarget.Places, MediaTarget.Id.Value(p.id.value))

  override def searchTargets(places: List[MediaPlace], events: List[MediaEvent], http: Plugin.Http): MediaFound =
    // 1. each place's record(s): the picture links, in order
    val read = places.map { p =>
      val pids = p.ids.collect {
        case (scheme, id) if scheme.value == PleiadesPictures.Scheme => GazetteerPlace.Id(id.value)
      }
      (
        p,
        pids,
        pids.map(pid => pid -> http.askJson(PleiadesLinks.placeUrl(conf.apiUrl, pid)).left.map(_.problem).flatMap(PleiadesLinks.links))
      )
    }
    // 2. the Commons files, at most per-place; the rest counted by reason
    val found = read.flatMap { (p, _, answers) =>
      answers
        .flatMap((pid, a) => a.toOption.toList.flatten.flatMap(l => l.file.toOption.map(f => (pid, l, f))))
        .distinctBy(_._3)
        .take(conf.perPlace.value.max(0))
        .zipWithIndex
        .map { case ((pid, l, f), i) => (p, f, i, s"Pleiades place $pid links it (${l.citation.value})") }
    }
    val (files, judged) = Commons.Files(http, media, licences).items(found.map(_._2).distinct)
    MediaFound(
      uses = found.map((p, f, i, basis) =>
        MediaUse(
          placeTarget(p),
          files(f),
          MediaUse.Rank(i),
          basis = Some(MediaUse.Basis(basis)),
          note = Some(MediaUse.Note(basis))
        )
      ),
      asked = places.map(placeTarget),
      unanswered = read.collect { case (p, _, as) if as.exists(_._2.isLeft) => placeTarget(p) }.toSet,
      read = MediaFound.Read(read.map(_._3.count(_._2.isRight)).sum),
      failed = read
        .flatMap { (p, pids, answers) =>
          (if pids.isEmpty then List(s"place ${p.id.value} (${p.label.value}): no Pleiades id") else Nil) ++
            answers.collect { case (pid, Left(e)) => s"Pleiades place ${pid.value} (${p.id.value}): $e" }
        }
        .map(MediaFound.Failed(_)) ++ judged.map(j => MediaFound.Failed(j.value)),
      notTaken = read
        .flatMap(_._3.flatMap(_._2.toOption.toList.flatten.flatMap(_.file.left.toOption)))
        .map(why => MediaFound.NotTaken(s"$why"))
    )

  override def completeAll(items: List[MediaItem], http: Plugin.Http): MediaCompleted =
    Commons.Files(http, media, licences).thumbnails(items)

object PleiadesPictures:
  /** The gazetteer the places it asks about carry an id of. */
  val Scheme = "pleiades"
