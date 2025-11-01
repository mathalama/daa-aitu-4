package metrics;

public interface metrics {
    void start();
    void stop();

    long getElapsedNs();
    double getElapsedMs();

    void incDfs();
    void incTopo();
    void incRelax();

    long getDfsOps();
    long getTopoOps();
    long getRelaxOps();
}
