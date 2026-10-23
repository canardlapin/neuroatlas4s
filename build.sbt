import org.scalajs.linker.interface.ModuleKind
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbtcrossproject.CrossPlugin.autoImport.*
import sbtcrossproject.CrossType
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*

ThisBuild / organization := "io.github.canardlapin"
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / homepage := Some(url("https://github.com/canardlapin/neuroatlas4s"))
ThisBuild / licenses := List(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
)

// Providers are immutable source pins. Each `neuroatlas4s.<provider>.build`
// property is an explicit sibling-checkout override for coordinated
// development; a consuming build forwards its own overrides here so one
// provider revision resolves throughout the graph.
def pinned(provider: String, revision: String): URI =
  sys.props
    .get(s"neuroatlas4s.$provider.build")
    .map(path => file(path).getCanonicalFile.toURI)
    .getOrElse(uri(s"https://github.com/canardlapin/$provider.git#$revision"))

lazy val locus4sRevision = "e32b0aeba5c4d19c457b2329272b95b26c061943"
lazy val locus4sBuild = pinned("locus4s", locus4sRevision)
lazy val locus4sCoreJVM = ProjectRef(locus4sBuild, "locus4s-coreJVM")
lazy val locus4sCoreJS = ProjectRef(locus4sBuild, "locus4s-coreJS")
lazy val locus4sDataJVM = ProjectRef(locus4sBuild, "locus4s-dataJVM")
lazy val locus4sDataJS = ProjectRef(locus4sBuild, "locus4s-dataJS")

lazy val neuroim4sRevision = "4f75c2bf141449d032f9fe3c558ef13a22a4d59f"
lazy val neuroim4sBuild = {
  Seq("locus4s", "image4s", "spatial4s", "reframe4s", "gale", "graph4s", "intaglio").foreach { provider =>
    sys.props.get(s"neuroatlas4s.$provider.build").foreach(System.setProperty(s"neuroim4s.$provider.build", _))
  }
  pinned("neuroim4s", neuroim4sRevision)
}
lazy val neuroim4sRoutingJVM = ProjectRef(neuroim4sBuild, "routingJVM")
lazy val neuroim4sRoutingJS = ProjectRef(neuroim4sBuild, "routingJS")
lazy val neuroim4sIoJVM = ProjectRef(neuroim4sBuild, "ioJVM")
lazy val neuroim4sTransformIoJVM = ProjectRef(neuroim4sBuild, "transformIoJVM")

lazy val neurosurf4sRevision = "4b23386bf750118b9c7c1220b6c592da9ba965e0"
lazy val neurosurf4sBuild = {
  Seq("spatial4s", "locus4s", "mesh4s", "image4s", "graph4s", "zarr4s", "neuroim4s", "reframe4s", "gale", "ravel", "intaglio").foreach { provider =>
    sys.props.get(s"neuroatlas4s.$provider.build").foreach(System.setProperty(s"neurosurf4s.$provider.build", _))
  }
  pinned("neurosurf4s", neurosurf4sRevision)
}
lazy val neurosurf4sCoreJVM = ProjectRef(neurosurf4sBuild, "coreJVM")
lazy val neurosurf4sCoreJS = ProjectRef(neurosurf4sBuild, "coreJS")
lazy val neurosurf4sSpatialIoJVM = ProjectRef(neurosurf4sBuild, "spatialIoJVM")
lazy val neurosurf4sIoJVM = ProjectRef(neurosurf4sBuild, "ioJVM")
lazy val neurosurf4sFixturesJVM = ProjectRef(neurosurf4sBuild, "fixturesJVM")
lazy val neurosurf4sRoutingJVM = ProjectRef(neurosurf4sBuild, "routingJVM")
lazy val neurosurf4sRoutingJS = ProjectRef(neurosurf4sBuild, "routingJS")

lazy val atlasBoundaryCheck = taskKey[Unit]("Keep atlas core runtime-independent and volume portable")
lazy val atlasBoundarySettings = Seq(
  atlasBoundaryCheck := {
    val repository = (LocalRootProject / baseDirectory).value
    val coreSources = (repository / "modules/neuroatlas4s-core/shared/src/main/scala" ** "*.scala").get
    val volumeSources = (repository / "modules/neuroatlas4s-volume/shared/src/main/scala" ** "*.scala").get
    val coreRuntimeImport = "(?m)^import (?:image4s|neuroim4s|neurosurf4s|reframe4s|scalafim)\\.".r
    val runtimeLeak = "(?m)^import (?:scalafim|java\\.(?:io|nio)|scala\\.io)\\.".r
    val violations = coreSources.filter(p => coreRuntimeImport.findFirstIn(IO.read(p)).nonEmpty) ++
      volumeSources.filter(p => runtimeLeak.findFirstIn(IO.read(p)).nonEmpty)
    if (violations.nonEmpty) sys.error(s"Atlas runtime boundary: ${violations.mkString(", ")}")
  },
  Compile / compile := (Compile / compile).dependsOn(atlasBoundaryCheck).value
)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-release:17"
  ),
  Test / fork := false,
  libraryDependencies += "org.scalameta" %%% "munit" % "1.2.1" % Test
)

lazy val jsSettings = Seq(
  scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  Test / jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv()
)

