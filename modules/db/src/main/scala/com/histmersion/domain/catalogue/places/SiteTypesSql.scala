package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.domain.catalogue.Place

/** [[SiteTypes.of]] in SQL, over a `places` row aliased `a` (a site or a feature): the stored type, else the Pleiades
  * feature types (the most specific by [[SiteTypes]]' preference), else the GeoNames feature code, else the Wikidata
  * item's own P31 classes (`properties.p31`, the most specific), else the Wikidata import's class — generated from the
  * same tables, so the map's site-type filter and the type a site is drawn with cannot disagree. `SiteTypesSqlSuite`
  * holds it to [[SiteTypes.of]].
  */
object SiteTypesSql:

  type QuoteResult = QuoteResult.T
  object QuoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuoteS = QuoteS.T
  object QuoteS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListXs = ListXs.T
  object ListXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Preference = Preference.T
  object Preference extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExprResult = ExprResult.T
  object ExprResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExprA = ExprA.T
  object ExprA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def quote(s: com.histmersion.domain.catalogue.places.SiteTypesSql.QuoteS): com.histmersion.domain.catalogue.places.SiteTypesSql.QuoteResult = "'" + s.replace("'", "''") + "'"
  private def list(xs: Iterable[com.histmersion.domain.catalogue.places.SiteTypesSql.ListXs]): com.histmersion.domain.catalogue.places.SiteTypesSql.ListResult = xs.map(quote).mkString(", ")

  /** The preference [[SiteTypes]] picks a type by when a place lists several. */
  private val preference: List[com.histmersion.domain.catalogue.places.SiteTypesSql.Preference] = SiteTypes.preference

  def expr(a: com.histmersion.domain.catalogue.places.SiteTypesSql.ExprA): com.histmersion.domain.catalogue.places.SiteTypesSql.ExprResult =
    val stored = s"CASE WHEN $a.site_type IN (${list(SiteTypes.all.map(_.id))}) THEN $a.site_type END"
    // the feature types as a list: lower case, each without its `-2` variant suffix
    val types = s"regexp_split_to_array(btrim(regexp_replace(lower($a.properties->>'feature_types'), " +
      "'-[0-9]+\\s*(,|$)', '\\1', 'g')), '\\s*,\\s*')"
    val byType = SiteTypes.pleiadesTypes.toList.groupMap(_._2)(_._1)
    val pleiades = preference
      .flatMap(t => byType.get(t).map(ks => s"WHEN ft.v && ARRAY[${list(ks.sorted)}] THEN ${quote(t)}"))
      .mkString(s"(SELECT CASE ", " ", s" END FROM (SELECT $types AS v) ft)")
    val fcode = s"upper(btrim($a.properties->>'fcode'))"
    val geonames = SiteTypes.geonamesCodes.toList.sorted
      .map((c, t) => s"WHEN ${quote(c)} THEN ${quote(t)}")
      .mkString(s"CASE $fcode ", " ", s" ELSE CASE WHEN $fcode LIKE 'P.%' THEN ${quote(SiteTypes.Settlement)} END END")
    // the item's own P31 classes: a JSON array in `properties.p31` (anything else counts as none)
    val p31 = s"ARRAY(SELECT btrim(x) FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof($a.properties->'p31') = " +
      "'array' THEN " + s"$a.properties->'p31' ELSE '[]'::jsonb END) x)"
    val byClass = SiteTypes.wikidataClasses.toList.groupMap(_._2)(_._1)
    val own = preference
      .flatMap(t => byClass.get(t).map(ks => s"WHEN w.v && ARRAY[${list(ks.sorted)}] THEN ${quote(t)}"))
      .mkString(s"(SELECT CASE ", " ", s" END FROM (SELECT $p31 AS v) w)")
    val wikidata = SiteTypes.wikidataClasses.toList.sorted
      .map((c, t) => s"WHEN ${quote(c)} THEN ${quote(t)}")
      .mkString(s"CASE btrim($a.properties->>'class') ", " ", " END")
    s"(CASE WHEN $a.kind IN (${quote(Place.Kind.Site)}, ${quote(Place.Kind.Feature)}) THEN " +
      s"coalesce($stored, $pleiades, $geonames, $own, $wikidata) END)"
