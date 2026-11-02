package com.histmersion.api.media
import com.histmersion.api.given
final case class DataCredit(
    name: String,
    usedFor: String,
    licence: String,
    licenceUrl: Option[String],
    credit: String,
    url: String,
    onMap: Boolean = true
)
