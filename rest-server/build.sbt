import sbt.Keys.resolvers

name := "harness"

version := "0.2.0-RC1"

scalaVersion := "2.11.11"

lazy val akkaVersion = "2.4.18"
lazy val akkaHttpVersion = "10.0.9"
lazy val circeVersion = "0.8.0"
lazy val scalaTestVersion = "3.0.1"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
resolvers +=  "Novus Release Repository" at "http://repo.novus.com/releases/"

lazy val commonSettings = Seq(
  organization := "com.actionml",
  version := "0.2.0-RC1",
  scalaVersion := "2.11.8",
  updateOptions := updateOptions.value.withLatestSnapshots(false),
  resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  resolvers += Resolver.mavenLocal,
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.25",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

    "org.typelevel" %% "cats" % "0.9.0",

    "com.typesafe" % "config" % "1.3.1",
    "com.iheart" %% "ficus" % "1.4.0",

    "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
  )
)

lazy val core = (project in file("core")).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % "2.3.0",
      "org.mongodb.scala" %% "mongo-scala-bson" % "2.3.0",
      "org.mongodb" % "bson" % "3.7.0",
      "org.mongodb" % "mongodb-driver-core" % "3.7.0",
      "org.mongodb" % "mongodb-driver-async" % "3.7.0",

      "org.json4s" %% "json4s-jackson" % "3.5.1",
      "org.json4s" %% "json4s-ext" % "3.5.1"
    )
  )

lazy val common = (project in file("common")).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-generic-extras" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.16.0",

      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,

      "org.scaldi" %% "scaldi-akka" % "0.5.8"
    )
  )

lazy val engines = (project in file("engines")).dependsOn(core).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,

      "org.json4s" %% "json4s-jackson" % "3.5.1",
      "org.json4s" %% "json4s-ext" % "3.5.1",

      // the dynamic lib must be hand installed for this Java JNI wrapper to find it
      "com.github.johnlangford" % "vw-jni" % "8.4.1"
    )
  )

lazy val admin = (project in file("admin")).dependsOn(core).
  settings(
    commonSettings
  )

lazy val server = (project in file("server")).dependsOn(core, common, engines, admin).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
    "org.ehcache" % "ehcache" % "3.4.0"
  )
).enablePlugins(JavaAppPackaging).aggregate(core, common, engines, admin)

lazy val authServer = (project in file("auth-server")).dependsOn(core, common)
  .settings(commonSettings)
  .enablePlugins(JavaAppPackaging)
  .aggregate(common)
