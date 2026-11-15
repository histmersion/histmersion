package com.histmersion
package plugin

import com.histmersion.util.problem.Problems
import io.github.iltotore.iron.constraint.any.Pure

/** What [[SourceCollection.updateAll]] does, or did, to one item. */
enum CollectionUpdateOutcome:
  /** A dry run: these fields would change. */
  case Change(changes: List[CollectionChange])

  /** These fields were written. */
  case Applied(changes: List[CollectionChange])

  /** Writing these fields failed, for `reason`. */
  case Failed(changes: List[CollectionChange], reason: Problems)

  /** The item already says what the source says. */
  case UpToDate

  /** More than one source links the item: which one it should show is a person's decision, so it is left alone. */
  case Shared

  /** The collection has no such item. */
  case Missing

  /** The source's own metadata is refused (a blank or placeholder title or author), for `reason`. */
  case Refused(reason: Problems)
