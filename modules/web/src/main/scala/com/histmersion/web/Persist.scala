package com.histmersion
package web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

/** Where a field survives a reload. */
enum Persist derives CanEqual:
  /** In the URL hash: shareable, and walked by back/forward. */
  case Hash

  /** In the browser's storage: a per-browser preference, not part of a shared link. */
  case Local
