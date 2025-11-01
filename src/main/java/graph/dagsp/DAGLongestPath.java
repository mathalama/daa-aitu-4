package graph.dagsp;

import java.util.*;
import metrics.MetricsTracker;

/**
 * Longest (critical) path in a DAG using topological order.
 */
public final class DAGLongestPath {

    public static int[] longestFrom(int src,
                                    List<Integer> topo,
                                    List<List<int[]>> g,
                                    MetricsTracker m) {
        int n = g.size();
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MIN_VALUE);
        dist[src] = 0;

        m.start();
        for (int v : topo) {
            if (dist[v] == Integer.MIN_VALUE) continue;
            for (int[] e : g.get(v)) {
                int to = e[0];
                int w = e[1];
                int nd = dist[v] + w;
                if (nd > dist[to]) {
                    dist[to] = nd;
                    m.incRelax();
                }
            }
        }
        m.stop();
        return dist;
    }
}
