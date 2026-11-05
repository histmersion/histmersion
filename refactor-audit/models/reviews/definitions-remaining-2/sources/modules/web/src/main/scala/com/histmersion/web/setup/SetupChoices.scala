package com.histmersion.web.setup

import com.histmersion.api.instance.MapStartConfig

/** What a person chose in the setup. `area`: the background map's box to download (`None`: none); `finders`: each
  * open-access finder on or off.
  */
final case class SetupChoices(
    email: SetupChoices.Email = "",
    finders: Map[SetupChoices.FindersKey, SetupChoices.FindersValue] = Map.empty,
    area: Option[SetupChoices.Area] = Some("world"),
    start: MapStartConfig = MapStartConfig(),
    boundaries: SetupChoices.Boundaries = true,
    events: SetupChoices.Events = true,
    pictures: SetupChoices.Pictures = false,
    morePlaces: SetupChoices.MorePlaces = false,
    agents: SetupChoices.Agents = false,
    calibre: SetupChoices.Calibre = ""
)

object SetupChoices:
  type Email = Email.T
  object Email extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindersKey = FindersKey.T
  object FindersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindersValue = FindersValue.T
  object FindersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Area = Area.T
  object Area extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Boundaries = Boundaries.T
  object Boundaries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Events = Events.T
  object Events extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pictures = Pictures.T
  object Pictures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MorePlaces = MorePlaces.T
  object MorePlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Agents = Agents.T
  object Agents extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Calibre = Calibre.T
  object Calibre extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
