scalaVersion := "2.11.9"

libraryDependencies ++= Seq(
  "org.apache.spark" % "spark-core_2.11" % "2.1.0" % "provided",
  "org.apache.spark" % "spark-sql_2.11" % "2.1.0" % "provided",
  "org.apache.spark" % "spark-mllib_2.11" % "2.1.0" % "provided",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.7.0",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.7.0" classifier "models",
  "org.typelevel" %% "cats" % "0.9.0",
  "com.github.pureconfig" %% "pureconfig" % "0.7.0"
)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

logLevel in assembly := Level.Debug
assemblyJarName in assembly := "lsa-spark.jar"
mainClass in assembly := Some("lda.Main")
test in assembly := {}
assemblyShadeRules in assembly := Seq(
  ShadeRule
    .rename("shapeless.**" -> "shadedshapeless.@1")
    .inAll)

//def latestScalafmt = "0.7.0-RC1"
//commands += Command.args("scalafmt", "Run scalafmt cli.") {
//  case (state, args) =>
//    val Right(scalafmt) =
//      org.scalafmt.bootstrap.ScalafmtBootstrap.fromVersion(latestScalafmt)
//    scalafmt.main("--non-interactive" +: args.toArray)
//    state
//}

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8", // yes, this is 2 args
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code", // N.B. doesn't work well with the ??? hole
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
//  "-Xlog-implicits"
  "-Ypartial-unification"
)
