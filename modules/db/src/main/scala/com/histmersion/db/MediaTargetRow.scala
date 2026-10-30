package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*

final private[db] case class MediaTargetRow(
    targetType: String,
    targetId: String,
    role: String,
    label: String,
    item: String
) derives DbCodec
