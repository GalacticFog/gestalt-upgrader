name := """gestalt-upgrader"""
organization := "com.galacticfog"

lazy val root = (project in file(".")).enablePlugins(PlayScala, GitVersioning)

git.baseVersion := "1.6"
git.useGitDescribe := true

scalaVersion := "2.12.4"

import com.typesafe.sbt.packager.docker._
maintainer in Docker := "Chris Baker <chris@galacticfog.com>"
dockerBaseImage := "java:8-jre-alpine"
dockerExposedPorts := Seq(9000)
dockerUsername := Some("galacticfog")
dockerCommands := dockerCommands.value.flatMap {
  case cmd@Cmd("FROM",_) => List(
    cmd,
    Cmd("RUN", "apk add --update bash libc6-compat libstdc++ && rm -rf /var/cache/apk/*")     
  )
  case other => List(other)
}


libraryDependencies ++= Seq(
  ws,
  guice,
  "com.typesafe.akka" %% "akka-persistence" % "2.5.12",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "net.codingwell"  %% "scala-guice" 					 % "4.1.1"
)

libraryDependencies ++= Seq(
  specs2 % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.12" % Test
)

