package com.sagit.commands;

import com.sagit.utils.FS;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(name = "verify", description = "Check installation, hooks, and config")
public class VerifyCommand implements Runnable {

    @Override
    public void run() {
        try {
            Path root  = FS.repoRoot();
            Path jar   = root.resolve(".sagit/sagit.jar");
            Path hooks = root.resolve(".git/hooks");

            // Check both Unix and Windows variants
            Path pcmSh  = hooks.resolve("prepare-commit-msg");
            Path pcmBat = hooks.resolve("prepare-commit-msg.bat");
            Path cmSh   = hooks.resolve("commit-msg");
            Path cmBat  = hooks.resolve("commit-msg.bat");
            Path pcSh   = hooks.resolve("post-commit");
            Path pcBat  = hooks.resolve("post-commit.bat");

            boolean okJar      = Files.exists(jar);
            boolean okHooksDir = Files.isDirectory(hooks);

            boolean okPCM = (Files.exists(pcmSh)  && Files.isReadable(pcmSh))  || Files.exists(pcmBat);
            boolean okCM  = (Files.exists(cmSh)   && Files.isReadable(cmSh))   || Files.exists(cmBat);
            boolean okPC  = (Files.exists(pcSh)   && Files.isReadable(pcSh))   || Files.exists(pcBat);

            Path config = root.resolve(".sagit/config.json");
            Path rules  = root.resolve(".sagit/tests.map");

            System.out.println("Sagit verify:");
            System.out.println("  repo root: " + root);
            System.out.println("  jar: " + jar + "  [" + (okJar ? "OK" : "MISSING") + "]");
            System.out.println("  hooks dir: " + hooks + "  [" + (okHooksDir ? "OK" : "MISSING") + "]");
            System.out.println("  hook prepare-commit-msg: " + (okPCM ? "OK" : "MISSING"));
            System.out.println("  hook commit-msg: " + (okCM ? "OK" : "MISSING"));
            System.out.println("  hook post-commit: " + (okPC ? "OK" : "MISSING"));
            System.out.println("  .sagit/config.json: " + (Files.exists(config) ? "present" : "optional (not found)"));
            System.out.println("  .sagit/tests.map: " + (Files.exists(rules) ? "present" : "optional (not found)"));

            if (!okJar)      System.out.println("  > Run: sagit setup (to copy .sagit/sagit.jar)");
            if (!okHooksDir) System.out.println("  > Run: sagit setup (to create .git/hooks)");
            if (!okPCM || !okCM || !okPC)
                System.out.println("  > Re-run: sagit setup (to reinstall hooks)");

        } catch (Exception e) {
            System.err.println("verify failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
