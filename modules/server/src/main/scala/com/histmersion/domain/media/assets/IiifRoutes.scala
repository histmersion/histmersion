package com.histmersion
package domain.media.assets

import com.histmersion.api.{Api, Write}
import com.histmersion.api.library.Citekey as ApiCitekey
import com.histmersion.api.media.Iiif
import com.histmersion.domain.library.Citekey
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.lint.{LintRule, tolerated}

/** The IIIF Image API Level 1 (plan §4.3) bound to its controller: a figure's `info.json` and its images. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object IiifRoutes:
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  private def citekey[A](raw: String)(use: Citekey => Either[Api.Error, A]): Either[Api.Error, A] =
    Citekey.option(raw).toRight(Write.notFound(ApiCitekey(raw))).flatMap(use)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  def apply(iiif: IiifControllers): List[ServerEndpoint[Any, Identity]] =
    List(
      Api.iiifInfo.serverLogic[Identity]((ck, name) =>
        citekey(ck.value)(k => iiif.info(k, AssetService.AssetName(name.value)))
      ),
      Api.iiifImage.serverLogic[Identity] { (ck, name, region, size, rotation, filename) =>
        citekey(ck.value) { k =>
          iiif
            .image(
              k,
              AssetService.AssetName(name.value),
              Iiif.Region.Wire(region.value),
              Iiif.Size.Wire(size.value),
              Iiif.Rotation(rotation.value),
              IiifControllers.Filename(filename.value)
            )
            .map((bytes, mime) => (Api.FileBytes(bytes.value), Api.ContentType(mime.value)))
        }
      }
    )
