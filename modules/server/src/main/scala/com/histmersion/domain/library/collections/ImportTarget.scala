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
  case Update(previous: Option[ImportTarget.Previous])

object ImportTarget:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RomanNumeral = RomanNumeral.T
  object RomanNumeral extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Volume = Volume.T
  object Volume extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Previous = Previous.T
  object Previous extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Why an import may not go ahead. */
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfExplicit = OfExplicit.T
  object OfExplicit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val VolumeText = """(?i)\b(?:volume\s*|vol\.\s*|vol\s+)([0-9]+|[ivxlc]+)\b""".r

  /** The volume a title names — `Volume I`, `Vol. 2`, `volume 3` — so two volumes of one work get two citekeys. */
  def volumeOf(title: com.histmersion.domain.Title): Option[com.histmersion.domain.library.collections.ImportTarget.Volume] =
    VolumeText
      .findFirstMatchIn(title.value)
      .flatMap(m => m.group(1).toIntOption.map(n => Volume(n)).orElse(roman(RomanNumeral(m.group(1)))))
      .filter(_.value > 0)

  private def roman(s: com.histmersion.domain.library.collections.ImportTarget.RomanNumeral): Option[com.histmersion.domain.library.collections.ImportTarget.Volume] =
    val v = Map('i' -> 1, 'v' -> 5, 'x' -> 10, 'l' -> 50, 'c' -> 100)
    val ds = s.value.toLowerCase.toList.map(v)
    Option.when(ds.nonEmpty)(Volume(ds.zipAll(ds.drop(1), 0, 0).map((a, b) => if a < b then -a else a).sum))

  /** The citekey an import generates: the library's convention plus `_v<n>` for a numbered volume. A key the library
    * would refuse is a problem, not a record.
    */
  def citekeyFor(item: CollectionItem): Either[ImportTarget.Problem, com.histmersion.domain.library.Citekey] =
    val title = item.title.fold("")(_.value)
    val generated = Acquire
      .citekey(
        item.authors.map(a => com.histmersion.api.library.AuthorName(a.value)),
        item.year.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
        Some(com.histmersion.api.library.Title(title))
      )
      .value + volumeOf(com.histmersion.domain.Title(title)).fold("")(n => s"_v$n")
    com.histmersion.domain.library.Citekey
      .either(generated)
      .left
      .map(_ => Problem(s"the generated citekey '$generated' is not a valid citekey — pass --citekey <key>"))

  /** Whether importing entry `id` under `ck` may go ahead: two volumes once generated the same citekey and were merged
    * into one record.
    *
    * @param explicit
    *   the citekey was given, so landing on an existing record is deliberate
    * @param existing
    *   `None` when no record has `ck`; otherwise that record's Calibre id
    */
  def of(ck: com.histmersion.domain.library.Citekey, id: com.histmersion.domain.library.collections.ImportTarget.Id, explicit: com.histmersion.domain.library.collections.ImportTarget.OfExplicit, existing: Option[Option[ImportTarget.Previous]]): Either[ImportTarget.Problem, ImportTarget] =
    existing match
      case None                                             => Right(Create)
      case Some(Some(linked)) if linked.value == id.value => Right(Update(None))
      case Some(linked) if explicit.value                   => Right(Update(linked))
      case Some(linked)                                     =>
        val what = linked.fold("a record that is not linked to Calibre")(l => s"the record of Calibre id $l")
        Left(
          Problem(
            s"the generated citekey '$ck' is already $what — importing Calibre id $id would merge two " +
              s"works into one record. Pass --citekey <another key> for a separate record, or --citekey $ck " +
              "to import into that record on purpose"
          )
        )

  /** The summary an import writes: Calibre's description only fills an empty one, so a re-import never replaces a
    * summary an agent compiled. `None` leaves the record's summary alone.
    */
  def summary(fromCollection: Option[com.histmersion.api.library.Source.Summary], current: Option[com.histmersion.api.library.Source.Summary]): Option[com.histmersion.api.library.Source.Summary] =
    if current.exists(_.value.trim.nonEmpty) then None else fromCollection
