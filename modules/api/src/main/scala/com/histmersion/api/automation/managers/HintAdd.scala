package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class HintAdd(
    text: HintAdd.Text,
    item: Option[HintAdd.Item] = None,
    once: Option[HintAdd.Once] = None,
    effect: Option[HintEffect] = None,
    author: Option[HintAdd.Author] = None,
    propose: Option[HintAdd.Propose] = None,
    suggest: Option[HintAdd.Suggest] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object HintAdd:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Item = Item.T
  object Item extends Newtype[String, Pure]
  enum Once derives CanEqual:
    case Once, Standing
  object Once extends TwoStates[Once]:
    extension (once: Once) def once: Boolean = once == Once
  type Author = Author.T
  object Author extends Newtype[String, Pure]
  enum Propose derives CanEqual:
    case Propose, Direct
  object Propose extends TwoStates[Propose]:
    extension (propose: Propose) def propose: Boolean = propose == Propose
  enum Suggest derives CanEqual:
    case Suggest, Direct
  object Suggest extends TwoStates[Suggest]:
    extension (suggest: Suggest) def suggest: Boolean = suggest == Suggest
