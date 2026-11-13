package com.histmersion
package domain.media.imports

import com.histmersion.util.problem.Text.sentence
import hearth.kindlings.optics.syntax.*

import com.histmersion.pluginhost.{HttpTransport, PluginMedia, Polite, SpiValues}

import com.histmersion.api.media.{ImageSize, MediaCount, MediaImportReport, MediaImportRequest, MediaSample}
import com.histmersion.config.MediaConfig
import com.histmersion.domain.Licence
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.places.{ExternalIds, Wikidata}
import com.histmersion.domain.media.assets.{AssetService, ProviderAsset, Rights}
import com.histmersion.plugin.*
import com.histmersion.upstreams.media.MediaText

import scala.util.control.NonFatal
import com.histmersion.plugin.MediaTarget
import com.histmersion.domain.media.MediaRole

/** The import of a collection of its targets' own pictures ([[MediaMatchRule.Own]]: a Wikidata item's statements, the
  * files a gazetteer record or a depiction search names): the collection is asked about the whole batch at once
  * ([[MediaCollection.searchTargets]]) and states each picture's licence; the host reads every statement, judges it
  * against the collection's allowlist and counts what it read. A dry run stops there. Otherwise each accepted picture
  * not stored yet is completed in one batch ([[MediaCollection.completeAll]]: its thumbnail), downloaded — the
  * provider's own rendition, at most `media.max-bytes` — and stored; a stored one is linked and, with `refresh`, has
  * its licence re-read; every use is linked to its target in its role, and each target the collection asked about and
  * answered is recorded as asked. A stored picture whose licence a re-read no longer allows is KEPT, and listed for
  * review.
  *
  * @param name
  *   the collection's name in the registry
  */
