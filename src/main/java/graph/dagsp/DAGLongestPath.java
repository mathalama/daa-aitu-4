package graph.dagsp;

import metrics.MetricsTracker;

import java.util.*;

/**
 * Longest (critical) path algorithm for a DAG.
 * Uses dynamic programming over a topological order.
 */
public final class DAGLongestPath {

    private DAGLongestPath() {}

    /** Result structure containing distances and parent links. */
    public record LongestResult(int[] dist, int[] parent) {}

    /**
     * Computes the longest distances from a single source in a DAG.
     *
     * @param src  starting vertex (or component ID)
     * @param topo topological order of DAG
     * @param g    weighted DAG: list of int[]{to, weight}
     * @param m    metrics tracker
     * @return LongestResult(dist, parent)
     */
    public static LongestResult longestFrom(int src,
                                            List<Integer> topo,
                                            List<List<int[]>> g,
                                            MetricsTracker m) {
        int n = g.size();
        int[] dist = new int[n];
        int[] parent = new int[n];
        Arrays.fill(dist, Integer.MIN_VALUE);
        Arrays.fill(parent, -1);
        dist[src] = 0;

        m.start();
        for (int v : topo) {
            if (dist[v] == Integer.MIN_VALUE) continue; // unreachable
            for (int[] e : g.get(v)) {
                int to = e[0];
                int w = e[1];
                int nd = dist[v] + w;
                if (nd > dist[to]) {
                    dist[to] = nd;
                    parent[to] = v;
                    m.incRelax();
                }
            }
        }
        m.stop();
        return new LongestResult(dist, parent);
    }

    /**
     * Reconstructs a path from the parent array.
     *
     * @param target destination vertex
     * @param res result containing parent info
     * @return list of vertices along the path
     */
    public static List<Integer> rebuildPath(int target, LongestResult res) {
        List<Integer> path = new ArrayList<>();
        for (int v = target; v != -1; v = res.parent()[v]) {
            path.add(v);
        }
        Collections.reverse(path);
        return path;
    }
}
