import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graph.dagsp.DAGShortestPath;
import graph.dagsp.DAGLongestPath;
import graph.scc.TarjanSCC;
import graph.scc.CondensationBuilder;
import graph.topo.KahnTopologicalSort;
import graph.util.SCCUtils;
import metrics.MetricsTracker;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.nio.file.*;
import java.util.*;

/**
 * Integration test that runs SCC → Condensation → Topo → DAG-SP
 * on every JSON dataset in /data and writes metrics.
 */
public class GraphAlgorithmsIntegrationTest {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path OUT_JSON = DATA_DIR.resolve("output.json");
    private static final Path OUT_CSV = DATA_DIR.resolve("metrics.csv");

    /** Edge format in JSON. */
    public static class EdgeDTO { public int u, v, w; }

    /** Dataset format for JSON files. */
    public static class DatasetDTO {
        public boolean directed;
        public int n;
        public List<EdgeDTO> edges;
        public Integer source;
        public String weight_model;
    }

    @Test
    void runAllJsonDatasets() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode results = mapper.createArrayNode();

        if (!Files.exists(DATA_DIR)) throw new IllegalStateException("data/ folder missing");
        Files.deleteIfExists(OUT_CSV);

        try (BufferedWriter csv = Files.newBufferedWriter(OUT_CSV, StandardOpenOption.CREATE)) {
            csv.write("file,vertices,edges,"
                    + "Tarjan_SCC_count,Tarjan_time_ms,Tarjan_DFS_ops,"
                    + "Kahn_time_ms,Kahn_queue_ops,"
                    + "DAGSP_short_time_ms,DAGSP_short_relax_ops,"
                    + "DAGSP_long_time_ms,DAGSP_long_relax_ops,DAGSP_long_max\n");

            try (DirectoryStream<Path> files = Files.newDirectoryStream(DATA_DIR, "*.json")) {
                for (Path json : files) {
                    String name = json.getFileName().toString();
                    if (name.equals("output.json")) continue;
                    DatasetDTO ds = mapper.readValue(json.toFile(), new TypeReference<>() {});
                    runAlgorithms(ds, name, results, csv);
                }
            }
        }

        ObjectNode root = new ObjectMapper().createObjectNode();
        root.set("results", results);
        new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(OUT_JSON.toFile(), root);
    }

    /** Executes all algorithms on a single dataset and writes metrics. */
    private static void runAlgorithms(DatasetDTO ds,
                                      String name,
                                      ArrayNode results,
                                      BufferedWriter csv) throws Exception {

        List<List<Integer>> adj = buildAdj(ds);
        List<List<int[]>> adjW = buildWeightedAdj(ds);

        // Run SCC
        MetricsTracker sccM = new MetricsTracker();
        TarjanSCC scc = new TarjanSCC(adj, sccM);
        List<List<Integer>> comps = scc.run();
        int compCount = comps.size();
        int[] compOf = SCCUtils.buildVertexToComp(comps, ds.n);

        MetricsTracker topoM = new MetricsTracker();
        MetricsTracker shortM = new MetricsTracker();
        MetricsTracker longM = new MetricsTracker();

        List<Integer> topoOrder;
        List<List<int[]>> dagWeighted;
        int srcComp;

        // Determine if graph is already a DAG
        if (compCount == ds.n) {
            topoOrder = KahnTopologicalSort.topo(adj, topoM);
            if (topoOrder.size() < ds.n) {
                // fallback: condensation due to self-loop or hidden cycle
                List<List<Integer>> cond = CondensationBuilder.buildCondensation(adj, comps);
                List<List<int[]>> condW = CondensationBuilder.buildWeightedCondensation(adj, adjW, comps);
                topoOrder = KahnTopologicalSort.topo(cond, topoM);
                dagWeighted = condW;
                int originalSrc = (ds.source != null) ? ds.source : 0;
                srcComp = compOf[originalSrc];
            } else {
                dagWeighted = adjW;
                int originalSrc = (ds.source != null) ? ds.source : 0;
                srcComp = originalSrc;
            }
        } else {
            // Condense to DAG of components
            List<List<Integer>> cond = CondensationBuilder.buildCondensation(adj, comps);
            List<List<int[]>> condW = CondensationBuilder.buildWeightedCondensation(adj, adjW, comps);
            topoOrder = KahnTopologicalSort.topo(cond, topoM);
            dagWeighted = condW;
            int originalSrc = (ds.source != null) ? ds.source : 0;
            srcComp = compOf[originalSrc];
        }

        // DAG shortest path
        DAGShortestPath.shortestFrom(srcComp, topoOrder, dagWeighted, shortM);

        // DAG longest path
        DAGLongestPath.LongestResult longRes =
                DAGLongestPath.longestFrom(srcComp, topoOrder, dagWeighted, longM);
        int[] longDist = longRes.dist();
        int maxLen = Arrays.stream(longDist)
                .filter(v -> v > Integer.MIN_VALUE)
                .max().orElse(0);

        // JSON entry
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode one = mapper.createObjectNode();
        one.put("file", name);
        one.put("vertices", ds.n);
        one.put("edges", ds.edges.size());
        one.put("Tarjan_SCC_count", compCount);
        one.put("Tarjan_time_ms", sccM.getElapsedMs());
        one.put("Tarjan_DFS_ops", sccM.getDfsOps());
        one.put("Kahn_time_ms", topoM.getElapsedMs());
        one.put("Kahn_queue_ops", topoM.getTopoOps());
        one.put("DAGSP_short_time_ms", shortM.getElapsedMs());
        one.put("DAGSP_short_relax_ops", shortM.getRelaxOps());
        one.put("DAGSP_long_time_ms", longM.getElapsedMs());
        one.put("DAGSP_long_relax_ops", longM.getRelaxOps());
        one.put("DAGSP_long_max", maxLen);
        if (ds.weight_model != null) one.put("weight_model", ds.weight_model);
        results.add(one);

        // CSV row
        csv.write(String.join(",",
                name,
                String.valueOf(ds.n),
                String.valueOf(ds.edges.size()),
                String.valueOf(compCount),
                String.valueOf(sccM.getElapsedMs()),
                String.valueOf(sccM.getDfsOps()),
                String.valueOf(topoM.getElapsedMs()),
                String.valueOf(topoM.getTopoOps()),
                String.valueOf(shortM.getElapsedMs()),
                String.valueOf(shortM.getRelaxOps()),
                String.valueOf(longM.getElapsedMs()),
                String.valueOf(longM.getRelaxOps()),
                String.valueOf(maxLen)
        ));
        csv.write("\n");
    }

    /** Builds adjacency list from dataset. */
    private static List<List<Integer>> buildAdj(DatasetDTO ds) {
        List<List<Integer>> g = new ArrayList<>();
        for (int i = 0; i < ds.n; i++) g.add(new ArrayList<>());
        for (EdgeDTO e : ds.edges) g.get(e.u).add(e.v);
        return g;
    }

    /** Builds weighted adjacency list from dataset. */
    private static List<List<int[]>> buildWeightedAdj(DatasetDTO ds) {
        List<List<int[]>> g = new ArrayList<>();
        for (int i = 0; i < ds.n; i++) g.add(new ArrayList<>());
        for (EdgeDTO e : ds.edges) {
            int w = (e.w == 0) ? 1 : e.w;
            g.get(e.u).add(new int[]{e.v, w});
        }
        return g;
    }
}
