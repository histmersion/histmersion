package com.histmersion
package domain

import com.histmersion.UserId
import com.histmersion.domain.automation.Run

/** Who asks for an operation: a person, or an automated workflow run acting through the same service methods. */
enum Caller:
  case Person(user: UserId, role: Caller.Role)
  case Automation(run: Run.Id)

  /** The person asking; none for an automated run, which acts for nobody's own data. */
  def person: Option[UserId] = this match
    case Person(user, _) => Some(user)
    case Automation(_)   => None

object Caller:

  /** What a person may do, each role everything the one before it may and more. */
  enum Role:
    case Reader, Contributor, Researcher, Admin

    def includes(other: Role): Boolean = ordinal >= other.ordinal

  /** The one person a server without accounts runs for: every request is theirs, with every permission. */
  val Local: Person = Person(UserId.Local, Role.Admin)