final class OwnImport(name: MediaImporter.ImporterName, col: MediaCollection, fetch: Polite, cfg: MediaConfig):

  private val check: String = BuiltinMedia.checkOf(BuiltinMedia.CheckOfName(name.value)).value
  private val provider: String = BuiltinMedia.providerOf(BuiltinMedia.ProviderOfName(name.value)).value
  private val allow = LicencePolicy.of(
    Licence.AllowList(BuiltinMedia.licencesFor(BuiltinMedia.LicencesForName(name.value), cfg).value)
  )

  /** What the host decided about one picture: its licence key for the counts, and the item and rights when it may be
    * stored, else why not.
    */
  final private case class Verdict(licence: Option[String], decision: Either[String, (MediaItem, Rights)])

  private def idOf(t: Target): MediaTarget.Id =
    MediaTarget
      .Id(
        if t.targetType == ImportRepo.Event then MediaTarget.Events else MediaTarget.Places,
        MediaTarget.Id.Value(t.id.value)
      )

  /** A use's Wikidata statement as the host checks it: one that is no item is left out, with a warning. */
  private def wikidataOf(u: MediaUse): Option[Wikidata.Id] =
    val (q, why) = SpiValues.kept(u.wikidata)(SpiValues.wikidataOf)
    why.foreach(w => scribe.warn(s"media import: ${u.item.key}: ${w.sentence}, left out"))
    q

  private def typeOf(t: MediaTarget.Id): Target.TargetType = t.kind match
    case MediaTarget.Events => ImportRepo.Event
    case MediaTarget.Places => ImportRepo.Place

  private def roleOf(u: MediaUse): MediaRole.Wire =
    MediaRole.Wire(
      u.item.role.map(_.value.trim).filter(_.nonEmpty).getOrElse(col.role.value)
    )

  private def providerKey(key: MediaItem.Key): AssetService.ProviderKey = AssetService.ProviderKey(key.value)

  /** The host's reading of an item's stated licence, and its judgement. */
  private def judge(item: MediaItem): Verdict =
    item.rights match
      case Left(why)     => Verdict(None, Left(why.sentence))
      case Right(rights) =>
        val stated = scala.util.Try(SpiValues.licenceOf(rights.licence)).toOption.map(_.value).getOrElse("").trim
        val read = Licence.ofStatement(Licence.Statement(stated))
        val key: Option[String] = Some(
          if read.id.value.isEmpty then Licence.Family.Unknown.wire.value else read.id.value
        )
        item.withheld match
          case Some(why) => Verdict(key, Left(why.value))
          case None      =>
            PluginMedia.rights(rights) match
              case Left(why)                            => Verdict(key, Left(why.sentence))
              case Right(r) if !allow.allows(r.licence) =>
                Verdict(key, Left(s"licence ${r.licence.family.wire} not allowed for $provider"))
              case Right(r) =>
                Verdict(
                  key,
                  Right(
                    item -> Rights(
                      Rights.License(r.licence.id.value),
                      r.licenceUrl.map(u => Rights.LicenseUrl(u.value)),
                      r.author.map(a => Rights.Author(a.value)),
                      Rights.Credit(r.credit.value)
                    )
                  )
                )

  def run(
      targets: List[Target],
      dryRun: MediaImporter.DryRun,
      refresh: ImportService.Refresh,
      store: MediaStore
  ): MediaImportReport =
    val places = targets.filter(_.targetType == ImportRepo.Place)
    val events = targets.filter(_.targetType == ImportRepo.Event)
    val carried =
      if col.placesCarrying.isDefined then store.pleiadesIds(places.flatMap(t => Place.Id.option(t.id.value)))
      else Map.empty[Place.Id, List[ExternalIds.ExternalId]]
    val asPlaces = places.map(t =>
      MediaPlace(
        MediaPlace.Id(t.id.value),
        MediaPlace.Label(t.label.value),
        Wikidata.Id.option(t.qid.value).flatMap(SpiValues.wikidata),
        ids = col.placesCarrying.toList.flatMap(s =>
          Place.Id
            .option(t.id.value)
            .flatMap(carried.get)
            .getOrElse(Nil)
            .map(e => GazetteerRef.Scheme(s.value) -> GazetteerRef.Id(e.value))
        )
      )
    )
    val asEvents = events.map(t =>
      MediaEvent(
        MediaEvent.Id(t.id.value),
        MediaEvent.Label(t.label.value),
        Wikidata.Id.option(t.qid.value).flatMap(SpiValues.wikidata)
      )
    )
    val found =
      try col.searchTargets(asPlaces, asEvents, PluginMedia.http(fetch))
      catch
        case e: InterruptedException => throw e
        case NonFatal(e)             =>
          MediaFound(
            Nil,
            Nil,
            failed = List(MediaFound.Failed(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}"))
          )
    val failed = List.newBuilder[String] ++= found.failed.map(_.value)
    val byId = targets.map(t => idOf(t) -> t).toMap
    val uses = found.uses.filter(u => byId.contains(u.target))
    val verdicts = uses.map(_.item).distinctBy(_.key).map(it => it.key -> judge(it)).toMap

    val counted = MediaImportReport(
      provider = com.histmersion.api.media.MediaItem.Provider(check),
      dryRun = MediaImportRequest.DryRun(dryRun.value),
      targets = MediaImportReport.Targets(found.asked.size),
      items = MediaImportReport.Items(found.read.value),
      statements = MediaImportReport.Statements(uses.size),
      files = MediaImportReport.Files(verdicts.size),
      byLicence = MediaCount
        .of(
          verdicts.values.flatMap(_.licence).toList.map(MediaCount.Key(_)),
          k => allow.allows(Licence.classify(Licence.Id(k.value)))
        ),
      byRole = MediaCount.of(uses.map(u => MediaCount.Key(roleOf(u).value))),
      rejected = MediaCount.of(verdicts.values.flatMap(_.decision.left.toOption).toList.map(MediaCount.Key(_)))
    )
    val samples = uses
      .flatMap { u =>
        for
          note <- u.note
          (_, r) <- verdicts.get(u.item.key).flatMap(_.decision.toOption)
          t <- byId.get(u.target)
        yield MediaSample(
          com.histmersion.api.media.MediaTarget.TargetType(t.targetType.value),
          com.histmersion.api.media.MediaTarget.TargetId(t.id.value),
          MediaSample.TargetLabel(t.label.value),
          MediaSample.Key(u.item.key.value),
          MediaSample.Title(u.item.title.value),
          com.histmersion.api.media.MediaItem.License(r.license.value),
          MediaSample.Basis(note.value),
          MediaSample.Url(u.item.pageUrl.value)
        )
      }
      .take(cfg.samples.value.max(0))
    val base = counted
      .modify(_.samples)
      .setTo(samples)
      .modify(_.rejected)
      .setTo(
        MediaCount.of(
          counted.rejected.flatMap(c => List.fill(c.n.value)(c.key)) ++ found.notTaken.map(n => MediaCount.Key(n.value))
        )
      )

    if dryRun.value then base.modify(_.failed).setTo(failed.result().map(MediaImportReport.Failed(_)))
    else
      val asked = found.asked.toSet -- found.unanswered
      val answered = targets.filter(t => asked(idOf(t)))
      persist(base, uses, verdicts, answered, refresh, store, failed, found.categories, byId)

  private def persist(
      base: MediaImportReport,
      uses: List[MediaUse],
      verdicts: Map[MediaItem.Key, Verdict],
      answered: List[Target],
      refresh: ImportService.Refresh,
      db: MediaStore,
      failed: scala.collection.mutable.Builder[String, List[String]],
      categories: List[(MediaFound.Category.Target, MediaFound.Category.Name)],
      byId: Map[MediaTarget.Id, Target]
  ): MediaImportReport =
    val known = db.known(ImportService.Provider(provider), verdicts.keys.toList.map(providerKey))
    def each[A](what: => String)(write: => A): Option[A] = MediaStep.each(failed)(what)(write)

    val revoked = verdicts.toList.collect {
      case (key, Verdict(_, Left(why)))
          if known.contains(providerKey(key)) && !why.startsWith("no answer") && !why.startsWith("missing") =>
        s"$key (media:${known(providerKey(key)).uid}): $why"
    }

    val accepted = verdicts.collect { case (k, Verdict(_, Right(v))) => k -> v }
    val toFetch = accepted.keys.filterNot(k => known.contains(providerKey(k))).toList.sorted
    val completed =
      try col.completeAll(toFetch.flatMap(accepted.get).map(_._1), PluginMedia.http(fetch))
      catch
        case e: InterruptedException => throw e
        case NonFatal(e)             =>
          MediaCompleted(
            Map.empty,
            List(MediaCompleted.Failed(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}"))
          )
    failed ++= completed.failed.map(_.value)
    val firstUse = uses.groupBy(_.item.key).view.mapValues(_.head).toMap

    // `var`s: the counts of what this run wrote
    var stored, updated = 0
    val uids = scala.collection.mutable.Map.empty[MediaItem.Key, AssetService.AssetUid]
    accepted.toList.sortBy(_._1.value).foreach { case (key, (item, r)) =>
      known.get(providerKey(key)) match
        case Some(k) =>
          if refresh.value then each(s"$key (licence re-read)")(db.refreshRights(AssetService.AssetUid(k.uid.value), r))
          uids(key) = AssetService.AssetUid(k.uid.value)
        case None =>
          download(item, completed.items.get(key)) match
            case Left(why)                   => failed += s"$key: $why"
            case Right((bytes, mime, thumb)) =>
              val use = firstUse(key)
              val sized = ImageSize.of(ImageSize.Bytes(bytes))
              val (width, height) = item.rendition.fold(sized.map(_._1.value) -> sized.map(_._2.value))(r =>
                r.width.map(_.value) -> r.height.map(_.value)
              )
              val target = byId(use.target)
              val a = ProviderAsset(
                ProviderAsset.Provider(provider),
                ProviderAsset.Key(key.value),
                ProviderAsset.Name(MediaText.safeName(WorkKey.Title(key.value)).value),
                ProviderAsset.Mime(mime),
                ProviderAsset.Bytes(bytes),
                width.map(ProviderAsset.Width(_)),
                height.map(ProviderAsset.Height(_)),
                thumb.map(t => ProviderAsset.Thumb(t._1)),
                thumb.map(t => ProviderAsset.ThumbMime(t._2)),
                thumb.flatMap(_._3).map(ProviderAsset.ThumbWidth(_)),
                thumb.flatMap(_._4).map(ProviderAsset.ThumbHeight(_)),
                ProviderAsset.License(r.license.value),
                r.licenseUrl.map(u => ProviderAsset.LicenseUrl(u.value)),
                r.author.map(a => ProviderAsset.Author(a.value)),
                ProviderAsset.Credit(r.credit.value),
                ProviderAsset.SourceUrl(item.pageUrl.value),
                item.originalUrl.map(u => ProviderAsset.OriginalUrl(u.value)),
                Some(ProviderAsset.kindOf(roleOf(use)))
              )
              each(s"$key (store)")(
                db.save(a, (ImportService.TargetType(target.targetType.value), ImportService.TargetId(target.id.value)))
              ).foreach { (uid, inserted) =>
                if inserted then stored += 1 else updated += 1
                uids(key) = uid
              }
    }
    if refresh.value then updated += accepted.keys.count(k => known.contains(providerKey(k)))

    val linked = uses.count(u =>
      uids
        .get(u.item.key)
        .exists(uid =>
          each(s"${u.item.key} → ${typeOf(u.target)} ${u.target.id} (link)")(
            db.linkBasis(
              uid,
              ImportService.TargetType(typeOf(u.target).value),
              ImportService.TargetId(u.target.id.value),
              AssetService.LinkRole(roleOf(u).value),
              u.statement.map(_.value.trim).filter(_.nonEmpty).map(AssetService.LinkProperty(_)),
              wikidataOf(u).map(q => AssetService.LinkItem(q.value)),
              AssetService.LinkRank(u.rank.value),
              u.basis.map(b => AssetService.LinkBasis(b.value))
            )
          ).getOrElse(false)
        )
    )
    val found = uses
      .filter(u => uids.contains(u.item.key))
      .groupBy(u => (typeOf(u.target), Target.Id(u.target.id.value)))
      .view
      .mapValues(_.map(_.item.key).distinct.size)
      .toMap
    answered.foreach(t =>
      each(s"${t.targetType} ${t.id} (check)")(
        db.checked(
          ImportService.Provider(check),
          ImportService.TargetType(t.targetType.value),
          ImportService.TargetId(t.id.value),
          ImportService.FoundCount(found.getOrElse((t.targetType, t.id), 0)),
          Option.when(t.qid.value.nonEmpty)(t.qid.value).flatMap(q => Wikidata.Id.option(q))
        )
      )
    )
    val placeIds = answered.filter(_.targetType == ImportRepo.Place).map(t => t.id -> t).toMap
    val cats = categories.count((placeId, c) =>
      placeIds
        .get(Target.Id(placeId.value))
        .exists(t =>
          (for
            pid <- Place.Id.option(placeId.value)
            qid <- Wikidata.Id.option(t.qid.value)
          yield each(s"category $c → $placeId")(db.commonsCategory(pid, ImportService.CommonsCategory(c.value), qid))
            .getOrElse(false)).getOrElse(false)
        )
    )
    base
      .modify(_.stored)
      .setTo(MediaImportReport.Stored(stored))
      .modify(_.updated)
      .setTo(MediaImportReport.Updated(updated))
      .modify(_.linked)
      .setTo(MediaImportReport.Linked(linked))
      .modify(_.categories)
      .setTo(MediaImportReport.Categories(cats))
      .modify(_.revoked)
      .setTo(revoked.sorted.map(MediaImportReport.Revoked(_)))
      .modify(_.failed)
      .setTo(failed.result().map(MediaImportReport.Failed(_)))

  /** The picture and its thumbnail — the provider's own copies; the picture must not pass `max-bytes`. A failed
    * thumbnail leaves the picture without one (the card skips it).
    */
  private def download(
      item: MediaItem,
      completed: Option[MediaItem]
  ): Either[String, (Array[Byte], String, Option[(Array[Byte], String, Option[Int], Option[Int])])] =
    val mime = item.rendition.map(_.mime.value).getOrElse("image/jpeg")
    item.rendition.flatMap(_.bytes).map(_.value).filter(_ > cfg.maxBytes.value) match
      case Some(size) => Left(s"original of $size bytes is larger than media.max-bytes")
      case None       =>
        item.imageUrl
          .toRight("no image")
          .flatMap(u =>
            fetch.file(HttpTransport.Url(u.value), Polite.Mime(mime)).left.map(f => s"${f.problem.sentence}")
          )
          .flatMap { (bytes, got) =>
            if bytes.value.length.toLong > cfg.maxBytes.value then
              Left(s"rendition of ${bytes.value.length} bytes is larger than media.max-bytes")
            else
              val t = completed.flatMap(i => i.thumbUrl.map(u => (u, i))).flatMap { (u, i) =>
                fetch.file(HttpTransport.Url(u.value), Polite.Mime(mime)) match
                  case Right((tb, tm)) =>
                    Some(
                      (
                        tb.value,
                        tm.value,
                        i.rendition.flatMap(_.thumbWidth).map(_.value),
                        i.rendition.flatMap(_.thumbHeight).map(_.value)
                      )
                    )
                  case Left(why) =>
                    scribe.warn(s"media: thumbnail of ${item.key} not fetched: ${why.problem.sentence}"); None
              }
              Right((bytes.value, got.value, t))
          }
