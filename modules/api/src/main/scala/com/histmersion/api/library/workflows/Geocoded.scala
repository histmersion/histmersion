package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Finding
import com.histmersion.util.json.JsonCodec

final case class Geocoded(findings: List[Finding.Input], choices: List[PlaceChoice]) derives JsonCodec
