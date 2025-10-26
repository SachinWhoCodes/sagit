package com.sagit.commands.hooks;

import picocli.CommandLine;

@CommandLine.Command(
        name = "hook",
        description = "Internal hook entrypoints",
        subcommands = {
                PrepareCommitMsgHookCommand.class,
                CommitMsgHookCommand.class,
                PostCommitHookCommand.class
        }
)
public class HookCommand implements Runnable {
    @Override public void run() {
        CommandLine.usage(this, System.out);
    }
}
