name := """gestalt-upgrader"""
organization := "com.galacticfog"

lazy val root = (project in file(".")).enablePlugins(PlayScala, GitVersioning)

git.baseVersion := "1.6"
git.useGitDescribe := true

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.akka" %% "akka-persistence" % "2.5.12",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "net.codingwell"  %% "scala-guice" 					 % "4.1.1"
)

libraryDependencies ++= Seq(
  specs2 % Test
)

