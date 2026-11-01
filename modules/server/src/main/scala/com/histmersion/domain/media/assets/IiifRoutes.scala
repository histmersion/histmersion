package com.histmersion.domain.media.assets

import com.histmersion.api.Api
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The IIIF Image API Level 1 (plan §4.3) bound to its controller: a figure's `info.json` and its images. */
object IiifRoutes:
  def apply(iiif: IiifControllers): List[ServerEndpoint[Any, Identity]] =
    List(
      Api.iiifInfo.serverLogic[Identity]((ck, name) => iiif.info(ck, name)),
      Api.iiifImage.serverLogic[Identity] { (ck, name, region, size, rotation, filename) =>
        iiif.image(ck, name, region, size, rotation, filename)
      }
    )
