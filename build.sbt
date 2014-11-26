name := "TwitterClient"

version := "1.0"

scalaVersion := "2.11.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

libraryDependencies +=
  "com.typesafe.akka" %% "akka-remote" % "2.3.6"
