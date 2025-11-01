package graph.scc;

import java.util.ArrayList;
import java.util.List;
import metrics.MetricsTracker;

/**
 * Tarjan's algorithm for strongly connected components.
 * Input: directed graph as adjacency list.
 * Output: list of SCCs (each is list of vertices).
 */
public class TarjanSCC {

    private final List<List<Integer>> g;
    private final int n;
    private final MetricsTracker metrics;

    private int time = 0;
    private final int[] disc;
    private final int[] low;
    private final boolean[] onStack;
    private final int[] stack;
    private int sp = 0;

    private final List<List<Integer>> comps = new ArrayList<>();

    public TarjanSCC(List<List<Integer>> g, MetricsTracker metrics) {
        this.g = g;
        this.n = g.size();
        this.metrics = metrics;
        this.disc = new int[n];
        this.low = new int[n];
        this.onStack = new boolean[n];
        this.stack = new int[n];
    }

    /**
     * Runs Tarjan and returns all SCCs.
     */
    public List<List<Integer>> run() {
        metrics.start();
        for (int v = 0; v < n; v++) {
            if (disc[v] == 0) {
                dfs(v);
            }
        }
        metrics.stop();
        return comps;
    }

    private void dfs(int v) {
        metrics.incDfs();
        disc[v] = low[v] = ++time;
        stack[sp++] = v;
        onStack[v] = true;

        for (int to : g.get(v)) {
            if (disc[to] == 0) {
                dfs(to);
                low[v] = Math.min(low[v], low[to]);
            } else if (onStack[to]) {
                low[v] = Math.min(low[v], disc[to]);
            }
        }

        if (low[v] == disc[v]) {
            List<Integer> comp = new ArrayList<>();
            while (true) {
                int x = stack[--sp];
                onStack[x] = false;
                comp.add(x);
                if (x == v) break;
            }
            comps.add(comp);
        }
    }
}
