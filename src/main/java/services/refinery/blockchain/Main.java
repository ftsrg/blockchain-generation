package services.refinery.blockchain;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.*;
import java.nio.file.Paths;
import java.nio.file.Files;

public class Main {
    @Parameter(names = {"-problem", "-p"}, description = "Problem ID", required = true)
    private String problemID;

    @Parameter(names = {"-asp", "-a"}, description = "ASP analysis name", required = true)
    private String aspEvaluation;

    @Parameter(names = {"-timeout", "-t"}, description = "Timeout in seconds (default: 300)")
    private int timeout = 300;

    @Parameter(names = {"-warmup", "-w"}, description = "Number of warmup iterations (default: 0, paper: 5)")
    private int warmup;

    @Parameter(names = {"-runs", "-r"}, description = "Number of models to generate (default: 1, paper: 30)")
    private int runs = 1;

    public static void main(String[] args) throws IOException {
        var main = new Main();
        JCommander.newBuilder()
            .addObject(main)
            .build()
            .parse(args);
        main.run();
    }

    private void run() throws IOException {
        var problemFile = "./problemDescriptions/" + problemID + ".problem";
        String problemDescription = Files.readString(Paths.get(problemFile));

        // Warmup
        System.out.format("Warmup starting for %s%n", problemID);
        for (int i = 0; i < warmup; i++) {
            RefineryASPExperiment experiment = new RefineryASPExperiment("warmup",aspEvaluation,i,problemDescription,60);
            experiment.runExperiment();
        }
        System.out.println("End of warmup");


        var outputPath = "./output/CSV/" + problemID + ".csv";

        var outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        // Preparing the CSV File
        FileWriter resultCSV = new FileWriter(outputFile);
        var csvHeader = "problemid,modelid,refineryruntime,aspruntime,modelsize,numberofnodes,numberofnetworkuse,reliability";
        resultCSV.write(csvHeader+"\n");

        // Running the evaluation
        System.out.format("Evaluation starting for %s%n", problemID);
        for (int i = 0; i < runs; i++) {
            RefineryASPExperiment experiment = new RefineryASPExperiment(problemID,aspEvaluation,i,problemDescription,timeout);
            var resultLine = experiment.runExperiment();

            System.out.format("%s%n", resultLine);
            resultCSV.write(resultLine+"\n");
        }
        resultCSV.close();

        System.out.println("End of evaluation");
    }
}
