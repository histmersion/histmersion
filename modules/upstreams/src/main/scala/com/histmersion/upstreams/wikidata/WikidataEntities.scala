package com.histmersion
package upstreams.wikidata
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.{External, Problem}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** Wikidata's `wbgetentities` (props=claims): the URL of a batch of items and their picture statements. Read by the
  * media collections and by the host's label lookup alike. Pure.
  */
@atBoundary(BoundaryKind.Json, "reads Wikidata entity JSON")
object WikidataEntities:
  type Api = Api.T
  object Api extends Newtype[String, Pure]
  type Maxlag = Maxlag.T
  object Maxlag extends Newtype[Int, Pure]

  /** The Commons category property. */
  val CommonsCategory = "P373"

  private def enc(s: String): String = (URLEncoder.encode((s), UTF_8))

  /** Pure: the `wbgetentities` URL for up to the API's batch of ids. `maxlag` 0 = not sent. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def url(api: Api, ids: List[WikidataItem.Id], maxlag: Maxlag): RepositoryLanding.Candidate.Url =
    val lag = if (maxlag).value > 0 then s"&maxlag=$maxlag" else ""
    RepositoryLanding.Candidate.Url(
      s"$api?action=wbgetentities&format=json&formatversion=2&props=claims&ids=${enc((ids.mkString("|")))}$lag"
    )

  /** Pure: the items of a `wbgetentities` answer, keyed by the id ASKED for (a redirected item answers under the id
    * that was asked, with the statements of its target). `props` are the picture properties to keep; P373 is always
    * read. An API error is a [[Problem.Upstream.BadReply]] at `asked`, the URL `json` answered, reporting `code: info`.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def items(
      json: Json,
      props: Set[WikidataStatement.Property],
      asked: Problem.Upstream.Url
  ): Either[Problem, Map[WikidataItem.Id, WikidataItem]] =
    val c = json.cursor
    c.downField("error").focus match
      case Some(err) =>
        val code = err.cursor.get[String]("code").getOrElse("error")
        Left(
          Problem.Upstream.BadReply.here(
            Problem.Upstream.Name("wikidata"),
            asked,
            Problem.Upstream.Shape.Json,
            External.Reported.of(s"$code: ${err.cursor.get[String]("info").getOrElse("")}".trim)
          )
        )
      case None =>
        val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
        Right(ents.flatMap { (key, ent) =>
          val e = ent.cursor
          if e.downField("missing").focus.isDefined then None
          else
            val claims = e.downField("claims").focus.flatMap(_.asObject)
            def statementsOf(p: WikidataStatement.Property): List[WikidataStatement] =
              claims.flatMap(_((p).value)).flatMap(_.asArray).toList.flatten.zipWithIndex.flatMap { (st, i) =>
                val s = st.cursor
                val rank = s.get[String]("rank").getOrElse("normal")
                val snak = s.downField("mainsnak")
                val v = snak.downField("datavalue").get[String]("value").toOption.map(_.trim).filter(_.nonEmpty)
                if rank == "deprecated" || snak.get[String]("snaktype").toOption.exists(_ != "value") then None
                else
                  v.map(f =>
                    WikidataStatement(
                      p,
                      WikidataStatement.File(f),
                      WikidataStatement.Preferred(rank == "preferred"),
                      WikidataStatement.Order(i)
                    )
                  )
              }
            val pictures = props.toList.sorted.flatMap(statementsOf)
            val category = WikidataStatement
              .best(statementsOf(WikidataStatement.Property(CommonsCategory)), WikidataStatement.BestPer(1))
              .headOption
              .map(st => WikidataItem.CommonsCategory(st.file.value))
            val id = WikidataItem.Id(key)
            Some(id -> WikidataItem(id, pictures, category))
        }.toMap)
