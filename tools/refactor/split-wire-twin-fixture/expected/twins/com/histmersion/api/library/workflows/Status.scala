package com.histmersion
package api.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec

enum Status derives JsonCodec:
  case Open, Closed
