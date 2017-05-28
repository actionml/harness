name := "harness"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.18"
lazy val akkaHttpVersion = "10.0.7"
lazy val circeVersion = "0.8.0"
lazy val scalaTestVersion = "3.0.1"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val commonSettings = Seq(
  organization := "com.actionml",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.25",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

    "org.typelevel" %% "cats" % "0.9.0",

    "com.typesafe" % "config" % "1.3.1",
    "com.iheart" %% "ficus" % "1.4.0",
    "joda-time" % "joda-time" % "2.9.9",

    "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
  )
)

lazy val core = (project in file("core")).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.mongodb" %% "casbah" % "3.1.1",
      "com.github.salat" %% "salat" % "1.11.0",

      "org.json4s" %% "json4s-jackson" % "3.5.1",
      "org.json4s" %% "json4s-ext" % "3.5.1"
    )
  )

lazy val templates = (project in file("templates")).dependsOn(core).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,

      "org.mongodb" %% "casbah" % "3.1.1",
      "com.github.salat" %% "salat" % "1.11.0",

      "org.json4s" %% "json4s-jackson" % "3.5.1",
      "org.json4s" %% "json4s-ext" % "3.5.1",

      "com.github.johnlangford" % "vw-jni" % "8.0.0"
    )
  )

lazy val admin = (project in file("admin")).dependsOn(core).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.mongodb" %% "casbah" % "3.1.1",
      "com.github.salat" %% "salat" % "1.11.0",

      "org.json4s" %% "json4s-jackson" % "3.5.1",
      "org.json4s" %% "json4s-ext" % "3.5.1"
    )
  )

lazy val drivers = (project in file("drivers")).dependsOn(core, templates, admin).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "com.github.scopt" %% "scopt" % "3.5.0"
  )
)

lazy val server = (project in file("server")).dependsOn(core, templates, admin).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
    "de.heikoseeberger" %% "akka-http-circe" % "1.16.0",

    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,

    "org.scaldi" %% "scaldi-akka" % "0.5.8"
  )
).enablePlugins(JavaAppPackaging).aggregate(core, templates, admin)
