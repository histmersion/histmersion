package com.histmersion.domain.library.collections

import com.histmersion.api.library.Acquire
import com.histmersion.plugin.CollectionItem

/** Where an import writes, once the citekey is known. */
enum ImportTarget:
  /** No record has this citekey yet. */
  case Create

  /** The record exists; enrich it and link it to the imported entry. `previous` is the Calibre id it was linked to
    * before, when that was a different one.
    */
  case Update(previous: Option[ImportTarget.Update.Previous])

object ImportTarget:
  object Update:
    type Previous = Previous.T
    object Previous extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type VolumeOfResult = VolumeOfResult.T
  object VolumeOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VolumeOfTitle = VolumeOfTitle.T
  object VolumeOfTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RomanResult = RomanResult.T
  object RomanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RomanS = RomanS.T
  object RomanS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeyForResult = CitekeyForResult.T
  object CitekeyForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfCk = OfCk.T
  object OfCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfId = OfId.T
  object OfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfExplicit = OfExplicit.T
  object OfExplicit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfExisting = OfExisting.T
  object OfExisting extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryResult = SummaryResult.T
  object SummaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryFromCollection = SummaryFromCollection.T
  object SummaryFromCollection extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryCurrent = SummaryCurrent.T
  object SummaryCurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Volume = """(?i)\b(?:volume\s*|vol\.\s*|vol\s+)([0-9]+|[ivxlc]+)\b""".r

  /** The volume a title names — `Volume I`, `Vol. 2`, `volume 3` — so two volumes of one work get two citekeys. */
  def volumeOf(title: com.histmersion.domain.library.collections.ImportTarget.VolumeOfTitle): Option[com.histmersion.domain.library.collections.ImportTarget.VolumeOfResult] =
    Volume.findFirstMatchIn(title).flatMap(m => m.group(1).toIntOption.orElse(roman(m.group(1)))).filter(_ > 0)

  private def roman(s: com.histmersion.domain.library.collections.ImportTarget.RomanS): Option[com.histmersion.domain.library.collections.ImportTarget.RomanResult] =
    val v = Map('i' -> 1, 'v' -> 5, 'x' -> 10, 'l' -> 50, 'c' -> 100)
    val ds = s.toLowerCase.toList.map(v)
    Option.when(ds.nonEmpty)(ds.zipAll(ds.drop(1), 0, 0).map((a, b) => if a < b then -a else a).sum)

  /** The citekey an import generates: the library's convention plus `_v<n>` for a numbered volume. */
  def citekeyFor(item: CollectionItem): com.histmersion.domain.library.collections.ImportTarget.CitekeyForResult =
    val title = item.title.getOrElse("")
    Acquire.citekey(item.authors, item.year.map(_.value), Some(title)) + volumeOf(title).fold("")(n => s"_v$n")

  /** Whether importing entry `id` under `ck` may go ahead: two volumes once generated the same citekey and were merged
    * into one record.
    *
    * @param explicit
    *   the citekey was given, so landing on an existing record is deliberate
    * @param existing
    *   `None` when no record has `ck`; otherwise that record's Calibre id
    */
  def of(ck: com.histmersion.domain.library.collections.ImportTarget.OfCk, id: com.histmersion.domain.library.collections.ImportTarget.OfId, explicit: com.histmersion.domain.library.collections.ImportTarget.OfExplicit, existing: Option[Option[com.histmersion.domain.library.collections.ImportTarget.OfExisting]]): Either[com.histmersion.domain.library.collections.ImportTarget.OfResultPart1, ImportTarget] =
    existing match
      case None                               => Right(Create)
      case Some(Some(linked)) if linked == id => Right(Update(None))
      case Some(linked) if explicit           => Right(Update(linked))
      case Some(linked)                       =>
        val what = linked.fold("a record that is not linked to Calibre")(l => s"the record of Calibre id $l")
        Left(
          s"the generated citekey '$ck' is already $what — importing Calibre id $id would merge two " +
            s"works into one record. Pass --citekey <another key> for a separate record, or --citekey $ck " +
            "to import into that record on purpose"
        )

  /** The summary an import writes: Calibre's description only fills an empty one, so a re-import never replaces a
    * summary an agent compiled. `None` leaves the record's summary alone.
    */
  def summary(fromCollection: Option[com.histmersion.domain.library.collections.ImportTarget.SummaryFromCollection], current: Option[com.histmersion.domain.library.collections.ImportTarget.SummaryCurrent]): Option[com.histmersion.domain.library.collections.ImportTarget.SummaryResult] =
    if current.exists(_.trim.nonEmpty) then None else fromCollection
