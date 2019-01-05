package benchmark.cli;

import benchmark.Constants;

public class CommandLineArgsParser {


    private final int REQUIRED_MINIMUM_OF_ARGUMENTS = 2; // Database's credentials (username, password)
    private final int MAXIMAL_AVALIABLE_ARGUMENTS = 10;



    // NOTE: Database credentials
    private String userName;
    private String userPassword;

    // NOTE: Database target components
    private String databaseName;
    private String targetTableName;

    // NOTE: Benchmark components
    private String payloadSize;
    private String amountOfInsertOperations;
    private String fileNameForLogs;
    private String amountOfThreads;

    private OptionalCommandLineArguments optionalCommandLineArguments = new OptionalCommandLineArguments();




    // NOTE: Empty constructor
    public CommandLineArgsParser()  { }

    public void parseArguments(String[] args) throws IllegalArgumentException {
        if (!isAmountOfArgsSatysfying(args)) {
            // TODO: Replace to string formatter
            final String delimiter = " ";
            final String errorCause = "Invalid amount of arguments : " + args.length + ".";
            final String misleadingMsg = errorCause + delimiter + "Amount of arguments should stay in range: [" + this.REQUIRED_MINIMUM_OF_ARGUMENTS +
                    ":" + this.MAXIMAL_AVALIABLE_ARGUMENTS + "].";
            throw new IllegalArgumentException(misleadingMsg);
        }

        if (!hasRequiredArguments(args)) {
            final String misleadingMsg = "Arguments should contain both database's username and its password.";
            throw new IllegalArgumentException(misleadingMsg);
        }


        final int usernameArgumentIndex = 0;
        final int passwordArgumentIndex = 1;
        this.userName = args[usernameArgumentIndex];
        this.userPassword = args[passwordArgumentIndex];
        if (args.length > this.REQUIRED_MINIMUM_OF_ARGUMENTS) {
            optionalCommandLineArguments.parseOptionalArguments(args);
        }

    }

    private boolean isAmountOfArgsSatysfying(String[] args) {
        return ((args.length >= this.REQUIRED_MINIMUM_OF_ARGUMENTS) && (args.length <= this.MAXIMAL_AVALIABLE_ARGUMENTS));
    }

    private boolean hasRequiredArguments(String[] args) {
        for (int i = 0; i < this.REQUIRED_MINIMUM_OF_ARGUMENTS; ++i) {
            final String currentArgument = args[i];
            if (this.isArgumentOptional(currentArgument)) {
                return false;
            }
        }
        return true;
    }

    private boolean isArgumentOptional(String argument) {
        return this.optionalCommandLineArguments.isArgumentOptional(argument);
    }



    public String getHost() {
        return this.optionalCommandLineArguments.getOptionByTag("host");
    }

    public String getPort() {
        return this.optionalCommandLineArguments.getOptionByTag("port");
    }

    public String getTableName() {
        return this.optionalCommandLineArguments.getOptionByTag("table");
    }

    public String getName() {
        return this.optionalCommandLineArguments.getOptionByTag("name");

    }

    public int getAmountOfThreads() {
        final String amountOfThreads = this.optionalCommandLineArguments.getOptionByTag("threads");
        return Integer.valueOf(amountOfThreads);
    }

    public int getPayload() {
        final String payload = this.optionalCommandLineArguments.getOptionByTag("payload");
        return Integer.valueOf(payload);
    }

    public String getFileNameForLogs() {
        return this.optionalCommandLineArguments.getOptionByTag("file");
    }

    public int getAmountOfInsertions() {
        final String insertionAmount = this.optionalCommandLineArguments.getOptionByTag("insertions");
        return Integer.valueOf(insertionAmount);
    }
//
//    public String getPort() {
//        return this.optionalCommandLineArguments.getPort();
//    }

}
