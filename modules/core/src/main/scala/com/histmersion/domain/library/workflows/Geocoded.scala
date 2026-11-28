package com.histmersion
package domain.library.workflows

import com.histmersion.api.library.Finding

final case class Geocoded(findings: List[Finding.Input], choices: List[GeocodeChoice])
