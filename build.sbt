ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.13.18"
ThisBuild / organization := "com.example"

val AkkaVersion = "2.8.8"
val AkkaHttpVersion = "10.5.3"
val ScalaTestVersion = "3.2.19"

lazy val root = (project in file("."))
  .settings(
    name := "scala-communicator",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.5.6",
      "org.bouncycastle" % "bcprov-jdk18on" % "1.78.1",
      "org.bouncycastle" % "bcpkix-jdk18on" % "1.78.1",
      "org.xerial" % "sqlite-jdbc" % "3.46.0.0",
      "dev.onvoid.webrtc" % "webrtc-java" % "0.14.0",
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
    ),
    resolvers += "Akka library repository" at "https://repo.akka.io/maven"
  )
