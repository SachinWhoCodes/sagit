package com.sagit;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import com.sagit.commands.*;

@Command(
    name = "sagit",
    description = "Welcome to sagit!",
    mixinStandardHelpOptions = true,
    version = "Sagit 0.1",
    subcommands = {
        InitCommand.class
    }
)

public class SagitCLI implements Runnable{
    @Override
    public void run(){
        System.out.println("Sagit CLI - Use --help to see available commands.");
    }
    public static void main(String[] args) {
        int exitCode = new CommandLine(new SagitCLI()).execute(args);
        System.exit(exitCode);
    }
}
