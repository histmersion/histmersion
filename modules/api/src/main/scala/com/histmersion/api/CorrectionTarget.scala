package com.histmersion.api

import io.circe.syntax.*

/** CORRECTIONS BY HAND that survive a re-import (V088, the data-source assessment's §B.4): a person changed one FIELD
  * of something an importer wrote, and the next import must neither lose the change nor keep it blindly. Each
  * correction records the person's value AND what upstream said when it was made; on every refresh
  * [[Corrections.reconcile]] compares that with what upstream says now:
  *
  * | upstream now says             | outcome                                           |
  * |:------------------------------|:--------------------------------------------------|
  * | what it said then (unchanged) | keep applying the correction (`active`)           |
  * | the person's value            | retire it (`agreed`): upstream adopted the change |
  * | anything else                 | keep applying it, flagged `conflict` for review   |
  *
  * Generic over the TARGET (`event` now; `place` is the next one — `place_overrides` is meant to become its
  * canonical-target form): a correction names its target kind, its id and the field, and values are JSON, compared as
  * JSON.
  */
object CorrectionTarget:
  val Event = "event"
  val Place = "place"
  val all: List[String] = List(Event, Place)
