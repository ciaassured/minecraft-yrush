{ ... }:
{
  perSystem =
    {
      lib,
      pkgs,
      yrushPaper,
      ...
    }:
    let
      smoke = pkgs.writeShellApplication {
        name = "yrush-paper-smoke";
        excludeShellChecks = [ "SC2094" ];
        runtimeInputs = [
          pkgs.coreutils
          pkgs.gnugrep
          pkgs.util-linux
        ];
        text = ''
          smoke_dir="$(mktemp -d -t yrush-paper-smoke.XXXXXX)"
          server_pid=""

          cleanup() {
            if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
              kill "$server_pid" 2>/dev/null || true
              wait "$server_pid" 2>/dev/null || true
            fi
            rm -rf "$smoke_dir"
          }
          trap cleanup EXIT INT TERM

          cd "$smoke_dir"
          (
            for _ in $(seq 1 180); do
              if grep -Fq "YRush enabled." server.log 2>/dev/null \
                && grep -Fq "Done (" server.log 2>/dev/null; then
                printf 'stop\n'
                exit 0
              fi
              sleep 1
            done
            exit 1
          ) | ${pkgs.util-linux}/bin/script -qefc \
            "YRUSH_PAPER_STATE_DIR=$smoke_dir ${lib.getExe yrushPaper.paper}" \
            --flush /dev/null \
            >server.log 2>&1 &
          server_pid="$!"

          ready=0
          for _ in $(seq 1 180); do
            if grep -Fq "YRush enabled." server.log \
              && grep -Fq "Done (" server.log; then
              ready=1
              break
            fi
            if ! kill -0 "$server_pid" 2>/dev/null; then
              if wait "$server_pid"; then
                exit_status=0
              else
                exit_status="$?"
              fi
              server_pid=""
              echo "Paper exited with status $exit_status before becoming ready with YRush enabled." >&2
              cat server.log >&2
              if [[ -f logs/latest.log ]]; then
                echo "Paper latest.log:" >&2
                cat logs/latest.log >&2
              fi
              exit 1
            fi
            sleep 1
          done

          if [[ "$ready" -ne 1 ]]; then
            echo "Timed out waiting for Paper to become ready with YRush enabled." >&2
            cat server.log >&2
            exit 1
          fi

          for _ in $(seq 1 60); do
            if ! kill -0 "$server_pid" 2>/dev/null; then
              break
            fi
            sleep 1
          done

          if kill -0 "$server_pid" 2>/dev/null; then
            echo "Paper did not stop cleanly within 60 seconds." >&2
            cat server.log >&2
            exit 1
          fi

          wait "$server_pid"
          server_pid=""

          grep -Fq "YRush enabled." server.log
          grep -Fq "Done (" server.log
          grep -Fq "Disabling YRush" server.log
          grep -Fq "Stopping server" server.log
          echo "YRush Paper 26.2 smoke test passed."
        '';
      };
    in
    {
      apps.smoke = {
        type = "app";
        program = lib.getExe smoke;
        meta.description = "Start and cleanly stop a disposable Paper 26.2 server with YRush";
      };
    };
}
