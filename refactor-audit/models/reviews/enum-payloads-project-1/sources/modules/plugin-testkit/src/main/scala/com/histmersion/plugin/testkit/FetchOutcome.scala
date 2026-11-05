package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

import java.nio.file.Path

/** What the acquisition chain made of one work, after the server's own checks of each answer. */
enum FetchOutcome:
  /** A copy: the fetcher that found it, the file as the server would store it, and where the harness wrote it when
    * asked to.
    */
  case Found(fetcher: Found.Fetcher, file: FetchedFile, savedTo: Option[Path])

  /** No fetcher had a copy; each one's reason, as the ingest report lists them. */
  case Miss(why: Miss.Why)

  /** A fetcher knows of a copy it may not take (a lending-only item, a bot challenge, no open licence). */
  case Refused(why: Refused.Why)

object FetchOutcome:
  object Found:
    type Fetcher = Fetcher.T
    object Fetcher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Miss:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Refused:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
