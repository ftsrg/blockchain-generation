package services.refinery.blockchain;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tools.refinery.generator.ModelGenerator;
import tools.refinery.generator.standalone.StandaloneRefinery;
import tools.refinery.store.reasoning.ReasoningAdapter;
import tools.refinery.store.util.CancellationToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.Map;

public class RefineryASPExperiment {
    private String problemID;
    private String aspEvaluation;
    private Integer id;
    private String problemString;
    private String model_file;
    private Long refineryRuntime;
    private Float aspRuntime;
    private Integer reliability;
    private Integer modelSize;
    private Boolean generationSuccessful;
    private ModelGenerator generator;
    private Integer timeout;
    private Integer numberofNodes;
    private Integer numberOfNetworkuse;
    private volatile boolean cancelled;
	private final CancellationToken cancellationToken = () -> {
		if (cancelled || Thread.interrupted()) {
			throw new CancellationException();
		}
	};

    public RefineryASPExperiment(String problemID, String aspEvaluation, Integer id, String problemString, Integer timeout) throws IOException {
        this.problemID = problemID;
        this.aspEvaluation = aspEvaluation;
        this.id = id;
        this.problemString = problemString;
        this.model_file = "./output/ASP/" + this.problemID + "/model" + this.id + ".lp";
        this.modelSize = 0;
        this.timeout = timeout;
        generationSuccessful = Boolean.FALSE;

        File targetFile = new File(this.model_file);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Couldn't create dir: " + parent);
        }

