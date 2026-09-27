package com.c2exercise.specrag.answer;

import com.c2exercise.specrag.retrieve.AssembledContext;

/**
 * TASK-011. Turns assembled context into an answer.
 *
 * <p>Deliberately an interface with one local implementation: the boundary is where a hosted LLM
 * would slot in, and keeping it explicit is the point of D-2 rather than an accident of it.
 */
public interface AnswerGenerator {

    String id();

    Answer generate(String question, AssembledContext context);
}
