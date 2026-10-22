package neuroatlas4s

import neuroatlas4s.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class AtlasAssetSuite extends munit.FunSuite:
  test("FileAtlasStore resolves cached downloads and validates minBytes") {
    val tmp = Files.createTempDirectory("scalafim-atlas-store-test")
    val source = tmp.resolve("source.txt")
    Files.writeString(source, "atlas-data", StandardCharsets.UTF_8)
    val cache = tmp.resolve("cache")
    val store = FileAtlasStore(cache)
    val asset = AtlasAsset(
      key = "toy",
      fileName = "toy.txt",
      uri = source.toUri,
      minBytes = 4L
    )

    val resolved = store.resolve(asset)
    assertEquals(Files.readString(resolved, StandardCharsets.UTF_8), "atlas-data")
    assertEquals(AtlasAsset.sha256(resolved).length, 64)

    val cached = store.resolve(asset, AssetPolicy.CacheOnly)
    assertEquals(cached, resolved)

    interceptMessage[IllegalStateException]("atlas asset 'too-large' is not present in cache: " + cache.resolve("too-large.txt").toString) {
      store.resolve(asset.copy(key = "too-large", fileName = "too-large.txt", minBytes = 100L), AssetPolicy.CacheOnly)
    }
  }

  test("FileAtlasStore validates SHA-256 on downloaded assets") {
    val tmp = Files.createTempDirectory("scalafim-atlas-sha-test")
    val source = tmp.resolve("source.txt")
    Files.writeString(source, "atlas-data", StandardCharsets.UTF_8)
    val cache = tmp.resolve("cache")
    val store = FileAtlasStore(cache)
    val sha = AtlasAsset.sha256(source)
    val asset = AtlasAsset(
      key = "sha-ok",
      fileName = "sha-ok.txt",
      uri = source.toUri,
      minBytes = 4L,
      sha256 = Some(sha)
    )

    val resolved = store.resolve(asset, AssetPolicy.Refresh)
    assertEquals(AtlasAsset.sha256(resolved), sha)

    val bad = asset.copy(key = "bad-sha", fileName = "bad-sha.txt", sha256 = Some("0" * 64))
    val err =
      intercept[IllegalStateException] {
        store.resolve(bad, AssetPolicy.Refresh)
      }
    assertEquals(err.getMessage, "downloaded atlas asset 'bad-sha' failed validation")
    assert(!Files.exists(cache.resolve("bad-sha.txt")), clue = "failed downloads should not be cached")
  }

  test("FileAtlasStore refreshes stale cached assets and replaces cache on refresh") {
    val tmp = Files.createTempDirectory("scalafim-atlas-cache-refresh-test")
    val source = tmp.resolve("source.txt")
    val cache = tmp.resolve("cache")
    val cached = cache.resolve("atlas.txt")
    Files.createDirectories(cache)
    Files.writeString(cached, "stale-cache", StandardCharsets.UTF_8)
    Files.writeString(source, "fresh-cache", StandardCharsets.UTF_8)

    val store = FileAtlasStore(cache)
    val freshSha = AtlasAsset.sha256(source)
    val asset =
      AtlasAsset(
        key = "refresh",
        fileName = "atlas.txt",
        uri = source.toUri,
        minBytes = 1L,
        sha256 = Some(freshSha)
      )

    val resolved = store.resolve(asset, AssetPolicy.CacheOrDownload)
    assertEquals(resolved, cached)
    assertEquals(Files.readString(cached, StandardCharsets.UTF_8), "fresh-cache")
    assertEquals(AtlasAsset.sha256(cached), freshSha)

    Files.writeString(source, "new-refresh", StandardCharsets.UTF_8)
    val refreshedAsset = asset.copy(sha256 = Some(AtlasAsset.sha256(source)))
    val refreshed = store.resolve(refreshedAsset, AssetPolicy.Refresh)
    assertEquals(refreshed, cached)
    assertEquals(Files.readString(cached, StandardCharsets.UTF_8), "new-refresh")
  }

  test("AtlasAsset validates metadata fields") {
    val tmp = Files.createTempDirectory("scalafim-atlas-asset-validation-test")
    val source = tmp.resolve("source.txt")
    Files.writeString(source, "data", StandardCharsets.UTF_8)

    interceptMessage[IllegalArgumentException]("requirement failed: asset key must be non-empty") {
      AtlasAsset("", "atlas.txt", source.toUri)
    }

    interceptMessage[IllegalArgumentException]("requirement failed: asset fileName must be non-empty") {
      AtlasAsset("atlas", " ", source.toUri)
    }

    interceptMessage[IllegalArgumentException]("requirement failed: asset minBytes must be non-negative") {
      AtlasAsset("atlas", "atlas.txt", source.toUri, minBytes = -1L)
    }
  }

  test("AtlasProvenanceFiles attaches local paths and digests by artifact role") {
    val tmp = Files.createTempDirectory("scalafim-atlas-provenance-files-test")
    val volumePath = tmp.resolve("toy.nii.gz")
    val labelPath = tmp.resolve("toy.tsv")
    Files.writeString(volumePath, "volume-bytes", StandardCharsets.UTF_8)
    Files.writeString(labelPath, "1\tA\n", StandardCharsets.UTF_8)
    val regions =
      RegionIndex(
        Vector(
          AtlasRegionMetadata(RegionId(1), "A", hemisphere = Some(Hemisphere.Left))
        )
      )
    val ref =
      AtlasRef.volume(
        family = "toy",
        model = "Toy",
        templateSpace = SpaceId.MNI152,
        coordSpace = SpaceId.MNI152,
        confidence = Confidence.High,
        artifacts = Vector(
          AtlasArtifact(role = ArtifactRole.ParcellationVolume, sourceName = "toy", sourceRef = "toy.nii.gz", license = Some("CC0")),
          AtlasArtifact(role = ArtifactRole.LabelTable, sourceName = "toy", sourceRef = "toy.tsv", license = Some("CC0"))
        )
      )

    val enriched = AtlasProvenanceFiles.withLocalFiles(ref.toProvenance(regions), ArtifactRole.ParcellationVolume -> volumePath)
    val volume = enriched.sourceArtifacts.find(_.role == ArtifactRole.ParcellationVolume).get
    val label = enriched.sourceArtifacts.find(_.role == ArtifactRole.LabelTable).get

    assertEquals(volume.localPath, Some(volumePath.toString))
    assertEquals(volume.digest.map(_.value), Some(AtlasAsset.sha256(volumePath)))
    assertEquals(label.localPath, None)
    assertEquals(label.digest, None)
  }
