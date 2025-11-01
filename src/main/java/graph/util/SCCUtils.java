package graph.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper utilities for SCC and condensation operations.
 */
public final class SCCUtils {

    private SCCUtils() {}

    /**
     * Expands a topological order of SCCs into a derived order
     * of the original vertices.
     *
     * @param topoScc topological order of SCC indices
     * @param scc list of SCCs, each = list of original vertices
     * @return list of original vertices in derived order
     */
    public static List<Integer> expandOrder(List<Integer> topoScc,
                                            List<List<Integer>> scc) {
        List<Integer> order = new ArrayList<>();
        for (int cid : topoScc) {
            order.addAll(scc.get(cid));
        }
        return order;
    }

    /**
     * Builds an array mapping vertex → component ID.
     *
     * @param scc list of SCCs
     * @param n total number of vertices
     * @return int[n] array where result[v] = component id
     */
    public static int[] buildVertexToComp(List<List<Integer>> scc, int n) {
        int[] compOf = new int[n];
        for (int cid = 0; cid < scc.size(); cid++) {
            for (int v : scc.get(cid)) {
                compOf[v] = cid;
            }
        }
        return compOf;
    }
}
