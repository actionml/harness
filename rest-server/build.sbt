name := "harness-rest-server"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.18"
lazy val akkaHttpVersion = "10.0.5"
lazy val circeVersion = "0.7.1"
lazy val scalaTestVersion = "3.0.1"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val commonSettings = Seq(
  organization := "com.actionml",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.2",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.25",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
    "de.heikoseeberger" %% "akka-http-circe" % "1.14.0",

    "org.mongodb" %% "casbah" % "3.1.1",
    "com.github.salat" %% "salat" % "1.11.0",

    "org.json4s" %% "json4s-jackson" % "3.5.1",
    "org.json4s" %% "json4s-ext" % "3.5.1",

    "com.github.scopt" %% "scopt" % "3.5.0",

    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "org.typelevel" %% "cats" % "0.9.0",

    "com.typesafe" % "config" % "1.3.1",
    "com.iheart" %% "ficus" % "1.4.0",
    "org.scaldi" %% "scaldi-akka" % "0.5.8",
    "joda-time" % "joda-time" % "2.9.9",

    "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
  )
)

lazy val core = (project in file("core")).
  settings(
    commonSettings
  )

lazy val templates = (project in file("templates")).dependsOn(core).
  settings(
    commonSettings
  )

lazy val root = (project in file(".")).dependsOn(core, templates).settings(
  commonSettings
).enablePlugins(JavaAppPackaging).aggregate(core, templates)
