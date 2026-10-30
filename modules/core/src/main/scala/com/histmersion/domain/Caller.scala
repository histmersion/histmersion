package com.histmersion.domain

import com.histmersion.values.{RunId, UserId}

/** Who asks for an operation: a person, or an automated workflow run acting through the same service methods. */
enum Caller:
  case Person(user: UserId, role: Caller.Role)
  case Automation(run: RunId)

object Caller:

  /** What a person may do, each role everything the one before it may and more. */
  enum Role:
    case Reader, Contributor, Researcher, Admin

    def includes(other: Role): Boolean = ordinal >= other.ordinal

  /** The one person a server without accounts runs for: every request is theirs, with every permission. */
  val Local: Caller.Person = Caller.Person(UserId.Local, Role.Admin)
