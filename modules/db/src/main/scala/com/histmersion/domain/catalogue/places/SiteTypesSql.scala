package com.histmersion
package domain.catalogue.places

import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.domain.catalogue.Place
import com.histmersion.util.database.joined
import com.augustnagro.magnum.{sql, Frag, SqlLiteral}
import com.histmersion.lint.{LintRule, tolerated}

/** [[SiteTypes.of]] in SQL, over a `places` row aliased `a` (a site or a feature): the stored type, else the Pleiades
  * feature types (the most specific by [[SiteTypes]]' preference), else the GeoNames feature code, else the Wikidata
  * item's own P31 classes (`properties.p31`, the most specific), else the Wikidata import's class — generated from the
  * same tables, so the map's site-type filter and the type a site is drawn with cannot disagree. `SiteTypesSqlSuite`
  * holds it to [[SiteTypes.of]].
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object SiteTypesSql:

  /** A word of [[SiteTypes]]' own tables (a type id, a Pleiades type, a GeoNames code, a Wikidata class, a place kind)
    * as an SQL string literal, its quotes doubled. The ONE place the expression writes text: the tables are the code's
    * constants, a few hundred words, written in so a map tile's statement stays one plan (bound, each tile query would
    * carry them all as parameters).
    */
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  private def quote(s: String): Frag = sql"${SqlLiteral("'" + s.replace("'", "''") + "'")}"
  private def list(xs: Iterable[String]): Frag = xs.map(quote).toSeq.joined(sql", ")

  /** The preference [[SiteTypes]] picks a type by when a place lists several. */
  private val preference: List[SiteTypes.SiteType.Id] = SiteTypes.preference

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def expr(a: Frag): Frag =
    val stored = sql"CASE WHEN $a.site_type IN (${list(SiteTypes.all.map(_.id.value))}) THEN $a.site_type END"
    // the feature types as a list: lower case, each without its `-2` variant suffix
    val types =
      sql"regexp_split_to_array(btrim(regexp_replace(lower($a.properties->>'feature_types'), '-[0-9]+\\s*(,|$$)', '\\1', 'g')), '\\s*,\\s*')"
    val byType = SiteTypes.pleiadesTypes.toList.groupMap(_._2)(_._1)
    val pleiadesWhens = preference.flatMap(t =>
      byType.get(t).map(ks => sql"WHEN ft.v && ARRAY[${list(ks.map(_.value).sorted)}] THEN ${quote(t.value)}")
    )
    val pleiades = sql"(SELECT CASE ${pleiadesWhens.joined(sql" ")} END FROM (SELECT $types AS v) ft)"
    val fcode = sql"upper(btrim($a.properties->>'fcode'))"
    val geonamesWhens = SiteTypes.geonamesCodes.toList
      .sortBy(_._1.value)
      .map((c, t) => sql"WHEN ${quote(c.value)} THEN ${quote(t.value)}")
    val geonames =
      sql"CASE $fcode ${geonamesWhens.joined(sql" ")} ELSE CASE WHEN $fcode LIKE 'P.%' THEN ${quote(SiteTypes.Settlement)} END END"
    // the item's own P31 classes: a JSON array in `properties.p31` (anything else counts as none)
    val p31 =
      sql"ARRAY(SELECT btrim(x) FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof($a.properties->'p31') = 'array' THEN $a.properties->'p31' ELSE '[]'::jsonb END) x)"
    val byClass = SiteTypes.wikidataClasses.toList.groupMap(_._2)(_._1)
    val ownWhens = preference.flatMap(t =>
      byClass.get(t).map(ks => sql"WHEN w.v && ARRAY[${list(ks.map(_.value).sorted)}] THEN ${quote(t.value)}")
    )
    val own = sql"(SELECT CASE ${ownWhens.joined(sql" ")} END FROM (SELECT $p31 AS v) w)"
    val wikidataWhens = SiteTypes.wikidataClasses.toList
      .sortBy(_._1.value)
      .map((c, t) => sql"WHEN ${quote(c.value)} THEN ${quote(t.value)}")
    val wikidata = sql"CASE btrim($a.properties->>'class') ${wikidataWhens.joined(sql" ")} END"
    sql"(CASE WHEN $a.kind IN (${quote(Place.Kind.Site)}, ${quote(Place.Kind.Feature)}) THEN coalesce($stored, $pleiades, $geonames, $own, $wikidata) END)"
