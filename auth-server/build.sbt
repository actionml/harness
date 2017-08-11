name := "auth-server"

version := "1.0"

scalaVersion := "2.12.1"

val akkaVersion = "2.4.17"
val akkaHttpVersion = "10.0.3"
val circeVersion = "0.7.0"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.8",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.22",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.nulab-inc" %% "scala-oauth2-core" % "1.3.0",
  "com.nulab-inc" %% "akka-http-oauth2-provider" % "1.3.0",

  "de.heikoseeberger" %% "akka-http-circe" % "1.12.0",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  "org.mongodb.scala" %% "mongo-scala-driver" % "2.1.0",

  "com.typesafe" % "config" % "1.3.1",
  "com.iheart" %% "ficus" % "1.4.0",
  "org.scaldi" %% "scaldi-akka" % "0.5.8",
  "joda-time" % "joda-time" % "2.9.7"
)
    