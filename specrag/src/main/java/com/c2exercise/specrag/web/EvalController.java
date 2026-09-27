package com.c2exercise.specrag.web;

import com.c2exercise.specrag.eval.EvalReport;
import com.c2exercise.specrag.eval.EvalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** TASK-014. AC-19, AC-20. */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /**
     * POST rather than GET: this runs the whole question set against the live index.
     *
     * <p>{@code mode} selects the retriever under test (TASK-009), so two runs a second apart
     * over the same warm index produce directly comparable numbers.
     */
    @PostMapping
    public EvalReport run(@RequestParam(required = false) Integer topK,
                          @RequestParam(required = false) String mode) {
        return evalService.run(topK, mode);
    }
}
