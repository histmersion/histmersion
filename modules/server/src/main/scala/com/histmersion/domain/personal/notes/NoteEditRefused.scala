package com.histmersion.domain.personal.notes

import com.histmersion.domain.personal.MarkTime

/** Why a note's text was not changed: there is no such note, or it changed since the person read it. */
enum NoteEditRefused:
  case Missing
  case Changed(now: MarkTime)
