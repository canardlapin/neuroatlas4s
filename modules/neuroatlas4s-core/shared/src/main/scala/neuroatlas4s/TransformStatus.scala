package neuroatlas4s

/** The kind of map a template-space transform step applies. */
enum TransformKind:
  case Identity, Affine, NonlinearWarp, SphereResample, VolToSurf, SurfToVol

/** Whether a template-space transform step can run now. */
enum TransformStatus:
  case Available, Planned
