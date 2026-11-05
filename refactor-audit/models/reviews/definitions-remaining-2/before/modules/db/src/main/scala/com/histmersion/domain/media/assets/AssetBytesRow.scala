package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

final private[assets] case class AssetBytesRow(mime: String, bytes: Array[Byte]) derives DbCodec
