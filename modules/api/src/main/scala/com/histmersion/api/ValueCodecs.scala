package com.histmersion.api

import com.histmersion.domain.library.{Citekey, Doi}
import com.histmersion.{Latitude, Longitude, Year}
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.domain.catalogue.places.Wikidata

object ValueCodecs:
  given Encoder[Citekey] = Encoder.encodeString.contramap(_.value)
  given Decoder[Citekey] = Decoder.decodeString.emap(Citekey.parse)
  given Schema[Citekey] = Schema.string[Citekey]

  given Encoder[Year] = Encoder.encodeInt.contramap(_.value)
  given Decoder[Year] = Decoder.decodeInt.emap(Year.either)
  given Schema[Year] = Schema.schemaForInt.as[Year]

  given Encoder[Doi] = Encoder.encodeString.contramap(_.value)
  given Decoder[Doi] = Decoder.decodeString.emap(Doi.either)
  given Schema[Doi] = Schema.string[Doi]

  given given_Encoder_WikidataId: Encoder[Wikidata.Id] = Encoder.encodeString.contramap(_.value)
  given given_Decoder_WikidataId: Decoder[Wikidata.Id] = Decoder.decodeString.emap(Wikidata.Id.either)
  given given_Schema_WikidataId: Schema[Wikidata.Id] = Schema.string[Wikidata.Id]

  given Encoder[Latitude] = Encoder.encodeDouble.contramap(_.value)
  given Decoder[Latitude] = Decoder.decodeDouble.emap(Latitude.either)
  given Schema[Latitude] = Schema.schemaForDouble.as[Latitude]

  given Encoder[Longitude] = Encoder.encodeDouble.contramap(_.value)
  given Decoder[Longitude] = Decoder.decodeDouble.emap(Longitude.either)
  given Schema[Longitude] = Schema.schemaForDouble.as[Longitude]
