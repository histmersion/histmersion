package com.histmersion.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

import java.util.UUID

/** A record's stable identity across renames, merges and databases, whatever its kind. */
type ItemUid = ItemUid.T

object ItemUid extends RefinedType[UUID, Pure]
