import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graph.dagsp.DAGShortestPath;
import graph.dagsp.DAGLongestPath;
import graph.scc.TarjanSCC;
import graph.topo.KahnTopologicalSort;
import metrics.MetricsTracker;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.nio.file.*;
import java.util.*;

/**
 * Runs TarjanSCC, KahnTopologicalSort, DAGShortestPath, and DAGLongestPath
 * on all JSON datasets in /data.
 * Saves metrics to data/output.json and data/metrics.csv.
 */
public class GraphAlgorithmsIntegrationTest {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path OUT_JSON = DATA_DIR.resolve("output.json");
    private static final Path OUT_CSV = DATA_DIR.resolve("metrics.csv");

    /** Edge format from JSON file. */
    public static class EdgeDTO { public int u, v, w; }

    /** Graph dataset structure. */
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
        Files.deleteIfExists(OUT_CSV); // clean old csv

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
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(OUT_JSON.toFile(), root);
    }

    /** Runs all algorithms on one dataset and writes results. */
    private static void runAlgorithms(DatasetDTO ds, String name, ArrayNode results, BufferedWriter csv) throws Exception {
        List<List<Integer>> adj = buildAdj(ds);
        List<List<int[]>> adjW = buildWeightedAdj(ds);

        MetricsTracker sccM = new MetricsTracker();
        TarjanSCC scc = new TarjanSCC(adj, sccM);
        List<List<Integer>> comps = scc.run();
        int compCount = comps.size();

        MetricsTracker topoM = new MetricsTracker();
        MetricsTracker shortM = new MetricsTracker();
        MetricsTracker longM = new MetricsTracker();

        // if DAG → use original, else condensation
        List<Integer> topoOrder;
        List<List<int[]>> dagWeighted;
        int src;

        if (compCount == ds.n) {
            topoOrder = KahnTopologicalSort.topo(adj, topoM);
            dagWeighted = adjW;
            src = (ds.source != null) ? ds.source : 0;
        } else {
            List<List<Integer>> cond = buildCondensation(adj, comps);
            topoOrder = KahnTopologicalSort.topo(cond, topoM);
            dagWeighted = weightCond(cond);
            src = 0;
        }

        // shortest
        DAGShortestPath.shortestFrom(src, topoOrder, dagWeighted, shortM);
        // longest (critical)
        int[] longDist = DAGLongestPath.longestFrom(src, topoOrder, dagWeighted, longM);
        int maxLen = Arrays.stream(longDist).filter(v -> v > Integer.MIN_VALUE).max().orElse(0);

        // JSON result
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
        results.add(one);

        // CSV record
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

    /** Builds adjacency list. */
    private static List<List<Integer>> buildAdj(DatasetDTO ds) {
        List<List<Integer>> g = new ArrayList<>();
        for (int i = 0; i < ds.n; i++) g.add(new ArrayList<>());
        for (EdgeDTO e : ds.edges) g.get(e.u).add(e.v);
        return g;
    }

    /** Builds weighted adjacency list. */
    private static List<List<int[]>> buildWeightedAdj(DatasetDTO ds) {
        List<List<int[]>> g = new ArrayList<>();
        for (int i = 0; i < ds.n; i++) g.add(new ArrayList<>());
        for (EdgeDTO e : ds.edges) {
            int w = (e.w == 0) ? 1 : e.w;
            g.get(e.u).add(new int[]{e.v, w});
        }
        return g;
    }

    /** Assigns weight=1 for condensation DAG. */
    private static List<List<int[]>> weightCond(List<List<Integer>> cond) {
        List<List<int[]>> w = new ArrayList<>();
        for (int i = 0; i < cond.size(); i++) w.add(new ArrayList<>());
        for (int v = 0; v < cond.size(); v++)
            for (int to : cond.get(v))
                w.get(v).add(new int[]{to, 1});
        return w;
    }

    /** Builds condensation DAG (each SCC becomes a node). */
    private static List<List<Integer>> buildCondensation(List<List<Integer>> g, List<List<Integer>> comps) {
        int[] comp = new int[g.size()];
        for (int id = 0; id < comps.size(); id++)
            for (int v : comps.get(id)) comp[v] = id;

        List<List<Integer>> dag = new ArrayList<>();
        for (int i = 0; i < comps.size(); i++) dag.add(new ArrayList<>());

        Set<String> seen = new HashSet<>();
        for (int v = 0; v < g.size(); v++)
            for (int to : g.get(v)) {
                int a = comp[v], b = comp[to];
                if (a != b && seen.add(a + "-" + b)) dag.get(a).add(b);
            }
        return dag;
    }
}
