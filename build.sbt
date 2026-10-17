ThisBuild / version := sys.env.getOrElse("APP_VERSION", "0.1.0-SNAPSHOT")
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
    resolvers += "Akka library repository" at "https://repo.akka.io/maven",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) =>
        xs.map(_.toLowerCase) match {
          case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) => MergeStrategy.discard
          case (x :: _) if x.endsWith(".sf") || x.endsWith(".dsa") || x.endsWith(".rsa") => MergeStrategy.discard
          case ("license" :: Nil) | ("notice" :: Nil) | ("license.txt" :: Nil) | ("notice.txt" :: Nil) => MergeStrategy.discard
          case _ => MergeStrategy.first
        }
      case "reference.conf" => MergeStrategy.concat
      case "application.conf" => MergeStrategy.concat
      case x if x.endsWith("/module-info.class") => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
    assembly / mainClass := Some("com.example.comm.server.ServerApp"),
    assembly / assemblyJarName := s"scala-communicator-server-${version.value}.jar"
  )

lazy val clientAssembly = taskKey[File]("Build the client fat jar")
clientAssembly := {
  val log = streams.value.log
  val serverJar = assembly.value
  val clientJar = target.value / s"scala-communicator-client-${version.value}.jar"
  IO.copyFile(serverJar, clientJar)
  log.info(s"Client jar written to $clientJar (main: com.example.comm.client.ClientApp)")
  clientJar
}
