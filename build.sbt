scalaVersion := "3.2.1"

lazy val SubShEx2SPARQL = (project in file("."))
 .enablePlugins(DockerPlugin, JavaAppPackaging,BuildInfoPlugin)
 .settings(
  name := "SubShEx2SPARQL",
  libraryDependencies ++= Seq(
   "com.monovore"     %% "decline-effect"      % "2.4.0",
   "es.weso"          %% "shex"                % "0.2.29",
   "es.weso"          %% "srdfjena"            % "0.1.122",
   "org.apache.jena"  %  "jena-shex"           % "4.3.2", 
   "org.slf4j"        % "slf4j-api"            % "1.7.3",
   "org.slf4j"        % "slf4j-simple"         % "1.7.3",
   "org.typelevel"    %% "cats-effect"         % "3.4.2",
   "org.http4s"       %% "http4s-circe" % "0.23.12",
   "org.apache.jena" % "jena-arq"             % "4.6.1",
  ),
  run / fork := true,
  dockerSettings,
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "buildinfo"  
 )

import com.typesafe.sbt.packager.docker.DockerChmodType

lazy val dockerSettings = Seq(
  dockerRepository := Some("wesogroup"),
  Docker / packageName := "subshex2sparql",
  dockerBaseImage := "openjdk:11",
  dockerAdditionalPermissions ++= Seq((DockerChmodType.UserGroupWriteExecute, "/tmp")),
  Docker / daemonUserUid := Some("0"),
  Docker / daemonUser := "root"
)


