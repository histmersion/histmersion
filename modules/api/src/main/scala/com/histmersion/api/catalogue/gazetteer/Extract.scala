package com.histmersion
package api.catalogue.gazetteer

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.gazetteer.SourceInfo
import com.histmersion.Newtype

final case class Extract(
    name: Extract.Name,
    file: Extract.File,
    sha256: Extract.Sha256,
    bytes: Extract.Bytes,
    features: Extract.Features,
    version: Extract.Version,
    title: Extract.Title,
    licence: Extract.Licence,
    attribution: Option[Extract.Attribution] = None,
    url: Option[Extract.Url] = None,
    built: Option[Extract.Built] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Extract:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  export SourceInfo.File
  type Sha256 = Sha256.T
  object Sha256 extends Newtype[String, Pure]
  type Bytes = Bytes.T
  object Bytes extends Newtype[Long, Pure]
  type Features = Features.T
  object Features extends Newtype[Long, Pure]
  type Version = Version.T
  object Version extends Newtype[String, Pure]
  export SourceInfo.{Licence, Title}
  type Attribution = Attribution.T
  object Attribution extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Built = Built.T
  object Built extends Newtype[String, Pure]
