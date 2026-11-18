package com.histmersion
package api
package problem

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.json.JsonCodec
import json.NewtypeCodecs.given
import util.problem.Problem.Content

/** The derived codec of the Content group's cases, as [[Answer]] reads and writes them: in a class of its own, since
  * the group's vocabularies (`Requirement`, `PanelRule`) and the other groups' codecs no longer fit one class file
  * together.
  */
private[problem] object ContentCodec:

  /** Code words as strings (`"Fixed"`), as the other groups' codecs write them. */
  lazy val content: JsonValueCodec[Content] = JsonCodec.derivedWords[Content]
