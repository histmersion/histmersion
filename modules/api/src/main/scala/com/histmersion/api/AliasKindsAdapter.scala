package com.histmersion.api

import com.histmersion.util.identity.{AliasKind, KeyWord, RecordNoun}
import com.histmersion.values.BundleKind

/** Today's kinds of renamed records, named in one table until each owner names its own: what each is called and what
  * its identifier is called, and which bundle kind carries each.
  */
object AliasKindsAdapter:
  private def kind(noun: String, idWord: String): AliasKind = AliasKind(RecordNoun(noun), KeyWord(idWord))

  val SourceRecords: AliasKind = kind("source", "citekey")
  val ArticleRecords: AliasKind = kind("article", "slug")
  val EventRecords: AliasKind = kind("event", "id")
  val GoalRecords: AliasKind = kind("goal", "slug")
  val PlaceRecords: AliasKind = kind("place", "id")

  val all: List[AliasKind] = List(SourceRecords, ArticleRecords, EventRecords, GoalRecords, PlaceRecords)

  def of(b: BundleKind): Option[AliasKind] = b match
    case BundleKind.Article => Some(ArticleRecords)
    case BundleKind.Event   => Some(EventRecords)
    case BundleKind.Goal    => Some(GoalRecords)
    case BundleKind.Place   => Some(PlaceRecords)
    case _                  => None
