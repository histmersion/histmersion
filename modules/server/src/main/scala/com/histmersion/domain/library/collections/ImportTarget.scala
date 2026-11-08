package com.histmersion
package domain.library.collections

import com.histmersion.domain.Problem
import com.histmersion.api.library.Acquire
import com.histmersion.plugin.CollectionItem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Title
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Source

/** Where an import writes, once the citekey is known. */
enum ImportTarget derives CanEqual:
  /** No record has this citekey yet. */
  case Create

  /** The record exists; enrich it and link it to the imported entry. `previous` is the Calibre id it was linked to
    * before, when that was a different one.
    */
  case Update(previous: Option[ImportTarget.Previous])

object ImportTarget:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type RomanNumeral = RomanNumeral.T
  object RomanNumeral extends Newtype[String, Pure]
  type Volume = Volume.T
  object Volume extends Newtype[Int, Pure]
  type Previous = Previous.T
  object Previous extends Newtype[String, Pure]

  type OfExplicit = OfExplicit.T
  object OfExplicit extends Newtype[Boolean, Pure]

  private val VolumeText = """(?i)\b(?:volume\s*|vol\.\s*|vol\s+)([0-9]+|[ivxlc]+)\b""".r

  /** The volume a title names — `Volume I`, `Vol. 2`, `volume 3` — so two volumes of one work get two citekeys. */
  def volumeOf(title: Title): Option[Volume] =
    VolumeText
      .findFirstMatchIn(title.value)
      .flatMap(m => m.group(1).toIntOption.map(n => Volume(n)).orElse(roman(RomanNumeral(m.group(1)))))
      .filter(_.value > 0)

  private def roman(s: RomanNumeral): Option[Volume] =
    val v = Map('i' -> 1, 'v' -> 5, 'x' -> 10, 'l' -> 50, 'c' -> 100)
    val ds = s.value.toLowerCase.toList.map(v)
    Option.when(ds.nonEmpty)(Volume(ds.zipAll(ds.drop(1), 0, 0).map((a, b) => if a < b then -a else a).sum))

  /** The citekey an import generates: the library's convention plus `_v<n>` for a numbered volume. A key the library
    * would refuse is a problem, not a record.
    */
  def citekeyFor(item: CollectionItem): Either[Problem, Citekey] =
    val title = item.title.fold("")(_.value)
    val generated = Acquire
      .citekey(
        item.authors.map(a => AuthorName(a.value)),
        item.year.map(y => Year(y.value.toLong)),
        Some(com.histmersion.api.library.Title(title))
      )
      .value + volumeOf(Title(title)).fold("")(n => s"_v$n")
    Citekey
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
  def of(ck: Citekey, id: Id, explicit: OfExplicit, existing: Option[Option[Previous]]): Either[Problem, ImportTarget] =
    existing match
      case None                                           => Right(Create)
      case Some(Some(linked)) if linked.value == id.value => Right(Update(None))
      case Some(linked) if explicit.value                 => Right(Update(linked))
      case Some(linked)                                   =>
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
  def summary(fromCollection: Option[Source.Summary], current: Option[Source.Summary]): Option[Source.Summary] =
    if current.exists(_.value.trim.nonEmpty) then None else fromCollection
