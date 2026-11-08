package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class WikirefsResult(
    article: WikirefsResult.Article,
    url: WikirefsResult.Url,
    doiCount: WikirefsResult.DoiCount,
    dois: List[WikirefsResult.Dois],
    arxiv: List[WikirefsResult.Arxiv],
    known: List[WikirefsResult.Known] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object WikirefsResult:
  type Article = Article.T
  object Article extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type DoiCount = DoiCount.T
  object DoiCount extends Newtype[Int, Pure]
  type Dois = Dois.T
  object Dois extends Newtype[String, Pure]
  export Source.Candidate.ArxivId as Arxiv
  type Known = Known.T
  object Known extends Newtype[String, Pure]