        var problem = StandaloneRefinery.getProblemLoader()
            .loadString(problemString);
        this.generator = StandaloneRefinery.getGeneratorFactory()
            .cancellationToken(cancellationToken)
            .createGenerator(problem);
        this.generator.setRandomSeed(id);
    }

    public String runExperiment() throws IOException {
        generateModel();
        var csvLine = this.problemID +"," + this.id +","+ ",,,,,";

        if (this.generationSuccessful) {
            saveModel();
            this.numberofNodes =  countNodes("Node");
            this.numberOfNetworkuse = countNetworkUse("networkUse");
            generateASPCode();
            runASP();
            csvLine = toCSVLine(",");
        }

        return csvLine;
    }

    private void saveModel() throws IOException {
        var outputPath = "./output/Refinery/" + this.problemID + "/model" + this.id + ".refinery";
        var outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        var solution = this.generator.serializeSolution();
        var solutionResource = solution.eResource();
        var saveOptions = Map.of();
        try (var outputStream = new FileOutputStream(outputPath)) {
            solutionResource.save(outputStream, saveOptions);
        }
    }

    private void generateModel(){
        final Duration timeout = Duration.ofSeconds(this.timeout);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {
                final long start = System.currentTimeMillis();
                generator.generate();
                final long finish = System.currentTimeMillis();
                final long timeElapsed = finish - start;
                refineryRuntime = timeElapsed;
                generationSuccessful = Boolean.TRUE;
                return "OK";
            }
        });

        try {
            handler.get(this.timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            cancelled = true;
            handler.cancel(true);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        executor.shutdownNow();
    }

    private void generateASPCode() throws IOException {
        var objectMap = new HashMap<Integer, String>();

        objectMap.putAll(getNodes("EndorsingNode"));
        objectMap.putAll(getNodes("OrderingNode"));
        objectMap.putAll(getNodes("Host"));
        objectMap.putAll(getNodes("Organization"));
        objectMap.putAll(getNodes("Channel"));
        objectMap.putAll(getNodes("ChaincodeInstance"));

        getRelation("peer",objectMap);
        getRelation("hosts",objectMap);
        getRelation("participatesIn",objectMap);
        getRelation("nodes",objectMap);
        getRelation("orderedBy",objectMap);
        getRelation("chaincodes",objectMap);
        getRelation("endorsedBy",objectMap);
    }

    private void runASP() throws IOException {
        Process p = Runtime.getRuntime().exec("clingo " + this.model_file + " ./ASP/" + aspEvaluation + " --outf=2");

        var text = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        JsonObject jsonObject = JsonParser.parseString(text).getAsJsonObject();

        this.reliability = jsonObject.get("Models").getAsJsonObject().get("Costs").getAsJsonArray().get(0).getAsInt();
        this.aspRuntime = jsonObject.get("Time").getAsJsonObject().get("Total").getAsFloat();
    }

    private void getRelation(String relationstring, HashMap<Integer, String> objectMap) throws IOException {
        var contentsSymbol = this.generator.getProblemTrace().getPartialRelation(relationstring);
        var contentsInterpretation = this.generator.getPartialInterpretation(contentsSymbol);
        var nameMapping = this.generator.getProblemTrace().getNodeTrace().flipUniqueValues();
        var cursor = contentsInterpretation.getAll();

        var outputModelFile = new File(this.model_file);
        outputModelFile.getParentFile().mkdirs();

        FileWriter myWriter = new FileWriter(this.model_file, true);
        while (cursor.move()) {

            var fromID = cursor.getKey().get(0);
            var from = objectMap.get(fromID) + "_" + fromID;
            if (nameMapping.containsKey(fromID)){
                if (!nameMapping.get(fromID).getName().equals("new")) {
                    from = nameMapping.get(fromID).getName().toLowerCase();
                }
            }
            var toID = cursor.getKey().get(1);
            var to = objectMap.get(toID) + "_" +toID;
            if (nameMapping.containsKey(toID)){
                if (!nameMapping.get(toID).getName().equals("new")){
                    to = nameMapping.get(toID).getName().toLowerCase();
                }
            }

            if ((!to.equals("new") | (!from.equals("new")))){
                var fromFact = String.format("%s(%s).%n",objectMap.get(fromID), from);
                var toFact = String.format("%s(%s).%n",objectMap.get(toID), to);
                var relFact = String.format("%s(%s,%s).%n", relationstring,from, to);
                myWriter.write(fromFact);
                myWriter.write(toFact);
                myWriter.write(relFact);
            }
        }
        myWriter.close();
    }

    private HashMap<Integer, String> getNodes(String nodetype){
        var objectMap = new HashMap<Integer, String>();

        var contentsSymbol = this.generator.getProblemTrace().getPartialRelation(nodetype);
        var contentsInterpretation = this.generator.getPartialInterpretation(contentsSymbol);
        var existing = this.generator.getPartialInterpretation(ReasoningAdapter.EXISTS_SYMBOL);
        var cursor = contentsInterpretation.getAll();


        while (cursor.move()) {
            if (existing.get(cursor.getKey()).may()){
                objectMap.put(cursor.getKey().get(0), nodetype.toLowerCase());
                this.modelSize += 1;
            }
        }
        return  objectMap;
    }

    private Integer countNodes(String nodeType){
        var contentsSymbol = this.generator.getProblemTrace().getPartialRelation(nodeType);
        var contentsInterpretation = this.generator.getPartialInterpretation(contentsSymbol);
        var existing = this.generator.getPartialInterpretation(ReasoningAdapter.EXISTS_SYMBOL);
        var cursor = contentsInterpretation.getAll();

        var numberOfNodes = 0;
        while (cursor.move()) {

            if (existing.get(cursor.getKey()).may()){
                numberOfNodes++;
            }
        }
        return  numberOfNodes;
    }

    private Integer countNetworkUse(String nodeType){
        var contentsSymbol = this.generator.getProblemTrace().getPartialRelation(nodeType);
        var contentsInterpretation = this.generator.getPartialInterpretation(contentsSymbol);
        var cursor = contentsInterpretation.getAll();

        var numberOfNodes = 0;
        while (cursor.move()) {
             numberOfNodes++;
        }
        return  numberOfNodes;
    }

    public String toCSVLine(String delimiter){
        var csvStr = this.problemID + delimiter +
                this.id + delimiter +
                this.refineryRuntime + delimiter +
                this.aspRuntime + delimiter +
                this.modelSize + delimiter +
                this.numberofNodes + delimiter +
                this.numberOfNetworkuse + delimiter +
                this.reliability;

        return csvStr;
    }

    @Override
    public String toString() {
        return "RefineryASPExperiment{" +
                "problemID='" + problemID + '\'' +
                ", id=" + id +
                ", model_file='" + model_file + '\'' +
                ", refineryRuntime=" + refineryRuntime +
                ", aspRuntime=" + aspRuntime +
                ", reliability=" + reliability +
                ", modelSize=" + modelSize +
                ", numberofNode=" + numberofNodes +
                ", numberOfNetworkuse=" + numberOfNetworkuse +
                '}';
    }

    public String getProblemID() {
        return problemID;
    }

    public Integer getId() {
        return id;
    }

    public String getProblemString() {
        return problemString;
    }

    public Long getRefineryRuntime() {
        return refineryRuntime;
    }

    public Float getAspRuntime() {
        return aspRuntime;
    }

    public Integer getReliability() {
        return reliability;
    }

}
