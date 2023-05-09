val scala3Version = "3.2.2"
val http4sVersion = "0.23.18"
val circeVersion = "0.14.1"
val munitVersion = "0.7.29"
val munitCatsEffectVersion = "1.0.7"

lazy val root = project
  .in(file("."))
  .settings(
    name := "fs2-pubsub-snippet",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion % Test
    )
  )
