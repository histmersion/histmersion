package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.util.json.JsonCodec

final case class IngestedWork(doi: Doi, citekey: Citekey) derives JsonCodec
