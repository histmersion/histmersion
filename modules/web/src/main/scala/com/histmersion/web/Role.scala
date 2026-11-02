package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.Span
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import com.histmersion.api.library.Filter

/** What a field is FOR — which fields "clear all" clears and which become chips. */
enum Role:
  /** Part of the query, shown as a removable chip (unless `chip = false`), cleared by "clear all". */
  case Filter

  /** The time window and viewport: part of the query, never a chip, not cleared by "clear all". */
  case Span

  /** UI only: the tab, the open windows, the layers. Never sent to the API. */
  case View
