package io.kiw.luxis.web.db;

public final class UpdateResult<KEY> {
    private final int rowCount;
    private final KEY generatedKey;

    public UpdateResult(final int rowCount, final KEY generatedKey) {
        this.rowCount = rowCount;
        this.generatedKey = generatedKey;
    }

    public int rowCount() {
        return rowCount;
    }

    public KEY generatedKey() {
        return generatedKey;
    }
}
