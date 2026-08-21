{ ... }:
{
  perSystem =
    { lib, pkgs, ... }:
    let
      nixSource = lib.fileset.toSource {
        root = ../.;
        fileset = lib.fileset.unions [
          ../flake.nix
          (lib.fileset.fileFilter (file: file.hasExt "nix") ../nix)
        ];
      };
    in
    {
      devShells.default = pkgs.mkShellNoCC {
        packages = [
          pkgs.gh
          pkgs.git
          pkgs.gradle_9
          pkgs.jdk25
          pkgs.nixfmt-tree
        ];

        JAVA_HOME = pkgs.jdk25;
      };

      formatter = pkgs.nixfmt-tree;

      checks.nixfmt = pkgs.runCommand "nixfmt-check" { nativeBuildInputs = [ pkgs.nixfmt-tree ]; } ''
        cp -r ${nixSource} source
        chmod -R u+w source
        treefmt --ci --tree-root source source
        touch "$out"
      '';
    };
}
