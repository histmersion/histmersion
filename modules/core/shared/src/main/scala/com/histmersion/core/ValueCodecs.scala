package com.histmersion.core

import com.histmersion.values.{Citekey, Doi, Latitude, Longitude, WikidataId, Year}
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

/** The wire instances of the newtypes: each maps its base type's, and a decoder refuses what the newtype's
  * constraint does not admit. */
object ValueCodecs:
  given Encoder[Citekey] = Encoder.encodeString.contramap(_.value)
  given Decoder[Citekey] = Decoder.decodeString.emap(Citekey.parse)
  given Schema[Citekey]  = Schema.string[Citekey]

  given Encoder[Year] = Encoder.encodeInt.contramap(_.value)
  given Decoder[Year] = Decoder.decodeInt.emap(Year.either)
  given Schema[Year]  = Schema.schemaForInt.as[Year]

  given Encoder[Doi] = Encoder.encodeString.contramap(_.value)
  given Decoder[Doi] = Decoder.decodeString.emap(Doi.either)
  given Schema[Doi]  = Schema.string[Doi]

  given Encoder[WikidataId] = Encoder.encodeString.contramap(_.value)
  given Decoder[WikidataId] = Decoder.decodeString.emap(WikidataId.either)
  given Schema[WikidataId]  = Schema.string[WikidataId]

  given Encoder[Latitude] = Encoder.encodeDouble.contramap(_.value)
  given Decoder[Latitude] = Decoder.decodeDouble.emap(Latitude.either)
  given Schema[Latitude]  = Schema.schemaForDouble.as[Latitude]

  given Encoder[Longitude] = Encoder.encodeDouble.contramap(_.value)
  given Decoder[Longitude] = Decoder.decodeDouble.emap(Longitude.either)
  given Schema[Longitude]  = Schema.schemaForDouble.as[Longitude]
