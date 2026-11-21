package com.histmersion
package plugin

import com.histmersion.util.problem.Problems
import io.github.iltotore.iron.constraint.any.Pure

/** What [[SourceCollection.updateAll]] does, or did, to one item. */
enum CollectionUpdateOutcome:
  /** A dry run: these fields would change. */
  case Change(changes: List[CollectionChange])

  case Applied(changes: List[CollectionChange])

  case Failed(changes: List[CollectionChange], reason: Problems)

  case UpToDate

  /** More than one source links the item: which one it should show is a person's decision, so it is left alone. */
  case Shared

  case Missing

  /** The source's own metadata is refused (a blank or placeholder title or author), for `reason`. */
  case Refused(reason: Problems)
