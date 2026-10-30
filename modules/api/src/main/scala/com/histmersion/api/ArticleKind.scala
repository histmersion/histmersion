package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

// ── article kind ──────────────────────────────────────────────────────────────────────────────────

/** The kinds the compiled wiki uses. Not a constraint — the kind field is free text so a new section needs no migration
  * — but the vocabulary the tools advertise and the CLI validates.
  */
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
