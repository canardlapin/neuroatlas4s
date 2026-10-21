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

lazy val neuroim4sRevision = "21df6416e4e28e97cfbe39e20d7303349be7e4cf"
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

lazy val atlasBoundaryCheck = taskKey[Unit]("Keep atlas core runtime-independent and volume portable")
lazy val atlasBoundarySettings = Seq(
  atlasBoundaryCheck := {
    val repository = (LocalRootProject / baseDirectory).value
    val coreSources = (repository / "modules/neuroatlas4s-core/shared/src/main/scala" ** "*.scala").get
    val volumeSources = (repository / "modules/neuroatlas4s-volume/shared/src/main/scala" ** "*.scala").get
    val coreRuntimeImport = "(?m)^import (?:image4s|neuroim4s|reframe4s|scalafim)\\.".r
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

lazy val volumeIoJVM = project
  .in(file("modules/neuroatlas4s-volume-io/jvm"))
  .dependsOn(volume.jvm % "compile->compile;test->test")
  .dependsOn(neuroim4sIoJVM, neuroim4sTransformIoJVM)
  .settings(commonSettings)
    .settings(atlasBoundarySettings)
  .settings(name := "neuroatlas4s-volume-io")

lazy val root =
  project
    .in(file("."))
    .aggregate(core.jvm, core.js, volume.jvm, volume.js, volumeIoJVM)
    .settings(name := "neuroatlas4s-root", publish / skip := true)

addCommandAlias("neuroatlas4sCompileAll", ";root/compile;root/Test/compile")
addCommandAlias("neuroatlas4sTestAll", ";coreJVM/test;coreJS/test;volumeJVM/test;volumeJS/test;volumeIoJVM/test")
