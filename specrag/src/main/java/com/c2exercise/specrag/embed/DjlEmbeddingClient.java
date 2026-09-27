package com.c2exercise.specrag.embed;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process embedding via DJL + ONNX Runtime.
 *
 * <p>Chosen so that SPEC-002 NFR-3 (no outbound network call during a query) and NFR-4 (corpus
 * never leaves the machine) hold without credentials. Model weights download once on first use
 * and are cached under {@code ~/.djl.ai/} (NFR-7).
 *
 * <p>The model is loaded exactly once per JVM (NFR-2); {@link #loadCount()} exposes that for the
 * test. {@link Predictor} is not thread-safe, so each calling thread gets its own.
 */
public class DjlEmbeddingClient implements EmbeddingClient, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DjlEmbeddingClient.class);

    private static final AtomicInteger LOAD_COUNT = new AtomicInteger();

    private final String modelUrl;
    private final int dimensions;
    private final ZooModel<String, float[]> model;
    private final ThreadLocal<Predictor<String, float[]>> predictor;

    public DjlEmbeddingClient(String modelUrl, int dimensions) {
        this.modelUrl = modelUrl;
        this.dimensions = dimensions;
        this.model = loadModel(modelUrl);
        this.predictor = ThreadLocal.withInitial(model::newPredictor);
    }

    private static ZooModel<String, float[]> loadModel(String modelUrl) {
        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(modelUrl)
                .optEngine("OnnxRuntime")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .build();
        try {
            long t0 = System.currentTimeMillis();
            ZooModel<String, float[]> m = criteria.loadModel();
            LOAD_COUNT.incrementAndGet();
            log.info("Loaded embedding model {} in {} ms", modelUrl, System.currentTimeMillis() - t0);
            return m;
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            throw new IllegalStateException("Failed to load embedding model: " + modelUrl, e);
        }
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        try {
            float[] raw = predictor.get().predict(text);
            if (raw.length != dimensions) {
                throw new IllegalStateException(
                        "model returned " + raw.length + " dims, expected " + dimensions);
            }
            // AC-9: callers rely on unit norm so cosine reduces to a dot product downstream.
            return Vectors.l2Normalize(raw);
        } catch (TranslateException e) {
            throw new IllegalStateException("Embedding failed for text of length " + text.length(), e);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String backendId() {
        return "djl:" + modelUrl.substring(modelUrl.lastIndexOf('/') + 1);
    }

    /**
     * Directory the model was unpacked to. The chunkers load {@code tokenizer.json} from here so
     * that chunk budgets are counted in the same tokens the encoder will actually see — a
     * separately-sourced tokenizer could drift from the model and silently break AC-6.
     */
    public Path modelPath() {
        return model.getModelPath();
    }

    /** NFR-2 verification hook: how many times a model has been loaded in this JVM. */
    public static int loadCount() {
        return LOAD_COUNT.get();
    }

    @Override
    @PreDestroy
    public void close() {
        model.close();
    }
}
