package com.histmersion.domain.personal

import com.histmersion.api.personal.ItemKind
import com.histmersion.db.{Param, Sql}

import com.augustnagro.magnum.*

/** How a bookmark's, a note's or a read mark's row (`r`) is joined to its item, for the item's key and title as they
  * are now: by the item's uid where its table has one (a renamed source keeps its bookmarks), by its id for a place.
  * Takes the connection it runs on.
  */
private[personal] object ItemTitles:

  private val Iso = """'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'"""
  def iso(col: String): String = s"to_char($col AT TIME ZONE 'UTC', $Iso)"

  def joins(r: String): String =
    s""" LEFT JOIN sources ts ON $r.target_kind = 'source' AND ts.uid = $r.target_uid
         LEFT JOIN articles ta ON $r.target_kind = 'article' AND ta.uid = $r.target_uid
         LEFT JOIN events te ON $r.target_kind = 'event' AND te.uid = $r.target_uid
         LEFT JOIN goals tg ON $r.target_kind = 'goal' AND tg.uid = $r.target_uid
         LEFT JOIN places tp ON $r.target_kind = 'place' AND tp.id = $r.target_id """

  def key(r: String): String = s"coalesce(ts.citekey, ta.slug, te.id, tg.slug, $r.target_id)"
  def title(r: String): String = s"coalesce(ts.title, ta.title, te.label, tg.title, tp.label, $r.target_label)"

  /** The table and key column of the items that have a uid. */
  private val Keyed: Map[ItemKind, (String, String)] = Map(
    ItemKind.Source -> ("sources", "citekey"),
    ItemKind.Article -> ("articles", "slug"),
    ItemKind.Event -> ("events", "id"),
    ItemKind.Goal -> ("goals", "slug")
  )

  /** The item's uid, when the collection holds it. */
  def uidOf(kind: ItemKind, key: Option[String])(using DbCon): Option[String] =
    for
      k <- key
      (tb, col) <- Keyed.get(kind)
      uid <- Sql.of(s"SELECT uid::text FROM $tb WHERE $col = ?", Param.S(k)).frag.query[String].run().headOption
    yield uid

  /** `AND` every word in `texts` (case ignored). */
  def wordsSql(words: List[String], texts: String): Sql =
    words.foldLeft(Sql.empty)((acc, w) => acc ++ Sql.of(s" AND position(? in lower($texts)) > 0", Param.S(w)))
