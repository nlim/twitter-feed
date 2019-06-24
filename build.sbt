name := "twitter-feed"

version := "0.0.1"

scalaVersion := "2.12.4"

parallelExecution in Test := false

val http4sVersion = "0.20.1"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-core" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "io.circe" %% "circe-generic" % "0.11.1",
  "io.circe" %% "circe-parser" % "0.11.1",
  "org.typelevel" %% "cats-core" % "2.0.0-M3",
  "org.typelevel" %% "cats-effect" % "1.3.1",
  "com.twitter" %% "algebird-core" % "0.13.5",
  "com.github.pureconfig" %% "pureconfig" % "0.11.1"
)

resolvers ++= Seq(
  "Sonatype Snapshots"  at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Releases"   at "https://oss.sonatype.org/content/repositories/releases",
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
)

autoCompilerPlugins := true

scalacOptions += "-feature"
scalacOptions += "-Ypartial-unification"
scalacOptions += "-language:higherKinds"
scalacOptions += "-language:postfixOps"
