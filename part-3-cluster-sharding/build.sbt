import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings

name := "reactive-payment-processor"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.8"

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .settings(
    parallelExecution in Test := false,
    logLevel := Level.Debug,
    multiNodeHosts in MultiJvm := Seq("pi@S1.local", "pi@S2.local", "pi@S3.local")
  )

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.iq80.leveldb"            % "leveldb"          % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
  "com.github.scullxbones" %% "akka-persistence-mongo-casbah" % "2.0.7",
  "org.mongodb" %% "casbah" % "3.1.1"
)
