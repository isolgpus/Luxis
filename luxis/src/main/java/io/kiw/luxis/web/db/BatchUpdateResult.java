package io.kiw.luxis.web.db;

import java.util.List;

public final class BatchUpdateResult<KEY> {
    private final int[] rowCounts;
    private final List<KEY> generatedKeys;

    public BatchUpdateResult(final int[] rowCounts, final List<KEY> generatedKeys) {
        this.rowCounts = rowCounts;
        this.generatedKeys = generatedKeys;
    }

    public int[] rowCounts() {
        return rowCounts;
    }

    public List<KEY> generatedKeys() {
        return generatedKeys;
    }
}
