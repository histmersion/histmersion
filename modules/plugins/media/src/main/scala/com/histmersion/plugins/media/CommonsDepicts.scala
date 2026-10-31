package com.histmersion.plugins.media

import io.circe.Json

/** Wikimedia Commons files that DEPICT an event: structured data's `P180 depicts` searched by CirrusSearch's documented
  * `haswbstatement` keyword through the action API (no login), and Wikidata's `P1299 depicted by` on the event's own
  * item (a work whose own image, P18, is the picture). URL building and parsing. Pure.
  */
object CommonsDepicts:

  val Depicts = "P180"
  val DepictedBy = "P1299"

  def searchUrl(api: String, qid: String, limit: Int, maxlag: Int): String =
    val lag = if maxlag > 0 then s"&maxlag=$maxlag" else ""
    s"$api?action=query&format=json&formatversion=2&list=search&srnamespace=6&srlimit=${limit.max(1)}" +
      s"&srsearch=${s"haswbstatement:$Depicts=$qid".enc}$lag"

  /** Pure: the file titles of a search answer, in its order; an API error is a `Left`. */
  def titles(json: Json): Either[String, List[String]] =
    val c = json.hcursor
    c.downField("error").focus match
      case Some(err) =>
        Left(
          s"${err.hcursor.get[String]("code").getOrElse("error")}: ${err.hcursor.get[String]("info").getOrElse("")}".trim
        )
      case None =>
        Right(
          c.downField("query")
            .downField("search")
            .focus
            .flatMap(_.asArray)
            .toList
            .flatten
            .flatMap(_.hcursor.get[String]("title").toOption)
            .filter(_.startsWith("File:"))
        )

  /** Pure: the items an entity names under `P1299 depicted by`, per entity asked, best rank first. */
  def depictedBy(json: Json): Map[String, List[String]] =
    json.hcursor
      .downField("entities")
      .focus
      .flatMap(_.asObject)
      .toList
      .flatMap(_.toList)
      .map { (key, ent) =>
        val sts = ent.hcursor.downField("claims").downField(DepictedBy).focus.flatMap(_.asArray).toList.flatten
        val ids = sts.flatMap { st =>
          val s = st.hcursor
          val rank = s.get[String]("rank").getOrElse("normal")
          Option
            .when(rank != "deprecated")(
              s.downField("mainsnak")
                .downField("datavalue")
                .downField("value")
                .get[String]("id")
                .toOption
            )
            .flatten
            .filter(_.matches("Q[1-9][0-9]*"))
            .map(id => (if rank == "preferred" then 0 else 1, id))
        }
        key -> ids.sortBy(_._1).map(_._2).distinct
      }
      .toMap
