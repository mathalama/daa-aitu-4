package graph.scc;

import java.util.*;

/**
 * Utility class that builds a condensation graph (SCC → DAG).
 * Each strongly connected component becomes a single vertex.
 * The result is always a Directed Acyclic Graph (DAG).
 */
public final class CondensationBuilder {

    private CondensationBuilder() {}

    /**
     * Builds an unweighted condensation graph.
     *
     * @param adj  original directed graph: adj[v] = list of outgoing neighbors
     * @param comps list of SCCs, where each SCC is a list of original vertices
     * @return adjacency list of the condensation DAG
     */
    public static List<List<Integer>> buildCondensation(List<List<Integer>> adj,
                                                        List<List<Integer>> comps) {
        int n = adj.size();
        int compCount = comps.size();

        // vertex -> component id
        int[] compOf = new int[n];
        for (int cid = 0; cid < compCount; cid++) {
            for (int v : comps.get(cid)) {
                compOf[v] = cid;
            }
        }

        List<List<Integer>> dag = new ArrayList<>();
        for (int i = 0; i < compCount; i++) {
            dag.add(new ArrayList<>());
        }

        // prevent parallel edges between same components
        Set<Long> seen = new HashSet<>();

        for (int v = 0; v < n; v++) {
            int a = compOf[v];
            for (int to : adj.get(v)) {
                int b = compOf[to];
                if (a == b) continue; // skip internal SCC edges
                long key = (((long) a) << 32) | (b & 0xffffffffL);
                if (seen.add(key)) {
                    dag.get(a).add(b);
                }
            }
        }
        return dag;
    }

    /**
     * Builds a weighted condensation graph.
     * For multiple edges between the same SCCs, keeps the minimum weight.
     *
     * @param adj   original unweighted adjacency (for sizing)
     * @param adjW  weighted adjacency list: adjW[v] = list of {to, weight}
     * @param comps list of SCCs
     * @return weighted DAG adjacency list
     */
    public static List<List<int[]>> buildWeightedCondensation(List<List<Integer>> adj,
                                                              List<List<int[]>> adjW,
                                                              List<List<Integer>> comps) {
        int n = adj.size();
        int compCount = comps.size();

        int[] compOf = new int[n];
        for (int cid = 0; cid < compCount; cid++) {
            for (int v : comps.get(cid)) {
                compOf[v] = cid;
            }
        }

        List<List<int[]>> dagW = new ArrayList<>();
        for (int i = 0; i < compCount; i++) {
            dagW.add(new ArrayList<>());
        }

        // (a,b) → minWeight
        Map<Long, Integer> best = new HashMap<>();

        for (int v = 0; v < n; v++) {
            int a = compOf[v];
            for (int[] e : adjW.get(v)) {
                int to = e[0];
                int w = e[1];
                int b = compOf[to];
                if (a == b) continue;
                long key = (((long) a) << 32) | (b & 0xffffffffL);
                best.merge(key, w, Math::min);
            }
        }

        // convert to adjacency list
        for (Map.Entry<Long, Integer> e : best.entrySet()) {
            long key = e.getKey();
            int a = (int) (key >> 32);
            int b = (int) (key & 0xffffffffL);
            dagW.get(a).add(new int[]{b, e.getValue()});
        }
        return dagW;
    }
}
