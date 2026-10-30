package com.histmersion.api

import io.circe.{Codec as CirceCodec, Decoder, Encoder}

enum FacetField:
  case Tags, Authors, Places, Topics, Status, Provenance

object FacetField:
  def label(f: FacetField): String = f match
    case Tags       => "tags";
    case Authors    => "authors";
    case Places     => "places"
    case Topics     => "topics";
    case Status     => "status";
    case Provenance => "provenance"

  def parse(s: String): Option[FacetField] =
    values.find(f => label(f) == s.trim.toLowerCase || f.toString.equalsIgnoreCase(s.trim))

  val all: List[String] = values.toList.map(label)
