package com.histmersion
package domain.compilearticle

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.api.given
import com.histmersion.api.library.Source
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.domain.research.Slug

final case class Corpus(
    isCitekey: Citekey => Boolean,
    isTarget: Slug => Boolean,
    statusOf: Citekey => Option[Source.Status]
)