// Atlas identities, releases, labels, hierarchies, catalogs and the generic
// realized-assignment contract. It needs no volume or surface runtime.
lazy val core =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/neuroatlas4s-core"))
    .settings(commonSettings)
    .settings(atlasBoundarySettings)
    .settings(
      name := "neuroatlas4s-core",
      description := "Atlas identities, releases, labels, hierarchies, catalogs and realized assignments."
    )
    .jvmConfigure(_.dependsOn(locus4sCoreJVM, locus4sDataJVM))
    .jsConfigure(_.dependsOn(locus4sCoreJS, locus4sDataJS))
    .jsSettings(jsSettings)

// Optional volume runtime. Core catalogs and publication records stay runtime-independent.
lazy val volume =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/neuroatlas4s-volume"))
    .dependsOn(core)
    .settings(commonSettings)
    .settings(atlasBoundarySettings)
    .settings(name := "neuroatlas4s-volume")
    .jvmConfigure(_.dependsOn(neuroim4sRoutingJVM))
    .jsConfigure(_.dependsOn(neuroim4sRoutingJS))
    .jsSettings(jsSettings)

lazy val ioJVM = project
  .in(file("modules/neuroatlas4s-io/jvm"))
  .dependsOn(core.jvm)
  .settings(commonSettings)
  .settings(name := "neuroatlas4s-io")

lazy val volumeIoJVM = project
  .in(file("modules/neuroatlas4s-volume-io/jvm"))
  .dependsOn(volume.jvm % "compile->compile;test->test", ioJVM)
  .dependsOn(neuroim4sIoJVM, neuroim4sTransformIoJVM)
  .settings(commonSettings)
    .settings(atlasBoundarySettings)
  .settings(name := "neuroatlas4s-volume-io")

// Surface assignments and GIFTI loading do not acquire the volume or render stack.
lazy val surface = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/neuroatlas4s-surface"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(atlasBoundarySettings)
  .settings(name := "neuroatlas4s-surface")
  .jvmConfigure(_.dependsOn(neurosurf4sCoreJVM))
  .jsConfigure(_.dependsOn(neurosurf4sCoreJS))
  .jsSettings(jsSettings)

lazy val surfaceIoJVM = project
  .in(file("modules/neuroatlas4s-surface-io/jvm"))
  .dependsOn(surface.jvm % "compile->compile;test->test", ioJVM, neurosurf4sIoJVM)
  .settings(commonSettings)
  .settings(name := "neuroatlas4s-surface-io")

// Atlas template policy composes the providers' anatomical execution.
lazy val routing = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/neuroatlas4s-routing"))
  .dependsOn(surface, volume % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(atlasBoundarySettings)
  .settings(name := "neuroatlas4s-routing")
  .jvmConfigure(_.dependsOn(neurosurf4sRoutingJVM))
  .jsConfigure(_.dependsOn(neurosurf4sRoutingJS))
  .jsSettings(jsSettings)

lazy val routingIoJVM = project
  .in(file("modules/neuroatlas4s-routing-io/jvm"))
  .dependsOn(routing.jvm % "compile->compile;test->test", volumeIoJVM, surfaceIoJVM, neurosurf4sSpatialIoJVM, neurosurf4sFixturesJVM % "test->compile;test->test")
  .settings(commonSettings)
  .settings(name := "neuroatlas4s-routing-io", libraryDependencies += "com.lihaoyi" %% "ujson" % "4.1.0" % Test)
  .settings(
      // Full-resolution MNI inversion retains large fields and residual arrays. Give this
      // native oracle a fresh heap on every gate, independent of the resident sbt server.
      Test / parallelExecution := false,
      Test / testForkedParallel := false,
      Test / testGrouping := {
        val (nativeOracle, ordinary) = (Test / definedTests).value.partition(
          _.name == "neuroatlas4s.io.MniTemplateBridgeFilesSuite"
        )
        val inherited = (Test / forkOptions).value
        val nativeOptions = inherited.withRunJVMOptions(
          inherited.runJVMOptions.filterNot(option => option.startsWith("-Xmx") || option.startsWith("-Xms")) ++
            Vector("-Xmx3g", "-XX:ActiveProcessorCount=2")
        )
        Seq(
          Tests.Group("atlas", ordinary, Tests.InProcess),
          Tests.Group("atlas-mni-native-oracle", nativeOracle, Tests.SubProcess(nativeOptions))
        ).filter(_.tests.nonEmpty)
      }
  )

lazy val root =
  project
    .in(file("."))
    .aggregate(core.jvm, core.js, ioJVM, volume.jvm, volume.js, volumeIoJVM, surface.jvm, surface.js, surfaceIoJVM, routing.jvm, routing.js, routingIoJVM)
    .settings(name := "neuroatlas4s-root", publish / skip := true)

addCommandAlias("neuroatlas4sCompileAll", ";root/compile;root/Test/compile")
addCommandAlias("neuroatlas4sTestAll", ";coreJVM/test;coreJS/test;ioJVM/test;volumeJVM/test;volumeJS/test;volumeIoJVM/test;surfaceJVM/test;surfaceJS/test;surfaceIoJVM/test;routingJVM/test;routingJS/test;routingIoJVM/test")
