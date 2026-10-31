package com.histmersion.domain.library.collections

import com.histmersion.api.Acquire
import com.histmersion.plugin.CollectionItem

/** Where an import writes, once the citekey is known. */
enum ImportTarget:
  /** No record has this citekey yet. */
  case Create

  /** The record exists; enrich it and link it to the imported entry. `previous` is the Calibre id it was linked to
    * before, when that was a different one.
    */
  case Update(previous: Option[String])

object ImportTarget:
  private val Volume = """(?i)\b(?:volume\s*|vol\.\s*|vol\s+)([0-9]+|[ivxlc]+)\b""".r

  /** The volume a title names — `Volume I`, `Vol. 2`, `volume 3` — so two volumes of one work get two citekeys. */
  def volumeOf(title: String): Option[Int] =
    Volume.findFirstMatchIn(title).flatMap(m => m.group(1).toIntOption.orElse(roman(m.group(1)))).filter(_ > 0)

  private def roman(s: String): Option[Int] =
    val v = Map('i' -> 1, 'v' -> 5, 'x' -> 10, 'l' -> 50, 'c' -> 100)
    val ds = s.toLowerCase.toList.map(v)
    Option.when(ds.nonEmpty)(ds.zipAll(ds.drop(1), 0, 0).map((a, b) => if a < b then -a else a).sum)

  /** The citekey an import generates: the library's convention plus `_v<n>` for a numbered volume. */
  def citekeyFor(item: CollectionItem): String =
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
  def of(ck: String, id: String, explicit: Boolean, existing: Option[Option[String]]): Either[String, ImportTarget] =
    existing match
      case None                                => Right(Create)
      case Some(Some(linked)) if linked == id => Right(Update(None))
      case Some(linked) if explicit            => Right(Update(linked))
      case Some(linked)                        =>
        val what = linked.fold("a record that is not linked to Calibre")(l => s"the record of Calibre id $l")
        Left(
          s"the generated citekey '$ck' is already $what — importing Calibre id $id would merge two " +
            s"works into one record. Pass --citekey <another key> for a separate record, or --citekey $ck " +
            "to import into that record on purpose"
        )

  /** The summary an import writes: Calibre's description only fills an empty one, so a re-import never replaces a
    * summary an agent compiled. `None` leaves the record's summary alone.
    */
  def summary(fromCollection: Option[String], current: Option[String]): Option[String] =
    if current.exists(_.trim.nonEmpty) then None else fromCollection
