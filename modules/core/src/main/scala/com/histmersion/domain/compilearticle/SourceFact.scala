package com.histmersion
package domain.compilearticle

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.library.Source
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.Year
import com.histmersion.domain.Title

final case class SourceFact(citekey: Citekey, title: Title, year: Option[Year], status: Source.Status) derives JsonCodec
