package com.histmersion
package domain.automation.goals

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.automation.Goal
import com.histmersion.api.automation.managers.ManagerItem
import com.histmersion.domain.automation.goals.Work

final case class Item(
    id: Work.ItemId,
    kind: ManagerItem.Kind,
    status: ItemStatus.Wire,
    goals: List[Goal.Slug],
    person: Work.Person = Work.Person(false),
    focusHeld: Work.FocusHeld = Work.FocusHeld(false)
)
