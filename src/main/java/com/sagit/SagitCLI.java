package com.sagit;

import com.sagit.commands.DiffSemanticCommand;
import com.sagit.commands.MetaShowCommand;
import com.sagit.commands.SetupCommand;
import com.sagit.commands.hooks.HookCommand;

import picocli.CommandLine;

@CommandLine.Command(
        name = "sagit",
        mixinStandardHelpOptions = true,
        version = "Sagit 0.1.0",
        description = "Git enhancer with semantic analysis",
        subcommands = {
                SetupCommand.class,
                DiffSemanticCommand.class,
                MetaShowCommand.class,
                HookCommand.class
        }
)
public class SagitCLI implements Runnable {
    public static void main(String[] args) {
        int code = new CommandLine(new SagitCLI()).execute(args);
        System.exit(code);
    }

    @Override public void run() {
        CommandLine.usage(this, System.out);
    }
}
