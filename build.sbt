name := """gestalt-upgrader"""
organization := "com.galacticfog"

//val location = file("..").toURI

//val sbtGit = RootProject(location)

lazy val root = (project in file(".")).enablePlugins(PlayScala, GitVersioning)

git.baseVersion := "1.6"
git.useGitDescribe := true

scalaVersion := "2.12.4"

libraryDependencies += guice
libraryDependencies += specs2

