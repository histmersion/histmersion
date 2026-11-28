package com.histmersion
package api.catalogue.gazetteer

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.catalogue.gazetteer.Extract

final case class Manifest(extracts: List[Extract]) derives JsonCodec, KindlingsSchema, api.json.Schema
