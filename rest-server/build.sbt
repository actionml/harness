import sbt.Keys.resolvers

name := "harness"

version := "0.3.0-SNAPSHOT"

scalaVersion := "2.11.12"

lazy val akkaVersion = "2.4.18"
lazy val akkaHttpVersion = "10.0.9"
lazy val circeVersion = "0.8.0"
lazy val scalaTestVersion = "3.0.1"
lazy val mongoVersion = "3.6.4"
lazy val mongoScalaDriverVersion = "2.4.0"
//lazy val sparkVersion = "2.3.1"
lazy val sparkVersion = "2.2.1"
//lazy val json4sVersion = "3.6.0"
lazy val json4sVersion = "3.5.1"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val commonSettings = Seq(
  organization := "com.actionml",
  version := "0.3.0-SNAPSHOT",
  scalaVersion := "2.11.12",
  updateOptions := updateOptions.value.withLatestSnapshots(false),
  resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  resolvers += Resolver.mavenLocal,
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

    "org.typelevel" %% "cats" % "0.9.0",

    "com.typesafe" % "config" % "1.3.1",
    "com.iheart" %% "ficus" % "1.4.0",

    "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
  ),
  excludeDependencies ++= Seq(
    SbtExclusionRule("org.slf4j", "log4j-over-slf4j"),
    SbtExclusionRule("org.slf4j", "slf4j-log4j12")
  )
)

lazy val core = (project in file("core")).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % mongoScalaDriverVersion,
      "org.mongodb.scala" %% "mongo-scala-bson" % mongoScalaDriverVersion,
      "org.mongodb" % "bson" % mongoVersion,
      "org.mongodb" % "mongodb-driver-core" % mongoVersion,
      "org.mongodb" % "mongodb-driver-async" % mongoVersion,

      "org.apache.spark" % "spark-core_2.11" %sparkVersion,
      "org.apache.spark" %% "spark-sql" % sparkVersion,
      "org.apache.spark" %% "spark-hive" % sparkVersion,
      "org.apache.spark" %% "spark-yarn" % sparkVersion,
      //"org.apache.hbase" % "hbase" % "2.1.0",
      //"org.apache.hbase" % "hbase-common" % "2.1.0",
      //"org.apache.hbase" % "hbase-client" % "2.1.0",

      "org.mongodb.spark" %% "mongo-spark-connector" % "2.2.3",
      "org.scala-lang.modules" %% "scala-xml" % "1.1.0",

      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.json4s" %% "json4s-ext" % json4sVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-generic-extras" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-optics" % circeVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.16.0",

      "org.elasticsearch.client" % "elasticsearch-rest-client" % "6.4.0",

      "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
    ),
    excludeDependencies ++= Seq(
      SbtExclusionRule("org.slf4j", "log4j-over-slf4j"),
      SbtExclusionRule("org.slf4j", "slf4j-log4j12")
    )
  )

lazy val common = (project in file("common")).dependsOn(core).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,

      "org.scaldi" %% "scaldi-akka" % "0.5.8"
    ),
    excludeDependencies ++= Seq(
      SbtExclusionRule("org.slf4j", "log4j-over-slf4j"),
      SbtExclusionRule("org.slf4j", "slf4j-log4j12")
    )
  )

lazy val engines = (project in file("engines")).dependsOn(core).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,

      // the dynamic lib must be hand installed for this Java JNI wrapper to find it
      "com.github.johnlangford" % "vw-jni" % "8.4.1"
    ),
    excludeDependencies ++= Seq(
      SbtExclusionRule("org.slf4j", "log4j-over-slf4j"),
      SbtExclusionRule("org.slf4j", "slf4j-log4j12")
    )
  )

lazy val admin = (project in file("admin")).dependsOn(core).
  settings(
    commonSettings
  )

lazy val server = (project in file("server")).dependsOn(core, common, engines, admin).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "com.actionml" %% "harness-auth-common" % "0.3.0-SNAPSHOT",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
    "org.ehcache" % "ehcache" % "3.4.0"
  ),
  excludeDependencies ++= Seq(
    SbtExclusionRule("org.slf4j", "log4j-over-slf4j"),
    SbtExclusionRule("org.slf4j", "slf4j-log4j12")
  )
).enablePlugins(JavaAppPackaging).aggregate(core, common, engines, admin)
