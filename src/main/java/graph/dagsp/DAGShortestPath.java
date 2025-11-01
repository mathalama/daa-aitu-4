package graph.dagsp;

import java.util.Arrays;
import java.util.List;
import metrics.MetricsTracker;

/**
 * Single-source shortest paths on DAG.
 * Expects: already have topological order.
 */
public class DAGShortestPath {

    public static final int INF = 1_000_000_000;

    public static int[] shortestFrom(int src,
                                     List<Integer> topo,
                                     List<List<int[]>> g,
                                     MetricsTracker m) {
        int n = g.size();
        int[] dist = new int[n];
        Arrays.fill(dist, INF);
        dist[src] = 0;

        m.start();
        for (int v : topo) {
            if (dist[v] == INF) continue;
            for (int[] e : g.get(v)) {
                int to = e[0];
                int w = e[1];
                int nd = dist[v] + w;
                if (nd < dist[to]) {
                    dist[to] = nd;
                    m.incRelax();
                }
            }
        }
        m.stop();
        return dist;
    }
}
