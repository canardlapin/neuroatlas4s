package neuroatlas4s

import image4s.geometry.{Affine as ProviderAffine, D3}

private[neuroatlas4s] object VolumeManifest:
  val mni305ToMni152: ProviderAffine[D3] =
    ProviderAffine.fromRowMajor[D3](
      Vector(
        0.9975, -0.0073, 0.0176, -0.0429,
        0.0146, 1.0009, -0.0024, 1.5496,
        -0.0130, -0.0093, 0.9971, 1.1840,
        0.0, 0.0, 0.0, 1.0
      )
    ).fold(error => throw new IllegalStateException(error.message), identity)

  val mni152ToMni305: ProviderAffine[D3] =
    mni305ToMni152.inverse

  val steps: Vector[TransformStep] =
    Vector(
      TransformStep(
        SpaceId.MNI305,
        SpaceId.MNI152,
        TransformKind.Affine,
        TransformBackend.InternalAffine,
        Confidence.Exact,
        reversible = true,
        dataFiles = Vector.empty,
        TransformStatus.Available,
        notes = Some("FreeSurfer mni152.register.dat"),
        affine = Some(mni305ToMni152)
      ),
      TransformStep(
        SpaceId.MNI152,
        SpaceId.MNI305,
        TransformKind.Affine,
        TransformBackend.InternalAffine,
        Confidence.Exact,
        reversible = true,
        dataFiles = Vector.empty,
        TransformStatus.Available,
        notes = Some("Inverse of FreeSurfer mni152.register.dat"),
        affine = Some(mni152ToMni305)
      ),
      TransformStep(
        SpaceId.MNI152NLin6Asym,
        SpaceId.MNI152NLin2009cAsym,
        TransformKind.NonlinearWarp,
        TransformBackend.TemplateFlowAnts,
        Confidence.High,
        reversible = true,
        dataFiles = Vector(TemplateFlowXfm.Mni6ToMni2009c.relativePath),
        TransformStatus.Planned,
        notes = Some("TemplateFlow composite warp; load it with MniTemplateBridge and install the bridge's steps")
      ),
      TransformStep(
        SpaceId.MNI152NLin2009cAsym,
        SpaceId.MNI152NLin6Asym,
        TransformKind.NonlinearWarp,
        TransformBackend.TemplateFlowAnts,
        Confidence.High,
        reversible = true,
        dataFiles = Vector(TemplateFlowXfm.Mni6ToMni2009c.relativePath),
        TransformStatus.Planned,
        notes = Some(
          "Inverse of the TemplateFlow composite warp; needs a qualified numerical inverse " +
            s"(${TemplateFlowXfm.Mni2009cToMni6.fileName} pulls the same way as the forward file and is refused)"
        )
      )
    )
