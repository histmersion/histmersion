package com.histmersion.domain.media.imports

import hearth.kindlings.optics.syntax.*

import com.histmersion.pluginhost.{PluginMedia, Polite, SpiValues}

import com.histmersion.api.media.{ImageSize, MediaCount, MediaImportReport, MediaSample}
import com.histmersion.config.MediaConfig
import com.histmersion.domain.Licence
import com.histmersion.domain.media.assets.{ProviderAsset, Rights}
import com.histmersion.plugin.*
import com.histmersion.upstreams.media.MediaText

import scala.util.control.NonFatal

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
final class OwnImport(name: OwnImport.Name, col: MediaCollection, fetch: Polite, cfg: MediaConfig):

  private val check = BuiltinMedia.checkOf(name)
  private val provider = BuiltinMedia.providerOf(name)
  private val allow = LicencePolicy.of(BuiltinMedia.licencesFor(name, cfg))

  /** What the host decided about one picture: its licence key for the counts, and the item and rights when it may be
    * stored, else why not.
    */
  final private case class Verdict(licence: Option[Verdict.Licence], decision: Either[Verdict.DecisionPart1, (MediaItem, Rights)])

  object Verdict:
    type Licence = Licence.T
    object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecisionPart1 = DecisionPart1.T
    object DecisionPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def idOf(t: Target): com.histmersion.plugin.MediaTarget.Id =
    com.histmersion.plugin.MediaTarget
      .Id(if t.targetType == ImportRepo.Event then MediaTarget.Events else MediaTarget.Places, t.id)

  /** A use's Wikidata statement as the host checks it: one that is no item is left out, with a warning. */
  private def wikidataOf(u: MediaUse): Option[com.histmersion.domain.catalogue.places.Wikidata.Id] =
    val (q, why) = SpiValues.kept(u.wikidata)(SpiValues.wikidataOf)
    why.foreach(w => scribe.warn(s"media import: ${u.item.key}: $w, left out"))
    q.map(_.value)

  private def typeOf(t: com.histmersion.plugin.MediaTarget.Id): com.histmersion.domain.media.imports.OwnImport.MediaKind = t.kind match
    case MediaTarget.Events => ImportRepo.Event
    case MediaTarget.Places => ImportRepo.Place

  private def roleOf(u: MediaUse): com.histmersion.domain.media.MediaRole.Wire = u.item.role.map(_.trim).filter(_.nonEmpty).getOrElse(col.role)

  /** The host's reading of an item's stated licence, and its judgement. */
  private def judge(item: MediaItem): Verdict =
    item.rights match
      case Left(why)     => Verdict(None, Left(why))
      case Right(rights) =>
        val stated = scala.util.Try(SpiValues.licenceOf(rights.licence)).toOption.getOrElse("").trim
        val read = Licence.ofStatement(stated)
        val key = Some(if read.id.value.isEmpty then Licence.Family.Unknown.wire else read.id.value)
        item.withheld match
          case Some(why) => Verdict(key, Left(why))
          case None      =>
            PluginMedia.rights(rights) match
              case Left(why)                            => Verdict(key, Left(why))
              case Right(r) if !allow.allows(r.licence) =>
                Verdict(key, Left(s"licence ${r.licence.family.wire} not allowed for $provider"))
              case Right(r) => Verdict(key, Right(item -> Rights(r.licence.id.value, r.licenceUrl, r.author, r.credit)))

  def run(targets: List[Target], dryRun: com.histmersion.domain.media.imports.CollectionImport.DryRun, refresh: com.histmersion.domain.media.imports.OwnImport.Refresh, store: MediaStore): MediaImportReport =
    val places = targets.filter(_.targetType == ImportRepo.Place)
    val events = targets.filter(_.targetType == ImportRepo.Event)
    val carried =
      if col.placesCarrying.isDefined then store.pleiadesIds(places.map(_.id)) else Map.empty[String, List[String]]
    val asPlaces = places.map(t =>
      MediaPlace(
        t.id,
        t.label,
        SpiValues.wikidata(t.qid),
        ids = col.placesCarrying.toList.flatMap(s => carried.getOrElse(t.id, Nil).map(s -> _))
      )
    )
    val asEvents = events.map(t => MediaEvent(t.id, t.label, SpiValues.wikidata(t.qid)))
    val found =
      try col.searchTargets(asPlaces, asEvents, PluginMedia.http(fetch))
      catch
        case e: InterruptedException => throw e
        case NonFatal(e)             =>
          MediaFound(Nil, Nil, failed = List(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}"))
    val failed = List.newBuilder[String] ++= found.failed
    val byId = targets.map(t => idOf(t) -> t).toMap
    val uses = found.uses.filter(u => byId.contains(u.target))
    val verdicts = uses.map(_.item).distinctBy(_.key).map(it => it.key -> judge(it)).toMap

    val counted = MediaImportReport(
      provider = check,
      dryRun = dryRun,
      targets = found.asked.size,
      items = found.read,
      statements = uses.size,
      files = verdicts.size,
      byLicence = MediaCount
        .of(verdicts.values.flatMap(_.licence).toList, k => allow.allows(Licence.classify(k))),
      byRole = MediaCount.of(uses.map(roleOf)),
      rejected = MediaCount.of(verdicts.values.flatMap(_.decision.left.toOption).toList)
    )
    val samples = uses
      .flatMap { u =>
        for
          note <- u.note
          (_, r) <- verdicts.get(u.item.key).flatMap(_.decision.toOption)
          t <- byId.get(u.target)
        yield MediaSample(t.targetType, t.id, t.label, u.item.key, u.item.title, r.license, note, u.item.pageUrl)
      }
      .take(cfg.samples.max(0))
    val base = counted.modify(
      _.samples).setTo(samples).modify(_.rejected).setTo(MediaCount.of(counted.rejected.flatMap(c => List.fill(c.n)(c.key)) ++ found.notTaken)
    )

    if dryRun then base.modify(_.failed).setTo(failed.result())
    else
      val asked = found.asked.toSet -- found.unanswered
      val answered = targets.filter(t => asked(idOf(t)))
      persist(base, uses, verdicts, answered, refresh, store, failed, found.categories, byId)

  private def persist(
      base: MediaImportReport,
      uses: List[MediaUse],
      verdicts: Map[com.histmersion.domain.media.imports.OwnImport.VerdictKey, Verdict],
      answered: List[Target],
      refresh: com.histmersion.domain.media.imports.OwnImport.Refresh,
      db: MediaStore,
      failed: scala.collection.mutable.Builder[com.histmersion.domain.media.imports.CollectionImport.FailedKey, List[com.histmersion.domain.media.imports.OwnImport.Problem]],
      categories: List[(com.histmersion.domain.catalogue.places.Wikidata.Id, com.histmersion.domain.media.imports.OwnImport.CategoryText)],
      byId: Map[com.histmersion.plugin.MediaTarget.Id, Target]
  ): MediaImportReport =
    val known = db.known(provider, verdicts.keys.toList)
    def each[A](what: => String)(write: => A): Option[A] = MediaStep.each(failed)(what)(write)

    val revoked = verdicts.toList.collect {
      case (key, Verdict(_, Left(why)))
          if known.contains(key) && !why.startsWith("no answer") && !why.startsWith("missing") =>
        s"$key (media:${known(key).uid}): $why"
    }

    val accepted = verdicts.collect { case (k, Verdict(_, Right(v))) => k -> v }
    val toFetch = accepted.keys.filterNot(known.contains).toList.sorted
    val completed =
      try col.completeAll(toFetch.flatMap(accepted.get).map(_._1), PluginMedia.http(fetch))
      catch
        case e: InterruptedException => throw e
        case NonFatal(e)             =>
          MediaCompleted(Map.empty, List(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}"))
    failed ++= completed.failed
    val firstUse = uses.groupBy(_.item.key).view.mapValues(_.head).toMap

    // `var`s: the counts of what this run wrote
    var stored, updated = 0
    val uids = scala.collection.mutable.Map.empty[String, String]
    accepted.toList.sortBy(_._1).foreach { case (key, (item, r)) =>
      known.get(key) match
        case Some(k) =>
          if refresh then each(s"$key (licence re-read)")(db.refreshRights(k.uid, r))
          uids(key) = k.uid
        case None =>
          download(item, completed.items.get(key)) match
            case Left(why)                   => failed += s"$key: $why"
            case Right((bytes, mime, thumb)) =>
              val use = firstUse(key)
              val (width, height) = item.rendition.fold(ImageSize.of(bytes).map(_._1) -> ImageSize.of(bytes).map(_._2))(
                r => r.width -> r.height
              )
              val target = byId(use.target)
              val a = ProviderAsset(
                provider,
                key,
                MediaText.safeName(key),
                mime,
                bytes,
                width,
                height,
                thumb.map(_._1),
                thumb.map(_._2),
                thumb.flatMap(_._3),
                thumb.flatMap(_._4),
                r.license,
                r.licenseUrl,
                r.author,
                r.credit,
                item.pageUrl,
                item.originalUrl,
                Some(ProviderAsset.kindOf(roleOf(use)))
              )
              each(s"$key (store)")(db.save(a, (target.targetType, target.id))).foreach { (uid, inserted) =>
                if inserted then stored += 1 else updated += 1
                uids(key) = uid
              }
    }
    if refresh then updated += accepted.keys.count(known.contains)

    val linked = uses.count(u =>
      uids
        .get(u.item.key)
        .exists(uid =>
          each(s"${u.item.key} → ${typeOf(u.target)} ${u.target.id} (link)")(
            db.linkBasis(
              uid,
              typeOf(u.target),
              u.target.id,
              roleOf(u),
              u.statement.map(_.trim).filter(_.nonEmpty),
              wikidataOf(u),
              u.rank,
              u.basis
            )
          ).getOrElse(false)
        )
    )
    val found = uses
      .filter(u => uids.contains(u.item.key))
      .groupBy(u => (typeOf(u.target), u.target.id))
      .view
      .mapValues(_.map(_.item.key).distinct.size)
      .toMap
    answered.foreach(t =>
      each(s"${t.targetType} ${t.id} (check)")(
        db.checked(check, t.targetType, t.id, found.getOrElse((t.targetType, t.id), 0), Some(t.qid).filter(_.nonEmpty))
      )
    )
    val placeIds = answered.filter(_.targetType == ImportRepo.Place).map(t => t.id -> t).toMap
    val cats = categories.count((placeId, c) =>
      placeIds
        .get(placeId)
        .exists(t => each(s"category $c → $placeId")(db.commonsCategory(placeId, c, t.qid)).getOrElse(false))
    )
    base.modify(
      _.stored).setTo(stored).modify(_.updated).setTo(updated).modify(_.linked).setTo(linked).modify(_.categories).setTo(cats).modify(_.revoked).setTo(revoked.sorted).modify(_.failed).setTo(failed.result()
    )

  /** The picture and its thumbnail — the provider's own copies; the picture must not pass `max-bytes`. A failed
    * thumbnail leaves the picture without one (the card skips it).
    */
  private def download(
      item: MediaItem,
      completed: Option[MediaItem]
  ): Either[com.histmersion.domain.media.imports.OwnImport.DownloadOutcome, (com.histmersion.domain.media.assets.AssetService.Bytes, com.histmersion.domain.media.assets.AssetService.Mime, Option[(com.histmersion.domain.media.assets.AssetService.Bytes, com.histmersion.domain.media.assets.AssetService.Mime, Option[com.histmersion.domain.media.assets.AssetService.ThumbWidth], Option[com.histmersion.domain.media.assets.AssetService.ThumbHeight])])] =
    val mime = item.rendition.map(_.mime).getOrElse("image/jpeg")
    item.rendition.flatMap(_.bytes).filter(_ > cfg.maxBytes) match
      case Some(size) => Left(s"original of $size bytes is larger than media.max-bytes")
      case None       =>
        item.imageUrl.toRight("no image").flatMap(u => fetch.file(u, mime)).flatMap { (bytes, got) =>
          if bytes.length.toLong > cfg.maxBytes then
            Left(s"rendition of ${bytes.length} bytes is larger than media.max-bytes")
          else
            val t = completed.flatMap(i => i.thumbUrl.map(u => (u, i))).flatMap { (u, i) =>
              fetch.file(u, mime) match
                case Right((tb, tm)) =>
                  Some((tb, tm, i.rendition.flatMap(_.thumbWidth), i.rendition.flatMap(_.thumbHeight)))
                case Left(why) => scribe.warn(s"media: thumbnail of ${item.key} not fetched: $why"); None
            }
            Right((bytes, got, t))
        }

object OwnImport:
  type CategoryText = CategoryText.T
  object CategoryText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadOutcome = DownloadOutcome.T
  object DownloadOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImporterName = ImporterName.T
  object ImporterName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaKind = MediaKind.T
  object MediaKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refresh = Refresh.T
  object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictKey = VerdictKey.T
  object VerdictKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = com.histmersion.domain.media.imports.OwnImport.ImporterName
  val Name: _root_.com.histmersion.domain.media.imports.OwnImport.ImporterName.type = _root_.com.histmersion.domain.media.imports.OwnImport.ImporterName

