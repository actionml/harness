name := "pio-kappa-rest-server"

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.17"
lazy val akkaHttpVersion = "10.0.5"
lazy val circeVersion = "0.7.0"
lazy val scalaTestVersion = "3.0.1"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.8",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.22",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.12.0",
  "de.heikoseeberger" %% "akka-http-jackson" % "1.12.0",

  "org.mongodb" %% "casbah" % "3.1.1",
  "com.novus" %% "salat-core" % "1.9.9",
  //"com.novus" %% "salat" % "2.0.0-SNAPSHOT",
  "org.json4s" %% "json4s-jackson" % "3.5.0",
  "org.json4s" %% "json4s-ext" % "3.5.0",
  "com.github.scopt" %% "scopt" % "3.5.0",

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "org.typelevel" %% "cats" % "0.9.0",

  "com.typesafe" % "config" % "1.3.1",
  "com.iheart" %% "ficus" % "1.4.0",
  "org.scaldi" %% "scaldi-akka" % "0.5.8",
  "joda-time" % "joda-time" % "2.9.7",

  "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
)
