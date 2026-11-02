package com.histmersion.api.research
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum ArticleKind:
  case Concept, Myth, Culture, Region, Timeline, Report, Index

object ArticleKind:
  def fromString(s: String): Option[ArticleKind] =
    values.find(_.toString.equalsIgnoreCase(s))

  given WireEnumValues[ArticleKind]:
    def all: Array[ArticleKind] = ArticleKind.values
    def wire(e: ArticleKind): String = e.toString.toLowerCase
    def fromWire(s: String): Option[ArticleKind] =
      ArticleKind.values.find(_.toString.equalsIgnoreCase(s))

  given Encoder[ArticleKind] = WireEnum.encoder
  given Decoder[ArticleKind] = WireEnum.decoder
  given Schema[ArticleKind] = WireEnum.schema
