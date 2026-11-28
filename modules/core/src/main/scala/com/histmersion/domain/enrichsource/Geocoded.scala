package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.library.Finding

final case class Geocoded(findings: List[Finding.Input], choices: List[GeocodeChoice]) derives JsonCodec
