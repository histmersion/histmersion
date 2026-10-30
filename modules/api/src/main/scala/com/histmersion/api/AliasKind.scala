package com.histmersion.api

import com.histmersion.values.BundleKind

/** The kinds of record a rename can give a new identifier, keeping the old one as an ALIAS (#1799): a source's citekey
  * (V027), an article's slug (V042), an event's id and a goal's slug (V053), a gazetteer place's id (V055).
  */
enum AliasKind(val noun: String, val idWord: String):
  case Source extends AliasKind("source", "citekey")
  case Article extends AliasKind("article", "slug")
  case Event extends AliasKind("event", "id")
  case Goal extends AliasKind("goal", "slug")
  case Place extends AliasKind("place", "id")

object AliasKind:
  def of(b: BundleKind): Option[AliasKind] = b match
    case BundleKind.Article => Some(Article)
    case BundleKind.Event   => Some(Event)
    case BundleKind.Goal    => Some(Goal)
    case BundleKind.Place   => Some(Place)
    case _                  => None
