package com.sagit.commands;

import picocli.CommandLine.Command;

@Command(
    name = "init",
    description = "Initialise the sagit repo!"
)

public class InitCommand implements Runnable{

    public void run(){
        System.out.println("The .sagit and .git set up done!");
    }

}
