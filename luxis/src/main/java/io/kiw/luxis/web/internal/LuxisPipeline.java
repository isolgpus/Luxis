package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.internal.ender.Ender;

import java.util.List;

public class LuxisPipeline<T> {
    private final List<MapInstruction> instructions;
    private final Object applicationState;
    private final boolean sendResponse;
    private final Ender ender;

    public LuxisPipeline(final List<MapInstruction> instructions, final Object applicationState) {
        this(instructions, applicationState, true, null);
    }

    public LuxisPipeline(final List<MapInstruction> instructions, final Object applicationState, final boolean sendResponse) {
        this(instructions, applicationState, sendResponse, null);
    }

    public LuxisPipeline(final List<MapInstruction> instructions, final Object applicationState, final Ender ender) {
        this(instructions, applicationState, true, ender);
    }

    public LuxisPipeline(final List<MapInstruction> instructions, final Object applicationState, final boolean sendResponse, final Ender ender) {
        this.instructions = instructions;
        this.applicationState = applicationState;
        this.sendResponse = sendResponse;
        this.ender = ender;
    }

    public List<MapInstruction> getApplicationInstructions() {
        return instructions;
    }

    public Object getApplicationState() {
        return applicationState;
    }

    public boolean shouldSendResponse() {
        return sendResponse;
    }

    public Ender getEnder() {
        return ender;
    }
}
