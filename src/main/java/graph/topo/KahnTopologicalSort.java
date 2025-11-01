package graph.topo;

import java.util.*;
import metrics.MetricsTracker;

/**
 * Kahn's algorithm for topological sorting of a DAG.
 */
public class KahnTopologicalSort {

    public static List<Integer> topo(List<List<Integer>> g, MetricsTracker m) {
        int n = g.size();
        int[] indeg = new int[n];
        for (int v = 0; v < n; v++) {
            for (int to : g.get(v)) {
                indeg[to]++;
            }
        }
        Queue<Integer> q = new ArrayDeque<>();
        for (int v = 0; v < n; v++) {
            if (indeg[v] == 0) {
                q.add(v);
                m.incTopo();
            }
        }
        m.start();
        List<Integer> order = new ArrayList<>();
        while (!q.isEmpty()) {
            int v = q.remove();
            m.incTopo(); // pop
            order.add(v);
            for (int to : g.get(v)) {
                if (--indeg[to] == 0) {
                    q.add(to);
                    m.incTopo(); // push
                }
            }
        }
        m.stop();
        return order;
    }
}
