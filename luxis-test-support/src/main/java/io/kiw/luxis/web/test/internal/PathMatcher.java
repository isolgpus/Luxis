package io.kiw.luxis.web.test.internal;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.internal.LuxisPipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

public class PathMatcher {

    private Map<String, PathMatcher> children = new LinkedHashMap<>();
    private String paramName = null;
    private PathMatcher paramChild = null;
    private Map<Method, List<LuxisPipeline<?>>> flows = new EnumMap<>(Method.class);
    private Map<Method, List<LuxisPipeline<?>>> wildCardFlows = new EnumMap<>(Method.class);
    private List<LuxisPipeline<?>> allMethodWildCardFlows = new ArrayList<>();

    public void putRoute(final String path, final Method method, final LuxisPipeline<?> flow) {
        final Queue<String> pathSegments = splitPath(path);
        this.putRoute(pathSegments, method, flow);
    }

    private Queue<String> splitPath(final String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private void putRoute(final Queue<String> pathSegments, final Method method, final LuxisPipeline<?> flow) {
        final String pathSegment = pathSegments.poll();


        final boolean isAWildCard = pathSegment.equals("*");
        if (isAWildCard) {
            this.addWildcardHandler(flow, method);
            return;
        }

        final boolean isAParam = pathSegment.startsWith(":");
        final PathMatcher childPathMatcher;
        if (isAParam) {
            this.paramName = pathSegment.substring(1);
            this.paramChild = this.paramChild != null ? this.paramChild : new PathMatcher();
            childPathMatcher = this.paramChild;
        } else {
            childPathMatcher = children.computeIfAbsent(pathSegment, s -> new PathMatcher());
        }

        if (pathSegments.size() == 0) {
            childPathMatcher.addHandler(flow, method);
        } else {
            childPathMatcher.putRoute(pathSegments, method, flow);
        }
    }

    public void putAllMethodRoute(final String path, final LuxisPipeline<?> flow) {
        final Queue<String> pathSegments = splitPath(path);
        this.putAllMethodRoute(pathSegments, flow);
    }

    private void putAllMethodRoute(final Queue<String> pathSegments, final LuxisPipeline<?> flow) {
        final String pathSegment = pathSegments.poll();

        final boolean isAWildCard = pathSegment.equals("*");
        if (isAWildCard) {
            this.allMethodWildCardFlows.add(flow);
            return;
        }

        final PathMatcher childPathMatcher = children.computeIfAbsent(pathSegment, s -> new PathMatcher());

        if (pathSegments.size() == 0) {
            childPathMatcher.allMethodWildCardFlows.add(flow);
        } else {
            childPathMatcher.putAllMethodRoute(pathSegments, flow);
        }
    }

    private void addWildcardHandler(final LuxisPipeline<?> flow, final Method method) {
        this.wildCardFlows.computeIfAbsent(method, key -> new ArrayList<>()).add(flow);

    }

    private void addHandler(final LuxisPipeline<?> flow, final Method method) {
        this.flows.computeIfAbsent(method, key -> new ArrayList<>()).add(flow);
    }

    public MatchResult get(final String path, final Method method) {
        final Queue<String> pathSegments = splitPath(path);
        final Map<String, String> pathParams = new LinkedHashMap<>();

        final List<LuxisPipeline<?>> flows = get(pathSegments, method, new ArrayList<>(), pathParams);
        return new MatchResult(flows, pathParams);
    }

    private List<LuxisPipeline<?>> get(final Queue<String> pathSegments, final Method method, final List<LuxisPipeline<?>> collectedFlows, final Map<String, String> pathParams) {
        collectedFlows.addAll(this.allMethodWildCardFlows);
        if (this.wildCardFlows.containsKey(method)) {
            collectedFlows.addAll(this.wildCardFlows.get(method));
        }
        final String pathSegment = pathSegments.poll();

        if (pathSegment == null) {
            if (this.flows.containsKey(method)) {
                collectedFlows.addAll(this.flows.get(method));
            }
        } else {
            final PathMatcher child = this.children.get(pathSegment);
            if (child != null) {
                child.get(pathSegments, method, collectedFlows, pathParams);
            } else if (this.paramChild != null) {
                pathParams.put(this.paramName, pathSegment);
                this.paramChild.get(pathSegments, method, collectedFlows, pathParams);
            }
        }

        return collectedFlows;
    }

    public static class MatchResult {
        private final List<LuxisPipeline<?>> flows;
        private final Map<String, String> pathParams;

        public MatchResult(final List<LuxisPipeline<?>> flows, final Map<String, String> pathParams) {
            this.flows = flows;
            this.pathParams = pathParams;
        }

        public List<LuxisPipeline<?>> getFlows() {
            return flows;
        }

        public Map<String, String> getPathParams() {
            return pathParams;
        }
    }
}
