{ ... }:
{
  perSystem =
    {
      config,
      lib,
      pkgs,
      ...
    }:
    let
      source = lib.fileset.toSource {
        root = ../.;
        fileset = lib.fileset.unions [
          ../build.gradle.kts
          ../settings.gradle.kts
          ../src
        ];
      };

      yrush = pkgs.stdenvNoCC.mkDerivation (finalAttrs: {
        pname = "yrush";
        version = "0.0.0-dev";
        src = source;

        nativeBuildInputs = [
          pkgs.gradle_9
          pkgs.jdk25
        ];

        mitmCache = pkgs.gradle_9.fetchDeps {
          pkg = finalAttrs.finalPackage;
          data = ./gradle-deps.json;
        };

        gradleFlags = [
          "--no-daemon"
          "--stacktrace"
          "-Dorg.gradle.java.home=${pkgs.jdk25}"
          "-PyrushVersion=${finalAttrs.version}"
        ];
        gradleBuildTask = "assemble";
        gradleCheckTask = "check";
        doCheck = true;

        installPhase = ''
          runHook preInstall
          mkdir -p "$out/share/yrush"
          cp build/libs/*.jar "$out/share/yrush/YRush.jar"
          runHook postInstall
        '';

        meta = {
          description = "Race to a random Y coordinate on Paper";
          homepage = "https://github.com/cia-assured/minecraft-yrush";
          platforms = [ "x86_64-linux" ];
        };
      });

    in
    {
      packages = {
        inherit yrush;
        default = yrush;
      };

      checks.yrush = config.packages.yrush;

      apps.update-gradle-deps = {
        type = "app";
        program = toString yrush.mitmCache.updateScript;
        meta.description = "Refresh the captured Gradle dependency metadata";
      };
    };
}
