{
  pkgs,
  ...
}:

{
  # https://devenv.sh/basics/
  env.GREET = "devenv";
  env.PORT = 7888;

  # https://devenv.sh/packages/
  packages = [
    pkgs.fswatch
    pkgs.git
    pkgs.gitleaks
    pkgs.nil
    pkgs.pre-commit
    pkgs.python313Packages.pre-commit-hooks
    pkgs.rsync
  ];

  # https://devenv.sh/languages/
  # languages.rust.enable = true;
  languages.clojure.enable = true;
  languages.python = {
    enable = true;
    uv.enable = true;
    uv.sync.enable = true;
  };

  # https://devenv.sh/processes/
  # processes.dev.exec = "${lib.getExe pkgs.watchexec} -n -- ls -la";
  processes = {
    nrepl.exec = ''
      echo $PORT > .nrepl-port && ssh -L "$PORT":localhost:"$PORT" -tt cue "cd cue && devenv shell clojure -M:nrepl -p $PORT"
    '';
    watch.exec = ''
      upload && fswatch -o . | xargs -I _ upload
    '';
  };

  # https://devenv.sh/services/
  # services.postgres.enable = true;

  # https://devenv.sh/scripts/
  scripts = {
    upload.exec = ''
      rsync -avz --exclude-from .gitignore --del --exclude .git . cue:~/cue
    '';
    hello.exec = ''
      echo hello from $GREET
    '';
  };

  # https://devenv.sh/basics/
  enterShell = ''
    hello         # Run scripts directly
    git --version # Use packages
    # https://github.com/astral-sh/uv/blob/cd4973623485aeda775d2721dddd95c269fa131b/docs/guides/projects.md?plain=1#L237
    source .devenv/state/venv/bin/activate
  '';

  # https://devenv.sh/tasks/
  # tasks = {
  #   "myproj:setup".exec = "mytool build";
  #   "devenv:enterShell".after = [ "myproj:setup" ];
  # };

  # https://devenv.sh/tests/
  enterTest = ''
    echo "Running tests"
    git --version | grep --color=auto "${pkgs.git.version}"
  '';

  # https://devenv.sh/git-hooks/
  # git-hooks.hooks.shellcheck.enable = true;
  git-hooks.hooks = {
    cljfmt = {
      enable = true;
      excludes = [ "^\.clj-kondo/imports" ];
    };
    gitleaks = {
      enable = true;
      # https://github.com/gitleaks/gitleaks/blob/ca20267a84aa1fa2c2a9c1a13cdb50cafb48eeb0/.pre-commit-hooks.yaml#L4
      # Direct execution of gitleaks here results in '[git] fatal: cannot change to 'devenv.nix': Not a directory'.
      entry = "bash -c 'exec gitleaks git --redact --staged --verbose'";
    };
    # https://github.com/NixOS/nixfmt/blob/5513ad83a6e8e203d76215ed17c9e0bccbe5b55c/README.md?plain=1#L169
    nixfmt.enable = true;
    prettier.enable = true;
    trailing-whitespace = {
      enable = true;
      # https://github.com/pre-commit/pre-commit-hooks/blob/5c514f85cc9be49324a6e3664e891ac2fc8a8609/.pre-commit-hooks.yaml#L205-L212
      entry = "trailing-whitespace-fixer";
      types = [ "text" ];
    };
  };

  # See full reference at https://devenv.sh/reference/options/
}
