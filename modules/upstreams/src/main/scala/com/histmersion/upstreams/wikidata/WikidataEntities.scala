package com.histmersion.upstreams.wikidata
import io.circe.Json

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/** Wikidata's `wbgetentities` (props=claims): the URL of a batch of items and their picture statements. Read by the
  * media collections and by the host's label lookup alike. Pure.
  */
object WikidataEntities:

  /** The Commons category property. */
  type ItemsStatementsOfP = ItemsStatementsOfP.T
  object ItemsStatementsOfP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type EncResult = EncResult.T
  object EncResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncS = EncS.T
  object EncS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlResult = UrlResult.T
  object UrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlApi = UrlApi.T
  object UrlApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlIds = UrlIds.T
  object UrlIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlMaxlag = UrlMaxlag.T
  object UrlMaxlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsResultPart1 = ItemsResultPart1.T
  object ItemsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsResultPart2Part1 = ItemsResultPart2Part1.T
  object ItemsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsProps = ItemsProps.T
  object ItemsProps extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)

  val CommonsCategory = "P373"

  private def enc(s: com.histmersion.upstreams.wikidata.WikidataEntities.EncS): com.histmersion.upstreams.wikidata.WikidataEntities.EncResult = _root_.com.histmersion.upstreams.wikidata.WikidataEntities.EncResult(URLEncoder.encode((s).value, UTF_8))

  /** Pure: the `wbgetentities` URL for up to the API's batch of ids. `maxlag` 0 = not sent. */
  def url(api: com.histmersion.upstreams.wikidata.WikidataEntities.UrlApi, ids: List[com.histmersion.upstreams.wikidata.WikidataEntities.UrlIds], maxlag: com.histmersion.upstreams.wikidata.WikidataEntities.UrlMaxlag): com.histmersion.upstreams.wikidata.WikidataEntities.UrlResult =
    val lag = if (maxlag).value > 0 then s"&maxlag=$maxlag" else ""
    _root_.com.histmersion.upstreams.wikidata.WikidataEntities.UrlResult(s"$api?action=wbgetentities&format=json&formatversion=2&props=claims&ids=${enc(_root_.com.histmersion.upstreams.wikidata.WikidataEntities.EncS(ids.mkString("|")))}$lag")

  /** Pure: the items of a `wbgetentities` answer, keyed by the id ASKED for (a redirected item answers under the id
    * that was asked, with the statements of its target). `props` are the picture properties to keep; P373 is always
    * read. An API error is `Left("code: info")`.
    */
  def items(json: Json, props: Set[com.histmersion.upstreams.wikidata.WikidataEntities.ItemsProps]): Either[com.histmersion.upstreams.wikidata.WikidataEntities.ItemsResultPart1, Map[com.histmersion.upstreams.wikidata.WikidataEntities.ItemsResultPart2Part1, WikidataItem]] =
    val c = json.hcursor
    c.downField("error").focus match
      case Some(err) =>
        val code = err.hcursor.get[String]("code").getOrElse("error")
        Left(_root_.com.histmersion.upstreams.wikidata.WikidataEntities.ItemsResultPart1(s"$code: ${err.hcursor.get[String]("info").getOrElse("")}".trim))
      case None =>
        val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
        Right(ents.flatMap { (key, ent) =>
          val e = ent.hcursor
          if e.downField("missing").focus.isDefined then None
          else
            val claims = e.downField("claims").focus.flatMap(_.asObject)
            def statementsOf(p: com.histmersion.upstreams.wikidata.WikidataEntities.ItemsStatementsOfP): List[WikidataStatement] =
              claims.flatMap(_((p).value)).flatMap(_.asArray).toList.flatten.zipWithIndex.flatMap { (st, i) =>
                val s = st.hcursor
                val rank = s.get[String]("rank").getOrElse("normal")
                val snak = s.downField("mainsnak")
                val v = snak.downField("datavalue").get[String]("value").toOption.map(_.trim).filter(_.nonEmpty)
                if rank == "deprecated" || snak.get[String]("snaktype").toOption.exists(_ != "value") then None
                else v.map(WikidataStatement(p, _root_.com.histmersion.upstreams.wikidata.WikidataStatement.File(_), _root_.com.histmersion.upstreams.wikidata.WikidataStatement.Preferred(rank == "preferred"), _root_.com.histmersion.upstreams.wikidata.WikidataStatement.Order(i)))
              }
            val pictures = props.toList.sorted.flatMap(statementsOf)
            val category = WikidataStatement.best(statementsOf(_root_.com.histmersion.upstreams.wikidata.WikidataEntities.ItemsStatementsOfP(CommonsCategory)), _root_.com.histmersion.upstreams.wikidata.WikidataStatement.BestPer(1)).headOption.map(_.file)
            Some(key -> WikidataItem(_root_.com.histmersion.upstreams.wikidata.WikidataItem.Id(key), pictures, category))
        }.toMap)
