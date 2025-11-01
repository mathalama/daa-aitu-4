import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graph.dagsp.DAGLongestPath;
import graph.dagsp.DAGShortestPath;
import graph.scc.CondensationBuilder;
import graph.scc.TarjanSCC;
import graph.topo.KahnTopologicalSort;
import graph.util.SCCUtils;
import metrics.MetricsTracker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Command line runner for SCC, Topo, and DAG shortest/longest path algorithms.
 *
 * Usage:
 *   java Main scc data/small1.json
 *   java Main topo data/small1.json
 *   java Main dagsp data/small1.json 0
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: scc|topo|dagsp <file> [source]");
            return;
        }

        String mode = args[0];
        Path file = Paths.get(args[1]);

        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(file.toFile());

        int n = root.get("n").asInt();
        JsonNode edges = root.get("edges");

        // Build graph
        List<List<Integer>> adj = new ArrayList<>();
        List<List<int[]>> adjW = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
            adjW.add(new ArrayList<>());
        }
        for (JsonNode e : edges) {
            int u = e.get("u").asInt();
            int v = e.get("v").asInt();
            int w = e.get("w").asInt();
            if (w == 0) w = 1;
            adj.get(u).add(v);
            adjW.get(u).add(new int[]{v, w});
        }

        // Run SCC
        MetricsTracker sccM = new MetricsTracker();
        TarjanSCC tarjan = new TarjanSCC(adj, sccM);
        List<List<Integer>> comps = tarjan.run();

        if (mode.equals("scc")) {
            System.out.println("SCC count = " + comps.size());
            for (int i = 0; i < comps.size(); i++) {
                System.out.println(i + ": " + comps.get(i));
            }
            return;
        }

        // Build condensation DAG
        List<List<Integer>> dag = CondensationBuilder.buildCondensation(adj, comps);
        List<List<int[]>> dagW = CondensationBuilder.buildWeightedCondensation(adj, adjW, comps);

        // Topological order
        MetricsTracker topoM = new MetricsTracker();
        List<Integer> topo = KahnTopologicalSort.topo(dag, topoM);

        if (mode.equals("topo")) {
            System.out.println("Topo (components): " + topo);
            System.out.println("Derived tasks: " + SCCUtils.expandOrder(topo, comps));
            return;
        }

        if (mode.equals("dagsp")) {
            int src = (args.length >= 3) ? Integer.parseInt(args[2]) : 0;
            // map source vertex to its SCC component
            int[] compOf = SCCUtils.buildVertexToComp(comps, n);
            int compSrc = compOf[src];

            // Shortest path in DAG
            MetricsTracker shortM = new MetricsTracker();
            int[] dist = DAGShortestPath.shortestFrom(compSrc, topo, dagW, shortM);
            System.out.println("Shortest distances: " + Arrays.toString(dist));

            // Longest (critical) path
            MetricsTracker longM = new MetricsTracker();
            DAGLongestPath.LongestResult lr =
                    DAGLongestPath.longestFrom(compSrc, topo, dagW, longM);
            int[] longDist = lr.dist();
            System.out.println("Longest distances: " + Arrays.toString(longDist));

            // Find the critical path
            int best = Integer.MIN_VALUE;
            int target = -1;
            for (int i = 0; i < longDist.length; i++) {
                if (longDist[i] > best) {
                    best = longDist[i];
                    target = i;
                }
            }
            if (target != -1) {
                System.out.println("Critical path (components): " +
                        DAGLongestPath.rebuildPath(target, lr));
                System.out.println("Critical length: " + best);
            }
        }
    }
}
