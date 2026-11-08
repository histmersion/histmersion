package com.histmersion
package web.setup

import com.histmersion.api.instance.MapStartConfig
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
  object Email extends Newtype[String, Pure]
  type FindersKey = FindersKey.T
  object FindersKey extends Newtype[String, Pure]
  type FindersValue = FindersValue.T
  object FindersValue extends Newtype[Boolean, Pure]
  type Area = Area.T
  object Area extends Newtype[String, Pure]
  type Boundaries = Boundaries.T
  object Boundaries extends Newtype[Boolean, Pure]
  type Events = Events.T
  object Events extends Newtype[Boolean, Pure]
  type Pictures = Pictures.T
  object Pictures extends Newtype[Boolean, Pure]
  type MorePlaces = MorePlaces.T
  object MorePlaces extends Newtype[Boolean, Pure]
  type Agents = Agents.T
  object Agents extends Newtype[Boolean, Pure]
  type Calibre = Calibre.T
  object Calibre extends Newtype[String, Pure]
