package com.histmersion.api

final case class DataCredit(
    name: String,
    usedFor: String,
    licence: String,
    licenceUrl: Option[String],
    credit: String,
    url: String,
    onMap: Boolean = true
)
