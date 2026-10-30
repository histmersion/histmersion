package com.histmersion.domain

import io.circe.Json

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** Wikidata, read through `wbgetentities` (props=claims). Pure parsing and URL building; the fetch is the server's.
  */
object Wikidata:

  /** A picture statement of an item: the property (`P18`), the Commons file name it gives, whether it is the item's
    * preferred one, and its order among the item's statements of that property.
    */
  final case class Statement(property: String, file: String, preferred: Boolean, order: Int)

  /** An item's picture statements and its Commons category (P373). */
  final case class Item(id: String, statements: List[Statement], commonsCategory: Option[String])

  val CommonsCategory = "P373"

  def isQid(s: String): Boolean = s.matches("Q[1-9][0-9]*")

  /** Pure: the item a gazetteer place id names (`wikidata_q132564` → `Q132564`). */
  def qidOfPlaceId(id: String): Option[String] =
    val s = id.trim.toLowerCase(Locale.ROOT)
    Option.when(s.startsWith("wikidata_q"))("Q" + s.drop("wikidata_q".length)).filter(isQid)

  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  /** Pure: the `wbgetentities` URL for up to the API's batch of ids. `maxlag` 0 = not sent. */
  def entitiesUrl(api: String, ids: List[String], maxlag: Int): String =
    val lag = if maxlag > 0 then s"&maxlag=$maxlag" else ""
    s"$api?action=wbgetentities&format=json&formatversion=2&props=claims&ids=${enc(ids.mkString("|"))}$lag"

  /** Pure: the items of a `wbgetentities` answer, keyed by the id ASKED for (a redirected item answers under the id
    * that was asked, with the statements of its target). `props` are the picture properties to keep; P373 is always
    * read. An API error is `Left("code: info")`.
    */
  def items(json: Json, props: Set[String]): Either[String, Map[String, Item]] =
    val c = json.hcursor
    c.downField("error").focus match
      case Some(err) =>
        val code = err.hcursor.get[String]("code").getOrElse("error")
        Left(s"$code: ${err.hcursor.get[String]("info").getOrElse("")}".trim)
      case None =>
        val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
        Right(ents.flatMap { (key, ent) =>
          val e = ent.hcursor
          if e.downField("missing").focus.isDefined then None
          else
            val claims = e.downField("claims").focus.flatMap(_.asObject)
            def statementsOf(p: String): List[Statement] =
              claims.flatMap(_(p)).flatMap(_.asArray).toList.flatten.zipWithIndex.flatMap { (st, i) =>
                val s = st.hcursor
                val rank = s.get[String]("rank").getOrElse("normal")
                val snak = s.downField("mainsnak")
                val v = snak.downField("datavalue").get[String]("value").toOption.map(_.trim).filter(_.nonEmpty)
                if rank == "deprecated" || snak.get[String]("snaktype").toOption.exists(_ != "value") then None
                else v.map(Statement(p, _, rank == "preferred", i))
              }
            val pictures = props.toList.sorted.flatMap(statementsOf)
            val category = best(statementsOf(CommonsCategory), 1).headOption.map(_.file)
            Some(key -> Item(key, pictures, category))
        }.toMap)

  /** Pure: the statements of one property to use, best first — the preferred ones when there are any (Wikidata's own
    * rule for "the" value), else the normal ones — at most `per`.
    */
  def best(statements: List[Statement], per: Int): List[Statement] =
    val preferred = statements.filter(_.preferred)
    (if preferred.nonEmpty then preferred else statements).sortBy(_.order).take(per.max(0))
