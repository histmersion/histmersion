package com.histmersion
package web.setup

import com.histmersion.api.instance.MapStartConfig
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a person chose in the setup. `area`: the background map's box to download (`None`: none); `finders`: each
  * open-access finder on or off.
  */
final case class SetupChoices(
    email: SetupChoices.Email = SetupChoices.Email(""),
    finders: Map[SetupChoices.FindersKey, SetupChoices.FindersValue] = Map.empty,
    area: Option[SetupChoices.Area] = Some(SetupChoices.Area("world")),
    start: MapStartConfig = MapStartConfig(),
    boundaries: SetupChoices.Boundaries = SetupChoices.Boundaries(true),
    events: SetupChoices.Events = SetupChoices.Events(true),
    pictures: SetupChoices.Pictures = SetupChoices.Pictures(false),
    morePlaces: SetupChoices.MorePlaces = SetupChoices.MorePlaces(false),
    agents: SetupChoices.Agents = SetupChoices.Agents(false),
    calibre: SetupChoices.Calibre = SetupChoices.Calibre("")
)

object SetupChoices:
  type Email = Email.T
  object Email extends RefinedType[String, Pure]
  type FindersKey = FindersKey.T
  object FindersKey extends RefinedType[String, Pure]
  type FindersValue = FindersValue.T
  object FindersValue extends RefinedType[Boolean, Pure]
  type Area = Area.T
  object Area extends RefinedType[String, Pure]
  type Boundaries = Boundaries.T
  object Boundaries extends RefinedType[Boolean, Pure]
  type Events = Events.T
  object Events extends RefinedType[Boolean, Pure]
  type Pictures = Pictures.T
  object Pictures extends RefinedType[Boolean, Pure]
  type MorePlaces = MorePlaces.T
  object MorePlaces extends RefinedType[Boolean, Pure]
  type Agents = Agents.T
  object Agents extends RefinedType[Boolean, Pure]
  type Calibre = Calibre.T
  object Calibre extends RefinedType[String, Pure]
