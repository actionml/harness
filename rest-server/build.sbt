name := "pio-kappa-rest-server"

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.16"
lazy val akkaHttpVersion = "10.0.2"
lazy val circeVersion = "0.7.0"
lazy val scalaTestVersion = "3.0.1"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.8",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.22",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.11.0",

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  "org.scaldi" %% "scaldi-akka" % "0.5.8",
  "joda-time" % "joda-time" % "2.9.7",

  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  "org.scalatest"     %% "scalatest" % scalaTestVersion % "test"
)