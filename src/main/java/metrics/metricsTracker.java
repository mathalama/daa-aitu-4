package metrics;

/**
 * Tracks execution time and operation counts for graph algorithms.
 */
public final class metricsTracker implements metrics {

    private long startNs;
    private long elapsedNs;

    private long dfsOps;
    private long topoOps;
    private long relaxOps;

    /** Start timer. */
    @Override
    public void start() {
        startNs = System.nanoTime();
    }

    /** Stop timer. */
    @Override
    public void stop() {
        elapsedNs = System.nanoTime() - startNs;
    }

    /** @return elapsed time in nanoseconds */
    @Override
    public long getElapsedNs() {
        return elapsedNs;
    }

    /** @return elapsed time in milliseconds */
    @Override
    public double getElapsedMs() {
        return elapsedNs / 1_000_000.0;
    }

    /** Count DFS visit. */
    @Override
    public void incDfs() {
        dfsOps++;
    }

    /** Count queue operation (Kahn). */
    @Override
    public void incTopo() {
        topoOps++;
    }

    /** Count relaxation (DAG-SP). */
    @Override
    public void incRelax() {
        relaxOps++;
    }

    /** Getters. **/
    @Override
    public long getDfsOps() {
        return dfsOps;
    }

    @Override
    public long getTopoOps() {
        return topoOps;
    }

    @Override
    public long getRelaxOps() {
        return relaxOps;
    }

    /** Summary string with time and counters. */
    @Override
    public String toString() {
        return "metricsTracker{" +
                "timeMs=" + getElapsedMs() +
                ", dfsOps=" + dfsOps +
                ", topoOps=" + topoOps +
                ", relaxOps=" + relaxOps +
                '}';
    }
}
