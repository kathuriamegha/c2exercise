package com.c2exercise.specrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for SPEC-002. Every NFR threshold that the tests assert on is configured here. */
@ConfigurationProperties(prefix = "specrag")
public class SpecRagProperties {

    private final Corpus corpus = new Corpus();
    private final Chunking chunk = new Chunking();
    private final Embedding embedding = new Embedding();
    private final Retrieval retrieval = new Retrieval();
    private final Context context = new Context();

    public Corpus getCorpus() {
        return corpus;
    }

    public Chunking getChunk() {
        return chunk;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Context getContext() {
        return context;
    }

    public static class Corpus {
        /**
         * Ingest is refused for any path resolving outside this root (AC-3). This is the only
         * thing standing between "index a directory" and "read any file on the machine".
         */
        private String allowedRoot = System.getProperty("user.home");

        public String getAllowedRoot() {
            return allowedRoot;
        }

        public void setAllowedRoot(String allowedRoot) {
            this.allowedRoot = allowedRoot;
        }
    }

    public static class Chunking {
        /**
         * Bounded above by the embedding model's sequence limit — 128 for all-MiniLM-L6-v2, less
         * the two special tokens. Startup fails if this exceeds it (Drift #2).
         */
        private int maxTokens = 120;
        private int overlapTokens = 24;
        private String defaultChunker = "recursive";

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getOverlapTokens() {
            return overlapTokens;
        }

        public void setOverlapTokens(int overlapTokens) {
            this.overlapTokens = overlapTokens;
        }

        public String getDefaultChunker() {
            return defaultChunker;
        }

        public void setDefaultChunker(String defaultChunker) {
            this.defaultChunker = defaultChunker;
        }
    }

    public static class Embedding {
        /** NFR-4: local backend by default; nothing leaves the machine. */
        private String backend = "djl";
        private String modelUrl =
                "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2";
        private int dimensions = 384;

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public String getModelUrl() {
            return modelUrl;
        }

        public void setModelUrl(String modelUrl) {
            this.modelUrl = modelUrl;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }
    }

    public static class Retrieval {
        private int defaultTopK = 5;
        /**
         * Below this cosine score nothing is considered relevant and the service says so
         * rather than answering (AC-13). Corpus-tuned; see SPEC-002 §9.
         */
        private double minScore = 0.25;

        /**
         * {@code semantic} or {@code hybrid} (TASK-009). The default stays semantic because it is
         * the measured baseline; a request may override it, and the eval harness exists to decide
         * whether this default should change.
         */
        private String mode = "semantic";

        /** Hybrid over-fetches {@code topK × this} candidates for BM25 to re-rank. */
        private int candidateMultiplier = 5;

        /** Reciprocal-rank-fusion damping constant; 60 is the value the literature settled on. */
        private int rrfK = 60;

        public int getDefaultTopK() {
            return defaultTopK;
        }

        public void setDefaultTopK(int defaultTopK) {
            this.defaultTopK = defaultTopK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getCandidateMultiplier() {
            return candidateMultiplier;
        }

        public void setCandidateMultiplier(int candidateMultiplier) {
            this.candidateMultiplier = candidateMultiplier;
        }

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int rrfK) {
            this.rrfK = rrfK;
        }
    }

    public static class Context {
        /** Assembled context never exceeds this (AC-18). */
        private int maxTokens = 1024;

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
}
